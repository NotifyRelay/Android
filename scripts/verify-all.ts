/**
 * 按依赖顺序运行全部门禁并报告各项结果。
 *
 * 门禁彼此独立，单个失败不得掩盖其余失败：每个门禁都会运行，只要有失败，
 * 进程就以非零状态退出。门禁之间没有共享状态，因此并发执行；写仓库的门禁除外。
 *
 * @module scripts/verify-all
 */

import { spawn } from 'node:child_process'
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

/** 一次门禁运行的结果。 */
interface GateResult {
  /** 该门禁的 npm 脚本名。 */
  readonly script: string
  /** 该门禁的职责。 */
  readonly purpose: string
  /** 退出码为 0 时为 true。 */
  readonly passed: boolean
  /** 标准输出与标准错误合并后的内容。 */
  readonly output: string
}

/**
 * 运行单个门禁。
 *
 * 通过本地 tsx 加载器直接运行各门禁模块。走 `npm run` 在 Windows 上需要
 * shell，并会掩盖子进程的退出状态。
 *
 * @param gate - 待运行的门禁。
 * @returns 退出码与合并后的输出。
 */
function runGate(gate: Gate): Promise<GateResult> {
  return new Promise((resolveGate) => {
    const child = spawn(process.execPath, [
      resolve(repoRoot, 'node_modules/tsx/dist/cli.mjs'),
      resolve(repoRoot, 'scripts', `${gate.script}.ts`),
    ], {
      cwd: repoRoot,
      env: { ...process.env, ...gate.env },
    })
    let output = ''
    child.stdout.on('data', (chunk: Buffer) => { output += chunk.toString('utf8') })
    child.stderr.on('data', (chunk: Buffer) => { output += chunk.toString('utf8') })
    child.on('error', (error: Error) => {
      resolveGate({ script: gate.script, purpose: gate.purpose, passed: false, output: output + error.message })
    })
    child.on('close', (code) => {
      resolveGate({ script: gate.script, purpose: gate.purpose, passed: code === 0, output })
    })
  })
}

// 并发启动全部门禁，再按声明顺序逐条报告：输出顺序因此与并发无关，失败信息不交错。
const results = await Promise.all(gates.map((gate) => runGate(gate)))
let failed = 0
for (const result of results) {
  if (!result.passed) failed += 1
  console.log(`${result.passed ? 'PASS' : 'FAIL'}  ${result.script.padEnd(36)} ${result.purpose}`)
  const output = result.output.trim()
  if (!result.passed && output !== '') {
    for (const line of output.split('\n')) console.log(`      ${line}`)
  }
}

if (failed > 0) {
  console.error(`\nverify-all: ${failed} of ${gates.length} gate(s) failed.`)
  process.exit(1)
}
console.log(`\nverify-all: ${gates.length} gate(s) passed.`)
