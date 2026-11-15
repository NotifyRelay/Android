/**
 * 重复代码的扫描与清单渲染。
 *
 * 语料由 git 给出，不由 glob 拼出：`git ls-files` 逐级应用各级 `.gitignore`，构建产物、
 * `node_modules` 与本地配置因此永不进入扫描，新增规则也只需改 `.gitignore`。已跟踪与未
 * 跟踪但未被忽略的文件都在语料内，未提交的新源码因此与提交后一样受检。子模块内容按
 * `--recurse-submodules` 列出，各自应用所属仓库的 `.gitignore`。
 *
 * 门禁按克隆逐条比对基线；清单按人读的口径把同一对文件里位置重叠的克隆合并成一个重复点，
 * 并按「能否在本仓修」排序。两者共用同一次扫描，口径不同，条数因此不同。
 *
 * @module scripts/duplication-report
 */

import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { relative, resolve } from 'node:path'
import { detectClones } from 'jscpd'
import { repoRoot } from './doc-system.config.ts'

const root = repoRoot

/** 扫描参数；与基线一致，改动会让整份基线失效。 */
export const MIN_LINES = 5
export const MIN_TOKENS = 50

/** 语料扩展名。 */
const SOURCE_PATTERNS = ['*.kt', '*.kts', '*.rs', '*.ts']

/** jscpd 分词格式，覆盖 SOURCE_PATTERNS 的全部扩展名。 */
const FORMATS = ['kotlin', 'rust', 'typescript']

/** 属于子模块的源码前缀；这些文件的改动只能在所属仓库进行。 */
export const SUBMODULE_PREFIXES = ['scrcpy/', 'checkupdata/', 'nativecore/notify-relay-core/', 'desktop/logi-battery/', 'desktop/smtc-rust/']

/** 一段克隆。 */
export type Clone = Awaited<ReturnType<typeof detectClones>>[number]

/**
 * 列出待扫描的源文件。
 *
 * jscpd 对 Windows 反斜杠绝对路径会静默扫不到文件（返回空结果），因此路径统一成正斜杠
 * 形式。
 *
 * @returns 绝对路径数组。
 */
export function sourceFiles(): string[] {
  const list = (extra: readonly string[]): string[] =>
    execFileSync('git', ['ls-files', '-z', ...extra, '--', ...SOURCE_PATTERNS], { cwd: root, encoding: 'utf8' })
      .split('\0')
      .filter(Boolean)
  // 已从工作区删除但尚未暂存的路径仍在索引里；jscpd 对不存在的路径会整次扫描抛错，
  // 因此先滤掉，使门禁在删除文件后立刻可用而不必先 git add。
  return [...list(['--cached', '--recurse-submodules']), ...list(['--others', '--exclude-standard'])]
    .map(path => resolve(root, path).replaceAll('\\', '/'))
    .filter(path => existsSync(path))
}

/**
 * 扫描语料中的克隆。
 *
 * @returns 克隆列表。
 */
export function scanClones() {
  return detectClones({
    path: sourceFiles(),
    minLines: MIN_LINES,
    minTokens: MIN_TOKENS,
    format: FORMATS,
    absolute: true,
    silent: true,
    reporters: [],
  })
}

/** 一处重复在某一侧的定位。 */
interface Site {
  /** 相对仓库的正斜杠路径。 */
  readonly file: string
  /** 1 基行号区间，已按升序归一。 */
  readonly lines: readonly [number, number]
}

/** 一个重复点：同一对文件里位置重叠的多条克隆合并后的结果。 */
export interface Block {
  readonly a: Site
  readonly b: Site
  /** 两侧中较长一侧的行数。 */
  readonly lines: number
  /** 合并的克隆条数。 */
  readonly clones: number
  /** 两侧片段是否都只由 import、use 与 package 声明构成。 */
  readonly importOnly: boolean
  /** 任一侧是否在子模块内。 */
  readonly submodule: boolean
  /** 任一侧是否是测试源码。 */
  readonly test: boolean
  /** 两侧是否是同一份源码的 androidMain 与 jvmMain 实现。 */
  readonly mirror: boolean
  /** 片段前几行，用于在清单里定位。 */
  readonly firstLine: string
  /** 一侧所在的声明。 */
  readonly declarationA: string
  /** 另一侧所在的声明。 */
  readonly declarationB: string
}

