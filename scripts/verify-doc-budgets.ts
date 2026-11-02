/**
 * 执行来自已配置预算清单的 `wc -w` 式上限，并强制清单覆盖配置声明的全部
 * 常驻文档。缺文件、非法上限与漏配的常驻文档都会失败；`--list` 报告当前
 * 用量。上限以至少 5% 余量向下棘轮；上调上限需要 `docs/AGENTS.md` 定义的
 * 正当理由。
 *
 * 覆盖由 `docSystem.budgetCoveragePatterns` 决定：匹配到的每一篇都必须在
 * 清单里有一条上限，因此新增常驻文档时漏配预算会让门禁变红，不依赖散文
 * 约定或人工记性。
 *
 * @module scripts/verify-doc-budgets
 */

import { existsSync, readFileSync } from 'node:fs'
import { relative, resolve } from 'node:path'
import { docSystem, repoRoot } from './doc-system.config.ts'
import { uniqueRepoFiles } from './repo-files.ts'

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

/** 把绝对路径转成清单使用的斜杠相对路径。 */
function toRepoPath(absolute: string): string {
  return relative(root, absolute).replaceAll('\\', '/')
}

/**
 * 覆盖比较与清单键都按大小写不敏感归一。磁盘上的实际大小写随检出平台与
 * 历史重命名而变（例如 `docs/` 在磁盘上是 `Docs/`），而清单键是配置里
 * 写定的形态；按字面比较会把同一篇文档判成缺失。
 */
function normalizePath(path: string): string {
  return path.toLowerCase()
}

const manifest = JSON.parse(readFileSync(MANIFEST_PATH, 'utf8')) as Record<string, number>

const listOnly = process.argv.includes('--list')
const failures: string[] = []
const rows: string[] = []

/** 配置声明必须配预算的常驻文档。 */
const covered = uniqueRepoFiles(root, docSystem.budgetCoveragePatterns).map(file => toRepoPath(file.abs))
const budgeted = new Set(Object.keys(manifest).map(normalizePath))

for (const path of covered) {
  if (!budgeted.has(normalizePath(path))) {
    failures.push(`${path}: standing doc has no budget entry — add one to ${docSystem.budgetManifest} in the same change`)
  }
}

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
    failures.push(`${path}: ${words} words exceeds the ${ceiling}-word ceiling — relocate or condense per docs/AGENTS.md (raising the ceiling requires justification in the change)`)
  }
}

if (listOnly) {
  console.log(rows.join('\n'))
  process.exit(0)
}

if (failures.length > 0) {
  console.error('verify-doc-budgets failed:\n')
  for (const failure of failures) console.error(`  ${failure}`)
  console.error(`\nSee docs/AGENTS.md for the documentation standard and the relocation-first rule. Manifest: ${relative(root, MANIFEST_PATH)}`)
  process.exit(1)
}

console.log(`verify-doc-budgets: ${Object.keys(manifest).length} budgeted docs within ceiling, covering ${covered.length} standing doc(s).`)
