/**
 * 执行 Agent Note 的生命周期/分类路径与带日期文件名规则。结构规则与
 * `agent-note-tree.ts` 共享；封闭的分类规则见 `.agents/notes/README.md`。
 */

import { existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { walkAgentNoteTree } from './agent-note-tree.ts'
import { docSystem, repoRoot } from './doc-system.config.ts'

const { notes, errors } = walkAgentNoteTree()

// 让旧位置保持不可用，使新笔记无法静默脱离此树。
for (const legacyRoot of ['docs/rfc', 'docs/rfcs']) {
  if (existsSync(resolve(repoRoot, legacyRoot))) {
    errors.push(`legacy-path: ${legacyRoot}/ is forbidden — put Agent Notes under ${docSystem.agentNotes.root}/`)
  }
}

if (errors.length === 0) {
  console.log(`verify-agent-note-classification: ${notes.length} Agent Note(s) checked, structure consistent.`)
  process.exit(0)
}

console.error('verify-agent-note-classification: violations found:')
for (const e of errors) console.error(`  ${e}`)
process.exit(1)
