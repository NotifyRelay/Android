/**
 * 按依赖顺序运行全部门禁并报告各项结果。
 *
 * 门禁彼此独立，单个失败不得掩盖其余失败：每个门禁都会运行，只要有失败，
 * 进程就以非零状态退出。
 *
 * @module scripts/verify-all
 */

import { spawnSync } from 'node:child_process'
import { resolve } from 'node:path'
import { repoRoot } from './doc-system.config.ts'

/** 一个门禁：其 npm 脚本名与该门禁的职责。 */
interface Gate {
  script: string
  purpose: string
  /** 仅对本门禁生效的环境变量。 */
  env?: Record<string, string>
}

const gates: Gate[] = [
  { script: 'verify-md-wrap', purpose: 'one physical line per paragraph' },
  { script: 'verify-md-links', purpose: 'relative links and fragments resolve' },
  { script: 'verify-doc-budgets', purpose: 'standing docs stay within their word ceilings' },
  { script: 'verify-agent-note-classification', purpose: 'Agent Note lifecycle/class paths and filenames' },
  { script: 'verify-agent-note-format', purpose: 'Agent Note headers, sections, and alternatives' },
  { script: 'verify-archived-agent-notes', purpose: 'sealed archive is frozen and append-only' },
  { script: 'verify-skill-invocation-metadata', purpose: 'cross-product skill invocation policy' },
]

let failed = 0

for (const gate of gates) {
  // 通过本地 tsx 加载器直接运行各门禁模块。走 `npm run` 在 Windows 上需要
  // shell，并会掩盖子进程的退出状态。
  const result = spawnSync(process.execPath, [
    resolve(repoRoot, 'node_modules/tsx/dist/cli.mjs'),
    resolve(repoRoot, 'scripts', `${gate.script}.ts`),
  ], {
    cwd: repoRoot,
    encoding: 'utf8',
    env: { ...process.env, ...gate.env },
  })
  const passed = result.status === 0
  if (!passed) failed += 1
  console.log(`${passed ? 'PASS' : 'FAIL'}  ${gate.script.padEnd(36)} ${gate.purpose}`)
  const output = `${result.stdout ?? ''}${result.stderr ?? ''}`.trim()
  if (!passed && output !== '') {
    for (const line of output.split('\n')) console.log(`      ${line}`)
  }
}

if (failed > 0) {
  console.error(`\nverify-all: ${failed} of ${gates.length} gate(s) failed.`)
  process.exit(1)
}
console.log(`\nverify-all: ${gates.length} gate(s) passed.`)
