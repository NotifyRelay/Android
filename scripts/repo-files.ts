/** 供各门禁共享的仓库文件发现与按行引用扫描。 */

import { readdirSync, readFileSync, realpathSync, statSync, type Dirent } from 'node:fs'
import { join, relative, resolve, sep } from 'node:path'

/** 一条手写路径及其规范化目标，用于符号链接去重。 */
export interface RepoFile {
  /** 被调用方 glob 匹配到的绝对路径。 */
  abs: string
  /** 仅用于去重的绝对规范化路径。 */
  real: string
}

/** 规范化之前的一条遍历器匹配。 */
interface GlobMatch {
  /** 相对仓库的斜杠路径。 */
  path: string
  /** 仅当该匹配是只经由真实目录到达的普通文件时为 false。 */
  viaSymlink: boolean
}

/** 一条被拒绝的按行仓库引用。 */
export interface ReferenceViolation {
  /** 包含该引用的相对仓库文件。 */
  file: string
  /** 包含该引用的 1 基行号。 */
  line: number
  /** 规范化后的引用文本。 */
  ref: string
}

/** 判断仓库路径属于冻结的 Agent Note 历史，而非持续演进的源散文。 */
export function isArchivedAgentNotePath(path: string): boolean {
  return path.replaceAll('\\', '/').startsWith('.agents/notes/archived/')
}

/**
 * 按行切分文本，兼容 LF 与 CRLF。
 *
 * 同一份文件在不同平台检出时的行尾不同，门禁读到的源可能是任一种。按 `'\n'` 直接
 * 切分会给每行末尾留下 `'\r'`，使逐行精确比较与行首正则全部失配——空行判定、
 * `Status:` 行匹配这类规则会整体失效。
 * @param source - 待切分的文本。
 * @returns 不含行尾符的逐行数组。
 */
export function splitLines(source: string): string[] {
  return source.split(/\r?\n/)
}

/**
 * 判断模式段是否匹配一个目录或文件名。支持段内的 `*`、`?`，并遵循 node
 * glob 的 `dot: false`：首字符为通配符的段不匹配点开头的名字。`**` 由
 * 遍历器整体处理，不进入此函数。
 *
 * 匹配忽略大小写：同一份模式要在 Windows 与 Linux 上扫到同一批文件，而目录
 * 与文件的实际大小写随检出平台与历史重命名而变（例如 `docs/` 在磁盘上是
 * `Docs/`）。区分大小写会让同一份配置在两种平台上覆盖不同的语料。
 *
 * 这份宽容只管「扫哪些文件」，不管链接能否解析：链接目标由 `verify-md-links`
 * 逐段核对实际名字的大小写，写错的链接不会因为本函数而通过。
 */
function segmentMatches(pattern: string, name: string): boolean {
  if (name.startsWith('.') && (pattern.startsWith('*') || pattern.startsWith('?'))) return false
  let expression = ''
  for (const character of pattern) {
    if (character === '*') expression += '.*'
    else if (character === '?') expression += '.'
    else expression += character.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  }
  return new RegExp(`^${expression}$`, 'i').test(name)
}

/**
 * 拒绝遍历器未建模的 glob 语法。node 的 `fs.glob` 支持字符类、花括号选择与
 * extglob；把这些语法当作字面量静默展开会匹配不到任何内容，从而悄悄缩小
 * 门禁的语料，因此使用它们的模式段必须立刻失败。纯字面段也因此不得含有
 * 任何被拒绝的元字符，即使 node glob 会把它们读作字面量。
 */
const UNSUPPORTED_GLOB = /[[\]{}()!+@\\]/

function assertSupportedSegment(segment: string): void {
  if (UNSUPPORTED_GLOB.test(segment)) {
    throw new TypeError(`repo-files walker does not model glob syntax in segment: ${segment}`)
  }
}

