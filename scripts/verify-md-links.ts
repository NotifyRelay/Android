/**
 * 校验相对 Markdown 链接、图片与定义可解析——目标文件必须存在，且指向
 * Markdown 目标的 `#fragment`（包括同文件 `#anchor`）必须命名真实标题
 * slug 或显式 `<a id>`。URL 目标与根绝对目标被排除；查询字符串不影响
 * 相对源文件的解析。检查器从不改写；符号链接形式的指令文件会被去重。
 */

import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { dirname, join, relative, resolve, sep } from 'node:path'
import type { Nodes } from 'mdast'
import { markdownHeadingLines, parseMarkdown, visitMarkdown } from './markdown.ts'
import { isArchivedAgentNotePath, uniqueRepoFiles } from './repo-files.ts'
import { docSystem, repoRoot } from './doc-system.config.ts'

const root = repoRoot

/**
 * 发现手写 Markdown 源文件，并对符号链接去重。归档笔记不作为源文件，但
 * 指向它们的链接仍会被检查。
 * @param scanRoot - 仓库绝对根路径；把同一根传给 findViolations 以获得一致的诊断。
 * @returns 相对 scanRoot 的正斜杠源路径。
 */
export function markdownLinkSourcePaths(scanRoot: string = root): string[] {
  return uniqueRepoFiles(scanRoot, docSystem.markdownPatterns, isArchivedAgentNotePath)
    .map(file => relative(scanRoot, file.abs).replaceAll('\\', '/'))
}

/** 一处失效的相对链接：目标路径缺失（或大小写与实际名字不符），或目标上缺少锚点。 */
interface Violation {
  file: string
  /** 链接/图片/定义节点起始处的 1 基行号。 */
  line: number
  url: string
  /** 失败对象：目标文件（含大小写不符）或其上的 fragment。 */
  reason: 'target' | 'anchor'
}

/**
 * 对本门禁不得检查的目标返回 true：带 scheme 的 URL（`https:`、
 * `mailto:` 等）、协议相对（`//host`）与根绝对（`/path`）。纯页内锚点
 * （`#frag`）要检查，检查对象是源文件自身。
 */
function isExternal(url: string): boolean {
  if (url.startsWith('//')) return true
  if (url.startsWith('/')) return true
  // 形如 `https:` / `mailto:` 的 scheme——任何斜杠、点或井号之前出现冒号。
  return /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(url)
}

/**
 * 先剥掉链接目标中的 `#fragment` 与 `?query`，再对剩余路径做百分号解码，
 * 使编码后的目标（`My%20File.md`、`READ%4DE.md`）按 Markdown 渲染器的方式
 * 探测磁盘上的真实文件名。畸形转义（`%zz`）会让 `decodeURIComponent`
 * 抛出；此时保留原始路径，使该链接被报告为失效（`%zz` 目标并非任何人
 * 有意链接的文件），而不是让门禁崩溃。
 */