/** 一侧的字节区间（升序）。 */
interface ByteRange {
  /** 片段起点的字节偏移。 */
  readonly from: number
  /** 片段终点的字节偏移。 */
  readonly to: number
}

/** 把克隆某一侧的字节区间归一成升序。 */
function range(clone: Clone, side: 'duplicationA' | 'duplicationB'): ByteRange {
  const part = clone[side]
  return { from: Math.min(part.range[0], part.range[1]), to: Math.max(part.range[0], part.range[1]) }
}

const textCache = new Map<string, string>()

/**
 * 读取源文件全文。
 *
 * @param file - 相对仓库的路径。
 * @returns 文件内容；读不到时为空串，使行号计算退化为零区间而不抛错。
 */
function sourceText(file: string): string {
  let text = textCache.get(file)
  if (text === undefined) {
    try {
      text = readFileSync(resolve(root, file), 'utf8')
    } catch {
      text = ''
    }
    textCache.set(file, text)
  }
  return text
}

/**
 * 按字节偏移算 1 基行号区间。
 *
 * 行号取自偏移而非 jscpd 的 `start.line`/`end.line`：片段与 `slice(from, to)` 逐字节相同，
 * 偏移因此是权威定位；jscpd 自报的行号在合并相邻克隆后会有偏差，照抄会指错行。
 *
 * @param file - 相对仓库的路径。
 * @param span - 字节区间。
 * @returns 闭区间行号；区间为空时两端都是 0。
 */
function linesOf(file: string, span: ByteRange): readonly [number, number] {
  const text = sourceText(file)
  if (span.to <= span.from) return [0, 0]
  const first = text.slice(0, span.from).split('\n').length
  let last = text.slice(0, span.to).split('\n').length
  const lastChar = text[span.to - 1]
  if (last > first && (lastChar === '\n' || lastChar === '\r')) last -= 1
  return [first, last]
}

/**
 * 判断整条克隆的两侧是否都只由 import、use 与 package 声明构成。
 *
 * 门禁按克隆逐条比对，因此分级接受也在同一粒度上判定，不借用合并后的重复点。
 *
 * @param clone - 一段克隆。
 * @returns 两侧都是纯 import 时为 true。
 */
export function isImportOnlyClone(clone: Clone): boolean {
  const a = relative(root, clone.duplicationA.sourceId).split('\\').join('/')
  const b = relative(root, clone.duplicationB.sourceId).split('\\').join('/')
  return (
    isImportOnlyRange(a, range(clone, 'duplicationA'), clone.duplicationA.fragment ?? '') &&
    isImportOnlyRange(b, range(clone, 'duplicationB'), clone.duplicationB.fragment ?? '')
  )
}

/**
 * 判断某个字节区间覆盖的源码行是否只由 import、use 与 package 声明构成。
 *
 * 按行判定而不按片段判定：jscpd 的片段区间对齐到词元，可能从词中间开始（出现裸 `impo`），
 * 按片段判会把整段 import 误判成逻辑重复。
 *
 * 空行与注释不计入：它们是分组与说明，不改变这段代码的性质。
 *
 * @param file - 相对仓库的路径。
 * @param span - 字节区间。
 * @returns 去掉空行与注释后全部是声明行时为 true。
 */
function isImportOnlyRange(file: string, span: ByteRange, fragment: string): boolean {
  // 行数取自片段、内容取自源码：jscpd 的字节区间常把下一段的首行一并圈进来，而片段本身
  // 不会；反过来片段又可能从词中间开始（出现裸 `impo`），所以两边各取所长。
  const [first] = linesOf(file, span)
  const count = fragment.replace(/\r\n?/g, '\n').split('\n').length
  if (first < 1 || count < 1) return isDeclarationsOnly(fragment.split(/\r?\n/))
  return isDeclarationsOnly(sourceText(file).split(/\r?\n/).slice(first - 1, first - 1 + count))
}