/**
 * 在 `root` 下按一条相对仓库的 glob 遍历，不使用 node 的 `fs.glob`。目录性
 * 只由 dirent 类型与 stat 结果判定，从不在文件之下探测路径，因此同一棵树
 * 在每个 node 版本上的枚举结果一致；node 内部 glob 在某些 24.x 版本中展开
 * `**` 时会对符号链接文件 lstat 探测 `<matched>/<next segment>`，抛出
 * ENOTDIR 而不是跳过（在 node 24.13.0 扫描 `snapshots/acp/image-compaction`
 * 的符号链接 `system-prompt.expected.md` 时观察到）。
 *
 * 段语义：字面段经由 `stat` 解析，因此指向符号链接目录的字面段会进入该
 * 目录，与 node glob 一致——包括跨段重复出现的字面量，node glob 每次都
 * 重新解析；通配段与 `**` 依据 dirent 类型解析，因此从不进入符号链接
 * 目录，也从不匹配点开头的名字。node 只为 `**` 展开记录了
 * `follow: false`，所以通配侧行为是本遍历器自己的契约，由
 * repo-files.spec.ts 固定，而非 node 的跨版本保证；四个消费门禁的当前
 * 模式中不存在覆盖符号链接目录的通配段，故语料不变。`**` 跨越零个或
 * 多个目录。末段匹配文件与符号链接；损坏或成环的符号链接随后在调用方
 * 的 realpathSync 中立刻失败，而不是缩小被扫描的语料；而字面非末段下
 * 的损坏符号链接匹配不到任何内容，同样与 node glob 一致。遍历无需
 * visited 集合即可终止：每个字面段每次递归消耗一个模式段，`**` 只递归
 * 进入真实目录，而符号链接目录永不被其展开，故真实目录构成有限树。
 * 模式段支持 `*`、`?`，以及不含 `[]{}()!+@\` 的字面量；其他 node-glob
 * 语法，包括尾随 `**`、`..` 与空段，都在入口处立刻拒绝，而 `.` 段按
 * node glob 的规范化方式折掉。目录列表来自 `listDirectory`，调用方跨
 * 模式共享它，使每个目录只被读取一次。`skipDirectory` 返回 true 的目录
 * 永不进入。返回相对仓库的斜杠路径，已排序；除非该匹配是只经由真实
 * 目录到达的普通文件，每条都标记 `viaSymlink`。
 */
function expandGlob(
  root: string,
  pattern: string,
  listDirectory: (dirAbs: string) => readonly Dirent[],
  skipDirectory: (relativeDirectory: string) => boolean,
): GlobMatch[] {
  const segments: string[] = []
  for (const segment of pattern.split('/')) {
    if (segment === '.') {
      // node glob 会把 `.` 段规范化掉；此处同样丢弃，使 `./README.md` 与
      // `a/./b` 的匹配结果与 node glob 一致，而不是静默匹配不到内容。
      continue
    }
    if (segment === '..') {
      // `..` 段会逃出被扫描的根，并以 node glob 特殊处理的方式与 `**`
      // 交互；没有门禁模式使用它，因此遍历器立刻拒绝该形式，而不是静默
      // 匹配不到内容。
      throw new TypeError(`repo-files walker does not model .. segments in pattern: ${pattern}`)
    }
    if (segment === '') {
      // 前导、重复或尾随的斜杠会产生永远匹配不到任何条目的空段；node
      // glob 容忍该形式，因此遍历器必须立刻拒绝，而不是静默返回空。
      throw new TypeError(`repo-files walker does not model empty segments in pattern: ${pattern}`)
    }
    segments.push(segment)
  }
  if (segments.length === 0) {
    throw new TypeError(`repo-files walker does not model a pattern with no segments: ${pattern}`)
  }
  for (const segment of segments) {
    if (segment !== '**') assertSupportedSegment(segment)
  }
  // 尾随 `**` 会匹配前缀之下的文件、目录与符号链接；遍历器只把 `**` 建模
  // 为跨目录的段，因此必须立刻拒绝该形式，而不是静默返回空。
  if (segments[segments.length - 1] === '**') {
    throw new TypeError(`repo-files walker does not model a trailing ** segment in pattern: ${pattern}`)
  }
  const out: GlobMatch[] = []

  const visit = (dirAbs: string, dirRel: string, index: number, viaSymlink: boolean): void => {
    if (index >= segments.length) return
    if (segments[index] !== '**') {
      visitSegment(dirAbs, dirRel, index, viaSymlink)
      return
    }
    // 此处 `**` 消耗零个目录，每层递归消耗一个目录。
    visitSegment(dirAbs, dirRel, index + 1, viaSymlink)
    for (const entry of listDirectory(dirAbs)) {
      if (entry.name.startsWith('.') || !entry.isDirectory()) continue
      const childRel = dirRel === '.' ? entry.name : `${dirRel}/${entry.name}`
      if (skipDirectory(childRel)) continue
      visit(join(dirAbs, entry.name), childRel, index, viaSymlink)
    }
  }

  const visitSegment = (dirAbs: string, dirRel: string, index: number, viaSymlink: boolean): void => {
    if (index >= segments.length) return
    const segment = segments[index]
    if (segment === undefined) return
    if (segment === '**') {
      visit(dirAbs, dirRel, index, viaSymlink)
      return
    }
    const last = index === segments.length - 1
    for (const entry of listDirectory(dirAbs)) {
      if (!segmentMatches(segment, entry.name)) continue
      const childAbs = join(dirAbs, entry.name)
      const childRel = dirRel === '.' ? entry.name : `${dirRel}/${entry.name}`
      if (last) {
        // 文件与符号链接都会匹配；损坏或成环的符号链接随后在调用方的
        // realpathSync 中立刻失败，与 node glob 一致，而不是静默缩小被
        // 扫描的语料。
        if (entry.isFile() || entry.isSymbolicLink()) {
          out.push({ path: childRel, viaSymlink: viaSymlink || !entry.isFile() })
        }
        continue
      }
      if (entry.isDirectory()) {
        if (!skipDirectory(childRel)) visitSegment(childAbs, childRel, index + 1, viaSymlink)
        continue
      }
      // 字面非末段像 node glob 一样经由 stat 解析，因此会进入符号链接
      // 目录；通配段按 dirent 类型在上文匹配，不进入此分支。
      if (!entry.isSymbolicLink() || hasWildcard(segment)) continue
      let target: ReturnType<typeof statSync>
      try {
        target = statSync(childAbs)
      } catch {
        // 损坏的符号链接在字面段下匹配不到任何内容，正如 node glob 对它
        // 静默返回无匹配。
        continue
      }
      if (!target.isDirectory() || skipDirectory(childRel)) continue
      visitSegment(childAbs, childRel, index + 1, true)
    }
  }

  visit(root, '.', 0, false)
  return out.sort((left, right) => left.path < right.path ? -1 : left.path > right.path ? 1 : 0)
}

