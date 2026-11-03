/**
 * 门禁失败时的统一提示：指向该门禁的规则正家。
 *
 * 报错只说哪条不合规，不说该按什么规则改；照着报错改会绕开规则。
 * 规则本身只存在于各文档里，这里只给路径，不复述规则内容。
 *
 * @module scripts/gate-hint
 */

import { resolve } from 'node:path'
import { repoRoot } from './doc-system.config.ts'

/**
 * 各门禁的规则正家：门禁的 npm 脚本名到承载其规则的 Markdown 文件。
 *
 * 新增门禁时在这里登记；未登记的门禁走通用提示。
 */
export const GATE_RULES: Readonly<Record<string, string>> = {
  'verify-md-wrap': 'Docs/AGENTS.md',
  'verify-md-links': 'Docs/AGENTS.md',
  'verify-doc-budgets': 'Docs/AGENTS.md',
  'verify-agent-note-classification': '.agents/notes/README.md',
  'verify-agent-note-format': '.agents/notes/README.md',
  'verify-archived-agent-notes': '.agents/notes/README.md',
  'verify-skill-invocation-metadata': 'Docs/AGENTS.md',
}

/**
 * 拼出门禁失败时打印的提示行。
 *
 * @param gate - 门禁的 npm 脚本名。
 * @returns 待打印的多行文本；规则内容不在此处，只指向承载它的文件。
 */
export function gateHint(gate: string): string {
  const doc = GATE_RULES[gate]
  if (doc === undefined) {
    return [
      '',
      '请先阅读对应门禁管理的文件夹根 AGENTS.md 中的规则，再修改。',
    ].join('\n')
  }
  return [
    '',
    '请先阅读对应规则再修改：',
    `  ${doc}`,
    `  ${resolve(repoRoot, doc)}`,
  ].join('\n')
}