/**
 * 判断一组源码行去掉空行与注释后，是否只剩下 import、use 与 package 声明。
 *
 * 空行与注释不计入：它们是分组与说明，不改变这段代码的性质。
 *
 * @param lines - 源码行，可含空行与注释。
 * @returns 至少有一行声明，且每一行都是声明时为 true。
 */
function isDeclarationsOnly(lines: readonly string[]): boolean {
  const payload = lines
    .map(line => line.trim())
    .filter(line => line !== '' && !/^(\/\/|\/\*|\*|#(?!include))/.test(line))
  return payload.length > 0 && payload.every(line => /^(import |use |package |#include |extern crate |mod )/.test(line))
}

/** 判断路径是否属于子模块。 */
export function isSubmodulePath(path: string): boolean {
  return SUBMODULE_PREFIXES.some(prefix => path.startsWith(prefix))
}

/** 判断路径是否属于测试源码。 */
export function isTestSourcePath(path: string): boolean {
  return /(^|\/)tests?\//i.test(path) || /tests?\.(rs|kt|ts)$/i.test(path) || /(^|\/)(androidTest|jvmTest|commonTest)\//i.test(path)
}

/** 判断两侧是否是同一份源码的 androidMain 与 jvmMain 实现。 */
function isPlatformMirror(a: string, b: string): boolean {
  const platform = /\.(android|jvm)\.(kt|ts)$/
  if (!platform.test(a) || !platform.test(b)) return false
  const stem = (path: string) => (path.split('/').pop() ?? '').replace(platform, '')
  return stem(a) === stem(b)
}

const sourceCache = new Map<string, string[]>()

/**
 * 回溯到覆盖该行的最近一个声明。
 *
 * 只看 60 行以内：更远的多半是文件级 import，不代表这段代码的角色。
 *
 * @param file - 相对仓库的路径。
 * @param line - 1 基行号。
 * @returns 声明行文本；找不到时为空串。
 */
function declarationAt(file: string, line: number): string {
  let text = sourceCache.get(file)
  if (text === undefined) {
    try {
      text = readFileSync(resolve(root, file), 'utf8').split(/\r?\n/)
    } catch {
      text = []
    }
    sourceCache.set(file, text)
  }
  const declaration = /^\s*(?:@\w+\s*)*\s*(?:public |private |internal |protected |override |suspend |inline |open |abstract |data |sealed |static |async |pub |unsafe |extern |const |expect |actual )*(?:fun |class |object |interface |struct |enum |impl |fn |typealias |val |var )\b/
  for (let index = Math.min(line - 1, text.length - 1); index >= 0 && index > line - 60; index -= 1) {
    const candidate = text[index] ?? ''
    if (declaration.test(candidate)) return candidate.trim().replace(/\s*\{?\s*$/, '').slice(0, 100)
  }
  return ''
}

/**
 * 按克隆的文件对分组。
 *
 * 文件对无方向：A 侧与 B 侧互换不改变重复点的身份。
 *
 * @param clones - 克隆列表。
 * @returns 文件对到克隆的映射。
 */
function groupByFilePair(clones: readonly Clone[]): Map<string, Clone[]> {
  const byPair = new Map<string, Clone[]>()
  for (const clone of clones) {
    const pair = [relative(root, clone.duplicationA.sourceId), relative(root, clone.duplicationB.sourceId)]
      .map(name => name.split('\\').join('/'))
      .sort()
      .join('||')
    const bucket = byPair.get(pair)
    if (bucket === undefined) byPair.set(pair, [clone])
    else bucket.push(clone)
  }
  return byPair
}

/**
 * 把同一对文件里字节区间相邻或重叠的克隆合并成一个重复点。
 *
 * 合并按字节区间而非行号：jscpd 自报的行号会偏，用它会漏合并或误合并。
 *
 * @param clones - 克隆列表。
 * @returns 重复点，顺序未定义。
 */
export function mergeIntoBlocks(clones: readonly Clone[]): Block[] {
  const blocks: Block[] = []
  for (const group of groupByFilePair(clones).values()) {
    const merged: { a: ByteRange; b: ByteRange; clones: number; fragmentA: string; fragmentB: string; fileA: string; fileB: string }[] = []
    for (const clone of group) {
      const a = range(clone, 'duplicationA')
      const b = range(clone, 'duplicationB')
      const hit = merged.find(item => a.from <= item.a.to + 1 && a.to >= item.a.from - 1 && b.from <= item.b.to + 1 && b.to >= item.b.from - 1)
      if (hit === undefined) {
        merged.push({
          a,
          b,
          clones: 1,
          fragmentA: clone.duplicationA.fragment ?? '',
          fragmentB: clone.duplicationB.fragment ?? '',
          fileA: relative(root, clone.duplicationA.sourceId).split('\\').join('/'),
          fileB: relative(root, clone.duplicationB.sourceId).split('\\').join('/'),
        })
        continue
      }
      hit.a = { from: Math.min(hit.a.from, a.from), to: Math.max(hit.a.to, a.to) }
      hit.b = { from: Math.min(hit.b.from, b.from), to: Math.max(hit.b.to, b.to) }
      hit.clones += 1
      if (a.to - a.from > hit.a.to - hit.a.from) hit.fragmentA = clone.duplicationA.fragment ?? ''
      if (b.to - b.from > hit.b.to - hit.b.from) hit.fragmentB = clone.duplicationB.fragment ?? ''
    }
    for (const item of merged) {
      const linesA = linesOf(item.fileA, item.a)
      const linesB = linesOf(item.fileB, item.b)
      const text = item.fragmentA.split(/\r?\n/).map(line => line.trim()).filter(Boolean)
      // 两侧都必须是纯 import 才归入 import 块：只判一侧会让「一侧 import、另一侧是代码」
      // 的克隆冒充成 import 重复。
      const importOnly =
        isImportOnlyRange(item.fileA, item.a, item.fragmentA) && isImportOnlyRange(item.fileB, item.b, item.fragmentB)
      blocks.push({
        a: { file: item.fileA, lines: linesA },
        b: { file: item.fileB, lines: linesB },
        lines: Math.max(linesA[1] - linesA[0] + 1, linesB[1] - linesB[0] + 1),
        clones: item.clones,
        importOnly,
        submodule: isSubmodulePath(item.fileA) || isSubmodulePath(item.fileB),
        test: isTestSourcePath(item.fileA) || isTestSourcePath(item.fileB),
        mirror: isPlatformMirror(item.fileA, item.fileB),
        firstLine: text.slice(0, 4).join(' ').slice(0, 100),
        declarationA: declarationAt(item.fileA, linesA[0]),
        declarationB: declarationAt(item.fileB, linesB[0]),
      })
    }
  }
  return blocks
}

/** 报告的一个优先级层。 */
interface Tier {
  /** 稳定标识。 */
  readonly id: string
  /** 层标题。 */
  readonly title: string
  /** 该层为什么按这个优先级处理。 */
  readonly rationale: string
  /** 处理归属。 */
  readonly owner: string
}

/** 优先级从高到低。 */
const TIERS: readonly Tier[] = [
  {
    id: 'p0',
    title: 'P0 主仓跨模块生产逻辑',
    rationale: '两侧是不同模块的生产实现，同一段逻辑被各写一遍，应抽到 `shared` 或 `domain` 的公共实现共用；不合并则两端行为会各自漂移。',
    owner: '本仓',
  },
  {
    id: 'p1',
    title: 'P1 主仓同模块生产逻辑',
    rationale: '两侧同在一个模块内，多为相近的 ViewModel、同步管理器或 Compose 组合函数，抽成该模块内的私有 helper 或公共组件即可收敛。',
    owner: '本仓',
  },
  {
    id: 'p2',
    title: 'P2 主仓平台镜像实现',
    rationale: '两侧是同一份逻辑在 `androidMain` 与 `jvmMain` 的两套实现。平台 API 不同决定整份不能共用，但不触碰平台调用的段落可以下沉到 `commonMain`。',
    owner: '本仓',
  },
  {
    id: 'p3',
    title: 'P3 主仓 import 与包声明块',
    rationale: '两侧重合的是 import 与包声明，多来自同一套框架的固定引入；改写价值低于其余类别，已接受为基线。',
    owner: '本仓',
  },
  {
    id: 'p4',
    title: 'P4 子模块生产代码',
    rationale: '两侧位于子模块，只能在所属仓库修改，本仓门禁与 CI 不覆盖它们；处理后需更新子模块指针。',
    owner: '子模块仓库',
  },
  {
    id: 'p5',
    title: 'P5 主仓测试代码',
    rationale: '两侧是主仓测试代码，重复来自相同的 fixture、断言前奏或假实现；抽到该测试源集的共享 helper 后改断言只需改一处。',
    owner: '本仓',
  },
  {
    id: 'p6',
    title: 'P6 子模块测试代码',
    rationale: '两侧是子模块内的测试代码，同样只能在所属仓库修改。',
    owner: '子模块仓库',
  },
]

/**
 * 判断一处重复属于哪一层。
 *
 * import 块单独成层而不按模块归属：它几乎总是框架固定引入的副产物，与「逻辑被写了两遍」
 * 不是同一类问题。子模块先于测试判定，因为改动归属比代码性质更决定处理顺序。
 *
 * 克隆与重复点共用本函数：门禁按克隆逐条比对基线，分级接受也必须在克隆粒度上判定，
 * 否则接受的条数与报告的条数会对不上。
 *
 * @param facts - 判定所需的事实：两侧路径与片段性质。
 * @returns 该层标识。
 */
function layerOf(facts: { a: string; b: string; importOnly: boolean; submodule: boolean; test: boolean; mirror: boolean }): string {
  if (facts.importOnly) return facts.submodule ? 'p4' : 'p3'
  if (facts.submodule) return facts.test ? 'p6' : 'p4'
  if (facts.test) return 'p5'
  if (facts.mirror) return 'p2'
  return facts.a.split('/')[0] === facts.b.split('/')[0] ? 'p1' : 'p0'
}

/**
 * 判断一处重复点属于哪一层。
 *
 * @param block - 重复点。
 * @returns 该层标识。
 */
function tierOf(block: Block): string {
  return layerOf({
    a: block.a.file,
    b: block.b.file,
    importOnly: block.importOnly,
    submodule: block.submodule,
    test: block.test,
    mirror: block.mirror,
  })
}

/** 分级接受的合法层标识。 */
export const LAYERS = ['p0', 'p1', 'p2', 'p3', 'p4', 'p5', 'p6'] as const

/**
 * 判断单条克隆属于哪一层。
 *
 * @param clone - 一段克隆。
 * @returns 该层标识。
 */
export function layerOfClone(clone: Clone): string {
  const a = relative(root, clone.duplicationA.sourceId).split('\\').join('/')
  const b = relative(root, clone.duplicationB.sourceId).split('\\').join('/')
  return layerOf({
    a,
    b,
    importOnly: isImportOnlyClone(clone),
    submodule: isSubmodulePath(a) || isSubmodulePath(b),
    test: isTestSourcePath(a) || isTestSourcePath(b),
    mirror: isPlatformMirror(a, b),
  })
}

/**
 * 生成一句话描述：这段重复是什么。
 *
 * @param block - 重复点。
 * @returns 描述文本。
 */
function describe(block: Block): string {
  const code = (text: string) => `\`${text.replace(/\`/g, "'").slice(0, 90)}\``
  const short = (text: string) => text.replace(/\s+/g, ' ').trim().slice(0, 70)
  const parts: string[] = []
  if (block.importOnly) {
    parts.push(block.a.file === block.b.file ? '同一文件内两处相同的 import 组' : '相同的 import 与包声明组')
  } else if (block.a.file === block.b.file) {
    parts.push(block.declarationA === '' ? '同一文件内两处重复代码' : `同一文件内两处重复实现，位于 ${code(short(block.declarationA))}`)
  } else if (block.declarationA !== '' && block.declarationB !== '' && block.declarationA !== block.declarationB) {
    parts.push(`${code(short(block.declarationA))} 与 ${code(short(block.declarationB))}`)
  } else if (block.declarationA !== '' || block.declarationB !== '') {
    parts.push(code(short(block.declarationA !== '' ? block.declarationA : block.declarationB)))
  }
  if (!block.importOnly && block.firstLine !== '') parts.push(`重合起点 ${code(block.firstLine)}`)
  return parts.length === 0 ? '相同的代码段' : parts.join('，')
}

/**
 * 渲染整份清单。
 *
 * @param clones - 本次扫描的克隆。
 * @returns Markdown 全文。
 */
export function renderReport(clones: readonly Clone[]): string {
  const blocks = mergeIntoBlocks(clones)
  const buckets = new Map(TIERS.map(tier => [tier.id, [] as Block[]]))
  for (const block of blocks) buckets.get(tierOf(block))?.push(block)
  const lineCount = (list: readonly Block[]) => list.reduce((total, block) => total + block.lines, 0)
  for (const list of buckets.values()) list.sort((left, right) => right.lines - left.lines || left.a.file.localeCompare(right.a.file))
  const site = (part: Site) => `\`${part.file}\` :${part.lines[0]}${part.lines[1] === part.lines[0] ? '' : `-${part.lines[1]}`}`
  const cell = (text: string) => text.replaceAll('|', '\\|')

  const out: string[] = [
    '# 重复代码清单',
    '',
    '本文是 `pnpm run verify-duplication` 扫描结果的完整列表，由 `pnpm run verify-duplication:report` 生成；重扫会整体重写，不要手工编辑。门禁口径、基线规则与调高基线的做法见 `Docs/构建与验证.md` 的「重复代码门禁」。',
    '',
    `扫描参数 \`minLines: ${MIN_LINES}\`、\`minTokens: ${MIN_TOKENS}\`，语料为 \`git ls-files\` 列出的 Kotlin、Rust 与 TypeScript 源码。jscpd 会把同一处重复按滑动窗口拆成多条克隆，下表按「文件对 + 字节区间重叠」合并为独立重复点，因此条目数少于门禁报出的 ${clones.length} 条克隆。`,
    '',
    `**共 ${blocks.length} 个重复点，合计 ${lineCount(blocks)} 行。**「行数」取两侧中较长的一侧；位置为 1 基行号区间，按字节偏移换算。`,
    '',
    '## 优先级总览',
    '',
    '排序依据：能否在本仓直接修、修完是否真的减少维护面。能被本仓修掉的生产逻辑在前，只能在子模块改的在后，import 块因改写价值低而单列。',
    '',
    '| 优先级 | 类别 | 重复点 | 行数 | 处理归属 |',
    '|---|---|---|---|---|',
  ]
  for (const tier of TIERS) {
    const list = buckets.get(tier.id) ?? []
    if (list.length === 0) continue
    out.push(`| ${tier.title.slice(0, 2)} | ${tier.title.slice(3)} | ${list.length} | ${lineCount(list)} | ${tier.owner} |`)
  }
  out.push('')
  for (const tier of TIERS) {
    const list = buckets.get(tier.id) ?? []
    if (list.length === 0) continue
    out.push(
      `## ${tier.title}`,
      '',
      `${tier.rationale}共 ${list.length} 个重复点、${lineCount(list)} 行。`,
      '',
      '| # | 行数 | 一侧 | 另一侧 | 简述 |',
      '|---|---|---|---|---|',
    )
    list.forEach((block, index) => {
      out.push(`| ${index + 1} | ${block.lines} | ${cell(site(block.a))} | ${cell(site(block.b))} | ${cell(describe(block))} |`)
    })
    out.push('')
  }
  return out.join('\n') + '\n'
}
