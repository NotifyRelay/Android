/** 供文档门禁共享的 Markdown 解析与深度优先遍历。 */

import { fromMarkdown } from 'mdast-util-from-markdown'
import { gfmFromMarkdown } from 'mdast-util-gfm'
import { gfm } from 'micromark-extension-gfm'
import type { Nodes } from 'mdast'
import { splitLines } from './repo-files.ts'

/** 围栏代码块与渲染时消失的 HTML 注释之外的一行手写 Markdown。 */
export interface MarkdownProseLine {
  /** 1 基源行号。 */
  index: number
  /** 未做规范化的源文本。 */
  raw: string
}

/** 一个已解析的 Markdown 标题，保留其手写首行与渲染后文本。 */
export interface MarkdownHeadingLine extends MarkdownProseLine {
  /** 解析出的 ATX 或 Setext 标题层级。 */
  depth: 1 | 2 | 3 | 4 | 5 | 6
  /** 渲染后的标题文本，不含注释等原始 HTML。 */
  text: string
}

/** 解析出的一个 Markdown 代码块。 */
export interface MarkdownFence {
  /** 起始围栏所在的 1 基源行号。 */
  line: number
  /** info string 的语言部分（其首个词），裸块或缩进块为 null。 */
  lang: string | null
  /** 完整 info string（如 `ts ignore-check`），裸块或缩进块为 ''。 */
  info: string
  /** 去掉围栏定界符后的块体。 */
  code: string
  /**
   * 是否有闭合围栏定界符终止该块——mdast 会在文件末尾静默闭合未终止的
   * 围栏。缩进（非围栏）块没有围栏定界符，其结束行仍是代码，因此为
   * false。
   */
  closed: boolean
}

/** 按仓库的标准扩展解析 GitHub 风格 Markdown。 */
export function parseMarkdown(source: string): Nodes {
  return fromMarkdown(source, { extensions: [gfm()], mdastExtensions: [gfmFromMarkdown()] })
}

/**
 * 深度优先遍历 Markdown 树；某次访问返回 false 会剪除该节点的子树。
 * @param node - 当前树节点。
 * @param visitor - 在每个节点的子节点之前调用的回调。
 */
export function visitMarkdown(node: Nodes, visitor: (node: Nodes) => boolean | void): void {
  if (visitor(node) === false) return
  if ('children' in node) {
    for (const child of node.children) visitMarkdown(child, visitor)
  }
}

/** 其手写目标占用可替换源区间的 Markdown 节点。 */
export type MarkdownDestinationNode = Extract<Nodes, { type: 'link' | 'image' | 'definition' }>

/** 一个手写 Markdown 目标及其绝对源偏移。 */
export interface MarkdownDestination {
  start: number
  end: number
  url: string
}

/** 判断 Markdown URL 是外部、仓库根绝对路径，还是纯页内。 */
export function isExternalOrAbsoluteMarkdownUrl(url: string): boolean {
  return url.startsWith('#')
    || url.startsWith('//')
    || url.startsWith('/')
    || /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(url)
}

