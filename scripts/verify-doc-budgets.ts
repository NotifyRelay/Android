/**
 * 执行来自已配置预算清单的 `wc -w` 式上限。缺文件与非法上限都会失败；
 * `--list` 报告当前用量。只有清单列出的常驻文档受预算约束。上限以至少
 * 5% 余量向下棘轮；上调上限需要 `Docs/AGENTS.md` 定义的正当理由。
 *
 * @module scripts/verify-doc-budgets
 */

import { existsSync, readFileSync } from 'node:fs'
import { relative, resolve } from 'node:path'
import { docSystem, repoRoot } from './doc-system.config.ts'

const root = repoRoot

const MANIFEST_PATH = resolve(root, docSystem.budgetManifest)

/**
 * 统计预算词数：每个以空白分隔的拉丁 token 计一单位，每个 CJK 字符计一
 * 单位。中文没有词间空格，纯空白切分会把中文页面读成几十个词，预算便不
 * 会生效。
 * @param text - 完整 Markdown 源。
 * @returns 预算词数。
 */
function countWords(text: string): number {
  const cjk = text.match(/[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\u3040-\u30ff]/gu)?.length ?? 0
  const latin = text.replace(/[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\u3040-\u30ff]/gu, ' ')
    .split(/\s+/)
    .filter(Boolean)
    .length
  return cjk + latin
}

const manifest = JSON.parse(readFileSync(MANIFEST_PATH, 'utf8')) as Record<string, number>

const listOnly = process.argv.includes('--list')
const failures: string[] = []
const rows: string[] = []

for (const [path, ceiling] of Object.entries(manifest)) {
  if (!Number.isInteger(ceiling) || ceiling <= 0) {
    rows.push(`BAD   ${'—'.padStart(6)} / ${String(ceiling).padEnd(6)} ${path}`)
    failures.push(`${path}: ceiling must be a positive integer, got ${ceiling}`)
    continue
  }
  const abs = resolve(root, path)
  if (!existsSync(abs)) {
    rows.push(`MISS  ${'—'.padStart(6)} / ${String(ceiling).padEnd(6)} ${path}`)
    failures.push(`${path}: budgeted file does not exist (renamed or deleted? update the configured budget manifest in the same change)`)
    continue
  }
  const words = countWords(readFileSync(abs, 'utf8'))
  rows.push(`${words <= ceiling ? 'ok  ' : 'OVER'}  ${String(words).padStart(6)} / ${String(ceiling).padEnd(6)} ${path}`)
  if (words > ceiling) {
    failures.push(`${path}: ${words} words exceeds the ${ceiling}-word ceiling — relocate or condense per Docs/AGENTS.md (raising the ceiling requires justification in the change)`)
  }
}

if (listOnly) {
  console.log(rows.join('\n'))
  process.exit(0)
}

if (failures.length > 0) {
  console.error('verify-doc-budgets failed:\n')
  for (const failure of failures) console.error(`  ${failure}`)
  console.error(`\nSee Docs/AGENTS.md for the documentation standard and the relocation-first rule. Manifest: ${relative(root, MANIFEST_PATH)}`)
  process.exit(1)
}

console.log(`verify-doc-budgets: ${Object.keys(manifest).length} budgeted docs within ceiling.`)