function pathPart(url: string): string {
  const raw = url.replace(/[#?].*$/, '')
  try {
    return decodeURIComponent(raw)
  } catch {
    // decodeURIComponent 仅在百分号转义畸形时抛出；此时原始字符串是任何
    // 渲染器都无法解析的路径，因此继续执行存在性检查，由它报告为失效。
    return raw
  }
}

/** 链接目标中百分号解码后的 `#fragment`；无 fragment 时为 null。 */
function fragmentPart(url: string): string | null {
  const hash = url.indexOf('#')
  if (hash === -1) return null
  const raw = url.slice(hash + 1).replace(/\?.*$/, '')
  try {
    return decodeURIComponent(raw)
  } catch {
    // 与 pathPart 同一立场：畸形转义不构成任何人有意指定的锚点，因此原始
    // 文本进入查找并被报告为缺失。
    return raw
  }
}

/**
 * GitHub 的标题 slug 算法（转小写；除字母、数字、下划线、空格、连字符
 * 之外全部丢弃；空格变连字符）。下划线保留（`## Showcase: web_fetch` →
 * `#showcase-web_fetch`）。生成器产出的区域锚点走各自显式的 `<a id>`，
 * 与本函数计算的标题锚点是两套规则，因此无需保持一致。
 * @param heading - 渲染后的标题文本（Markdown 语法已被去除）。
 * @returns GitHub 赋予该标题首次出现的锚点。
 */
export function githubSlug(heading: string): string {
  return heading.toLowerCase().replace(/[^\p{L}\p{N}_ -]/gu, '').replaceAll(' ', '-')
}

/**
 * 一个 Markdown 文档暴露的全部锚点：每个标题的 GitHub slug——按渲染后的
 * 标题文本计算，因此标题内的链接、图片、行内代码与强调按其 GitHub 渲染
 * 结果参与 slug——加上出现在真实 HTML 流中的每个显式 `<a id="…">`（围栏
 * 代码或行内代码示例、被注释掉的锚点都不注册任何锚点）。重复 slug 按
 * GitHub 的占用集合获得 `-1`、`-2`、… 后缀：每次冲突都递增原 slug 的
 * 计数器，直到找到空闲名字，因此 `Repeat`、`Repeat-1`、`Repeat` 得到
 * `repeat`、`repeat-1`、`repeat-2`。匹配是精确的——元素 id 区分大小写。
 * @param source - 文档的完整 Markdown 文本。
 * @param tree - 当调用方已持有 `source` 的 GFM 解析结果时传入。
 * @returns 指向本文档的链接可用的合法 fragment 集合。
 */
export function documentAnchors(source: string, tree: Nodes = parseMarkdown(source)): Set<string> {
  const anchors = new Set<string>()
  const occurrences = new Map<string, number>()
  for (const heading of markdownHeadingLines(source, tree)) {
    const base = githubSlug(heading.text)
    let result = base
    let bump = occurrences.get(base) ?? 0
    while (anchors.has(result)) {
      bump += 1
      result = `${base}-${bump}`
    }
    occurrences.set(base, bump)
    anchors.add(result)
  }
  visitMarkdown(tree, (node: Nodes): void => {
    if (node.type !== 'html') return
    const html = node.value.replace(/<!--[\s\S]*?-->/g, '')
    for (const match of html.matchAll(/<a id="([^"]+)"/g)) anchors.add(match[1] ?? '')
  })
  return anchors
}

/** 单个 Markdown 文件的链接输入与暴露锚点，由一次解析收集。 */
export interface MarkdownLinkDocument {
  /** 链接、图片与定义目标及其 1 基起始行号，按文档顺序排列。 */
  destinations: { url: string; line: number }[]
  /** 指向本文档的链接可命名的 fragment。 */
  anchors: Set<string>
}

/** 每次运行内由所有被扫描源与链接目标共享的读取、解析与目标探测。 */
export interface MarkdownLinkCache {
  /** 每次运行中至多读取并解析一个 Markdown 文件一次。 */
  document: (absPath: string) => MarkdownLinkDocument
  /** 已解析的链接目标是否存在，每个路径至多探测一次。 */
  exists: (absPath: string) => boolean
  /**
   * 该路径的每一段是否与磁盘上的实际名字逐字符同大小写。
   *
   * 不区分大小写的文件系统（Windows、macOS 默认）让大小写写错的链接照常打开，
   * 同一份文档在区分大小写的 Linux 上却是断链。因此本门禁不依赖文件系统的宽容度，
   * 自己逐段核对。缓存按绝对路径记忆，每条路径至多枚举一次目录。
   */
  caseExact: (absPath: string) => boolean
}

/**
 * 逐段核对路径与磁盘上的实际名字是否同大小写。
 *
 * 从根开始逐段枚举父目录，要求每一段都精确出现在条目里；任一段不存在即返回
 * false。盘符段（`C:`）没有大小写变体，按原样拼接。
 *
 * @param absPath - 待核对的绝对路径。
 * @returns 每一段都精确匹配时为 true；某一级目录无法枚举或某段不存在时为 false。
 */
function isCaseExact(absPath: string): boolean {
  const absolute = resolve(absPath)
  const parts = absolute.split(/[\\/]/).filter((part) => part !== '')
  let current = absolute.startsWith('/') ? '/' : ''
  for (const part of parts) {
    // 盘符段（`C:`）没有大小写变体，按原样拼接。
    if (/^[A-Za-z]:$/.test(part)) {
      current = part + sep
      continue
    }
    let entries: string[]
    try {
      entries = readdirSync(current === '' ? '.' : current)
    } catch {
      return false
    }
    if (!entries.includes(part)) return false
    current = current === '' ? part : join(current, part)
  }
  return true
}

function markdownLinkDocument(source: string): MarkdownLinkDocument {
  const tree = parseMarkdown(source)
  const destinations: MarkdownLinkDocument['destinations'] = []
  visitMarkdown(tree, (node: Nodes): void => {
    if ((node.type === 'link' || node.type === 'image' || node.type === 'definition') && 'url' in node) {
      destinations.push({ url: node.url, line: node.position?.start.line ?? 0 })
    }
  })
  return { destinations, anchors: documentAnchors(source, tree) }
}

/**
 * 为一次检查器运行创建带记忆的文件读取与存在性探测。既是被扫描源又是
 * fragment 目标的文件只解析一次；运行期间假定仓库不变。
 * @returns 供本次运行中每次 {@link findViolations} 调用传入的缓存。
 */
export function markdownLinkCache(): MarkdownLinkCache {
  const documents = new Map<string, MarkdownLinkDocument>()
  const existence = new Map<string, boolean>()
  const casing = new Map<string, boolean>()
  return {
    document: (absPath) => {
      const hit = documents.get(absPath)
      if (hit) return hit
      const document = markdownLinkDocument(readFileSync(absPath, 'utf8'))
      documents.set(absPath, document)
      return document
    },
    exists: (absPath) => {
      const hit = existence.get(absPath)
      if (hit !== undefined) return hit
      const exists = existsSync(absPath)
      existence.set(absPath, exists)
      return exists
    },
    caseExact: (absPath) => {
      const hit = casing.get(absPath)
      if (hit !== undefined) return hit
      const exact = isCaseExact(absPath)
      casing.set(absPath, exact)
      return exact
    },
  }
}

/**
 * 经由 AST 找出单个 Markdown 文件中每处失效的相对交叉链接：不存在的相对
 * 目标，或指向 Markdown 文件（含同文件 `#anchor` 链接）却未命名该文件中
 * 任何标题 slug 或显式 `<a id>` 的 fragment。指向非 Markdown 目标
 * （`file.ts#L10`）的 fragment 由渲染器拥有其语义，不作判定。
 * @param absPath - 待扫描 Markdown 源的绝对路径。
 * @param cache - 跨文件共享以支持交叉链接检查的解析与目标探测。
 * @param scanRoot - 违规路径所相对报告的仓库根路径。
 * @returns 每处失效链接一条条目，按文档顺序排列。
 */
export function findViolations(
  absPath: string,
  cache: MarkdownLinkCache = markdownLinkCache(),
  scanRoot: string = root,
): Violation[] {
  const file = relative(scanRoot, absPath)
  const dir = dirname(absPath)
  const out: Violation[] = []
  for (const { url, line } of cache.document(absPath).destinations) {
    if (isExternal(url)) continue
    const target = pathPart(url)
    const resolved = target === '' ? absPath : resolve(dir, target)
    // 不区分大小写的文件系统会让写错大小写的目标照样存在，因此再核对一次实际名字。
    if (!cache.exists(resolved) || !cache.caseExact(resolved)) {
      out.push({ file, line, url, reason: 'target' })
      continue
    }
    const fragment = fragmentPart(url)
    if (fragment === null || !resolved.endsWith('.md')) continue
    if (!cache.document(resolved).anchors.has(fragment)) {
      out.push({ file, line, url, reason: 'anchor' })
    }
  }
  return out
}

if (process.argv[1] && import.meta.filename === resolve(process.argv[1])) {
  const files = markdownLinkSourcePaths(root)
  const cache = markdownLinkCache()
  const all = files.flatMap(file => findViolations(resolve(root, file), cache))
  const checked = files.length

  if (all.length === 0) {
    console.log(`verify-md-links: ${checked} file(s) checked, all relative cross-links and fragments resolve.`)
    process.exit(0)
  }

  console.error('verify-md-links: broken relative cross-links found:')
  for (const v of all) {
    console.error(`  ${v.file}:${v.line}  ${v.url}  (${v.reason === 'target' ? 'target does not exist' : 'no such anchor in target'})`)
  }
  process.exit(1)
}