/** 拆分一个 Markdown URL，不规范化其 query 或 fragment 后缀。 */
export function splitMarkdownUrlTarget(url: string): { path: string; suffix: string } {
  const boundary = url.search(/[?#]/)
  if (boundary === -1) return { path: url, suffix: '' }
  return { path: url.slice(0, boundary), suffix: url.slice(boundary) }
}

function skipWhitespace(source: string, start: number): number {
  let index = start
  while (/\s/.test(source[index] ?? '')) index += 1
  return index
}

function labelEnd(source: string): number {
  const first = source.indexOf('[')
  if (first === -1) return -1
  let depth = 0
  for (let index = first; index < source.length; index += 1) {
    const char = source[index]
    if (char === '\\') index += 1
    else if (char === '[') depth += 1
    else if (char === ']') {
      depth -= 1
      if (depth === 0) return index
    }
  }
  return -1
}

function destinationRange(rawNode: string, type: MarkdownDestinationNode['type']): { start: number; end: number } {
  const endOfLabel = labelEnd(rawNode)
  if (endOfLabel === -1) throw new Error(`markdown: cannot locate label end in ${JSON.stringify(rawNode)}`)
  let start: number
  if (type === 'definition') {
    const colon = rawNode.indexOf(':', endOfLabel + 1)
    if (colon === -1) throw new Error(`markdown: cannot locate definition separator in ${JSON.stringify(rawNode)}`)
    start = skipWhitespace(rawNode, colon + 1)
  } else {
    if (rawNode[endOfLabel + 1] !== '(') {
      throw new Error(`markdown: cannot locate inline destination in ${JSON.stringify(rawNode)}`)
    }
    start = skipWhitespace(rawNode, endOfLabel + 2)
  }
  if (rawNode[start] === '<') {
    for (let index = start + 1; index < rawNode.length; index += 1) {
      if (rawNode[index] === '\\') index += 1
      else if (rawNode[index] === '>') return { start: start + 1, end: index }
    }
    throw new Error(`markdown: cannot locate angle-bracket destination end in ${JSON.stringify(rawNode)}`)
  }
  let depth = 0
  for (let index = start; index < rawNode.length; index += 1) {
    const char = rawNode[index]
    if (char === '\\') index += 1
    else if (char === '(') depth += 1
    else if (char === ')') {
      if (depth === 0) return { start, end: index }
      depth -= 1
    } else if (/\s/.test(char ?? '') && depth === 0) {
      return { start, end: index }
    }
  }
  return { start, end: rawNode.length }
}

/** 在原始 Markdown 中定位一个已解析目标，不做重新序列化。 */
export function markdownDestination(source: string, node: MarkdownDestinationNode): MarkdownDestination {
  const start = node.position?.start.offset
  const end = node.position?.end.offset
  if (start === undefined || end === undefined) {
    throw new Error(`markdown: destination ${JSON.stringify(node.url)} has no source offsets`)
  }
  const range = destinationRange(source.slice(start, end), node.type)
  const absolute = { start: start + range.start, end: start + range.end }
  return { ...absolute, url: source.slice(absolute.start, absolute.end) }
}

/**
 * 按文档顺序提取每个已解析代码块及其 info string。
 * @param source - 待扫描的 Markdown 源。
 * @returns 每个块的起始行、语言、info string 与块体。
 */
export function markdownFences(source: string): MarkdownFence[] {
  const lines = splitLines(source)
  const fences: MarkdownFence[] = []
  visitMarkdown(parseMarkdown(source), (node) => {
    if (node.type !== 'code' || node.position === undefined) return
    const lang = node.lang ?? null
    const meta = node.meta ?? ''
    const info = lang === null ? '' : meta === '' ? lang : `${lang} ${meta}`
    const endLine = lines[node.position.end.line - 1] ?? ''
    const closed = /^ {0,3}(`{3,}|~{3,})\s*$/.test(endLine)
    fences.push({ line: node.position.start.line, lang, info, code: node.value, closed })
  })
  return fences
}

/** 读者从单个 Markdown 节点看到的文本；原始 HTML 本身不贡献文本。 */
function renderedText(node: Nodes): string {
  if (node.type === 'text' || node.type === 'inlineCode') return node.value
  if (node.type === 'image' || node.type === 'imageReference') return node.alt ?? ''
  if (node.type === 'break') return ' '
  if ('children' in node) return node.children.map(child => renderedText(child)).join('')
  return ''
}

/**
 * 返回每个已解析 Markdown 标题及其渲染后文本与源行号。
 * @param source - 需要返回其标题的 Markdown 源。
 * @param tree - 当调用方已持有 `source` 的 {@link parseMarkdown} 结果时传入。
 * @returns 按文档顺序排列的标题。
 */
export function markdownHeadingLines(source: string, tree: Nodes = parseMarkdown(source)): MarkdownHeadingLine[] {
  const rawLines = splitLines(source)
  const headings: MarkdownHeadingLine[] = []
  visitMarkdown(tree, (node) => {
    if (node.type !== 'heading' || node.position === undefined) return
    headings.push({
      depth: node.depth,
      index: node.position.start.line,
      raw: rawLines[node.position.start.line - 1] ?? '',
      text: renderedText(node),
    })
  })
  return headings
}

type ColumnRange = readonly [start: number, end: number]
type OffsetRange = readonly [start: number, end: number]

/** 已解析 HTML 注释所占的源列区间，按源行号索引。 */
function htmlCommentRanges(tree: Nodes, rawLines: readonly string[]): Map<number, ColumnRange[]> {
  const comments: OffsetRange[] = []
  visitMarkdown(tree, (node) => {
    if (node.type !== 'html' || node.position?.start.offset === undefined) return
    let cursor = 0
    while (true) {
      const start = node.value.indexOf('<!--', cursor)
      if (start < 0) break
      const close = node.value.indexOf('-->', start + '<!--'.length)
      const end = close < 0 ? node.value.length : close + '-->'.length
      comments.push([node.position.start.offset + start, node.position.start.offset + end])
      cursor = end
    }
  })

  const ranges = new Map<number, ColumnRange[]>()
  let lineOffset = 0
  rawLines.forEach((raw, index) => {
    const lineEnd = lineOffset + raw.length
    for (const [start, end] of comments) {
      const from = Math.max(start, lineOffset)
      const to = Math.min(end, lineEnd)
      const coversEmptyLine = raw.length === 0 && start <= lineOffset && end > lineOffset
      if (from < to || coversEmptyLine) {
        const lineRanges = ranges.get(index + 1) ?? []
        lineRanges.push([from - lineOffset, to - lineOffset])
        ranges.set(index + 1, lineRanges)
      }
    }
    lineOffset = lineEnd + 1
  })
  return ranges
}

/** HTML 注释被移除后，源行是否仍保留非空白文本。 */
function hasRenderedTextOutsideComments(raw: string, ranges: readonly ColumnRange[] | undefined): boolean {
  if (ranges === undefined) return true
  let cursor = 0
  let visible = ''
  for (const [start, end] of [...ranges].sort((left, right) => left[0] - right[0])) {
    visible += raw.slice(cursor, start)
    cursor = Math.max(cursor, end)
  }
  visible += raw.slice(cursor)
  return visible.trim().length > 0
}

/**
 * 返回代码块和 HTML 注释之外的源行。
 * @param source - 其散文需原样保留的 Markdown 源。
 * @param tree - 当调用方已持有 `source` 的 {@link parseMarkdown} 结果时传入。
 * @returns 不在围栏内的行及其原始 1 基行号。
 */
export function markdownProseLines(source: string, tree: Nodes = parseMarkdown(source)): MarkdownProseLine[] {
  const rawLines = splitLines(source)
  const comments = htmlCommentRanges(tree, rawLines)
  const fenced = new Set<number>()
  visitMarkdown(tree, (node) => {
    if (node.type !== 'code' || node.position === undefined) return
    for (let line = node.position.start.line; line <= node.position.end.line; line += 1) fenced.add(line)
  })
  const kept: MarkdownProseLine[] = []
  rawLines.forEach((raw, i) => {
    if (fenced.has(i + 1)) return
    if (hasRenderedTextOutsideComments(raw, comments.get(i + 1))) {
      kept.push({ index: i + 1, raw })
    }
  })
  return kept
}