/** 判断模式段是否含 `*` 或 `?`，即是否为通配段。 */
function hasWildcard(segment: string): boolean {
  return segment.includes('*') || segment.includes('?')
}

/**
 * 展开相对仓库的 glob 并对符号链接文件去重。每次调用中每个目录至多列出
 * 一次，且在多个模式间共享。只经由真实目录到达的普通文件，其规范化路径
 * 为 `realpathSync(root)` 与其相对路径的拼接；其余匹配一律经过
 * `realpathSync`，因此损坏或成环的符号链接仍会抛出。
 * @param root - 仓库绝对根路径。
 * @param patterns - 相对仓库的 glob 模式，按顺序处理。
 * @param isExcluded - 可选谓词，作用于每条匹配的相对路径。
 * @param skipDirectory - 可选谓词，在遍历器列出某个相对仓库目录之前调用；仅当该
 *   目录下每条路径都满足 `isExcluded` 时才返回 true。
 * @returns 按首次出现顺序稳定排列的匹配文件。
 */
export function uniqueRepoFiles(
  root: string,
  patterns: readonly string[],
  isExcluded: (relativePath: string) => boolean = () => false,
  skipDirectory: (relativeDirectory: string) => boolean = () => false,
): RepoFile[] {
  const listings = new Map<string, readonly Dirent[]>()
  const listDirectory = (dirAbs: string): readonly Dirent[] => {
    let entries = listings.get(dirAbs)
    if (entries === undefined) {
      entries = readdirSync(dirAbs, { withFileTypes: true })
      listings.set(dirAbs, entries)
    }
    return entries
  }
  let realRoot: string | undefined
  const seen = new Set<string>()
  const files: RepoFile[] = []
  for (const pattern of patterns) {
    for (const { path: repoPath, viaSymlink } of expandGlob(root, pattern, listDirectory, skipDirectory)) {
      if (isExcluded(repoPath)) continue
      const abs = resolve(root, repoPath)
      const real = viaSymlink ? realpathSync(abs) : join(realRoot ??= realpathSync(root), repoPath)
      if (seen.has(real)) continue
      seen.add(real)
      files.push({ abs, real })
    }
  }
  return files
}

/**
 * 逐行扫描正则匹配，返回被调用方谓词拒绝的规范化匹配。
 * @param root - 用于生成违规路径的仓库绝对根路径。
 * @param absPath - 待扫描的绝对文本文件路径。
 * @param pattern - 逐行独立匹配的全局正则。
 * @param normalize - 把原始正则文本映射为门禁评估的引用。
 * @param isViolation - 规范化引用无效时返回 true。
 * @param excludedRange - 可选选择器，返回一个 0 基、结束端不含的行区间，该区间不扫描。
 * @returns 按源顺序排列的全部被拒绝引用。
 */
export function findReferenceViolations(
  root: string,
  absPath: string,
  pattern: RegExp,
  normalize: (raw: string) => string,
  isViolation: (ref: string) => boolean,
  excludedRange?: (file: string, source: string) => readonly [number, number] | undefined,
): ReferenceViolation[] {
  const file = relative(root, absPath).split(sep).join('/')
  const out: ReferenceViolation[] = []
  const source = readFileSync(absPath, 'utf8')
  const lines = splitLines(source)
  const excluded = excludedRange?.(file, source)
  for (let i = 0; i < lines.length; i++) {
    if (excluded !== undefined && i >= excluded[0] && i < excluded[1]) continue
    const line = lines[i]
    if (line === undefined) continue
    for (const match of line.matchAll(pattern)) {
      const ref = normalize(match[0])
      if (isViolation(ref)) out.push({ file, line: i + 1, ref })
    }
  }
  return out
}
