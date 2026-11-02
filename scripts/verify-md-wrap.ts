/**
 * 拒绝跨多个物理行的 Markdown 散文段落。GFM AST 能把段落——包括列表与
 * 引用块内的段落——与多行结构节点区分开。检查器从不改写；符号链接形式的
 * 指令文件会被去重。VitePress frontmatter 与自定义容器定界符在解析前被
 * 掩码。约定归属见 `docs/AGENTS.md`。
 */

import { readFileSync } from 'node:fs'
import { relative } from 'node:path'
import type { Nodes } from 'mdast'
import { parseMarkdown, visitMarkdown } from './markdown.ts'
import { isArchivedAgentNotePath, splitLines, uniqueRepoFiles } from './repo-files.ts'
import { docSystem, repoRoot } from './doc-system.config.ts'

const root = repoRoot

/** 一处已定位的硬折行：跨多个源行的散文段落。 */
interface Violation {
  file: string
  /** 硬折行段落起始处的 1 基行号。 */
  line: number
  text: string
}

function maskVitePressStructure(source: string): string {
  const lines = splitLines(source)
  if (lines[0] === '---') {
    const closing = lines.indexOf('---', 1)
    if (closing !== -1) {
      for (let index = 0; index <= closing; index++) lines[index] = ''
    }
  }
  return lines.map(line => line.trimStart().startsWith(':::') ? '' : line).join('\n')
}

/** 经由 AST 找出单个 Markdown 文件中每个硬折行的散文段落。 */
function findViolations(absPath: string): Violation[] {
  const file = relative(root, absPath)
  const source = readFileSync(absPath, 'utf8')
  const parsedSource = maskVitePressStructure(source)
  const tree = parseMarkdown(parsedSource)
  const out: Violation[] = []

  visitMarkdown(tree, (node: Nodes): boolean | void => {
    if (node.type === 'paragraph' && node.position) {
      const { start, end } = node.position
      if (end.line > start.line) {
        const firstLine = splitLines(source)[start.line - 1] ?? ''
        out.push({ file, line: start.line, text: firstLine.trim() })
      }
      // 段落的子节点皆为行内节点，因此不会再嵌套段落。
      return false
    }
  })
  return out
}

const files = uniqueRepoFiles(root, docSystem.markdownPatterns, isArchivedAgentNotePath)
const all = files.flatMap(file => findViolations(file.abs))
const checked = files.length

if (all.length === 0) {
  console.log(`verify-md-wrap: ${checked} file(s) checked, no hard-wrapped prose paragraphs.`)
  process.exit(0)
}

console.error('verify-md-wrap: hard-wrapped prose paragraphs found (write one physical line per paragraph):')
for (const v of all) {
  console.error(`  ${v.file}:${v.line}  ${v.text.slice(0, 80)}${v.text.length > 80 ? '…' : ''}`)
}
process.exit(1)
