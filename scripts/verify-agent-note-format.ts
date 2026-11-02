/**
 * 执行 Agent Note 的头部、按生命周期的章节、备选方案与已退役标记规则。
 * 分类与文件名归同级的树门禁；翻译结构归配对门禁。确切的格式与豁免规则
 * 见 `.agents/notes/README.md`。
 */

import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { agentNoteRoot, walkAgentNoteTree } from './agent-note-tree.ts'
import { splitLines } from './repo-files.ts'

/** 这些格式规则的生效日期；豁免注释仅在该日期之前有效。 */
const FORMAT_ADOPTED = '2026-07-05'

/** 格式规则生效前的 Agent Note 用以替代 `## Alternatives considered` 的确切注释。 */
const GRANDFATHER = '<!-- agent-note-format: alternatives-not-recorded (pre-format Agent Note) -->'

/** 曾标记旧格式正文的已退役债务标记；禁止出现，以免回流。 */
const LEGACY_MARKERS = ['XXX: legacy ADR/RFC body format', 'XXX: legacy ADR/Agent Note body format']

/** 每个生命周期文件夹的状态行语法。 */
const STATUS: Record<string, RegExp> = {
  proposed: /^Status: proposed$/,
  implemented: /^Status: implemented$/,
  rejected: /^Status: rejected — .+$/,
}

/** 每个生命周期必需的 `##` 标题，超出通行的 `## Problem` 开头。 */
const REQUIRED: Record<string, string[]> = {
  proposed: ['## Proposal', '## Acceptance criteria', '## Risks'],
  implemented: ['## Decision', '## Consequences'],
  rejected: ['## Proposal'],
}

/** `implemented/` 中禁止的标题——按 slop 检查清单，提案期的规格式表述。 */
const BANNED_IMPLEMENTED = /^## (?:Proposal\b|Plan\b|Migration plan\b|Acceptance criteria\b)/i

const { notes, errors } = walkAgentNoteTree()

for (const note of notes) {
  const fail = (msg: string): void => {
    errors.push(`format: ${note.rel} — ${msg}`)
  }
  const lines = splitLines(readFileSync(resolve(agentNoteRoot, note.rel), 'utf8'))
  // 围栏示例中的格式 token 不构成文档结构。
  let inFence = false
  const prose = lines.filter((l) => {
    if (l.startsWith('```')) {
      inFence = !inFence
      return false
    }
    return !inFence
  })

  if (!/^# Agent Note: \S/.test(lines[0] ?? '')) fail('line 1 must be `# Agent Note: <title>`')
  if (lines[1] !== '') fail('line 2 must be blank')
  const status = STATUS[note.lifecycle]
  if (status !== undefined && !status.test(lines[2] ?? '')) {
    fail(`line 3 must match the ${note.lifecycle} status grammar (${String(status)})`)
  }
  if (lines[3] !== '') fail('line 4 must be blank')
  const statusLines = prose.filter(l => l.startsWith('Status:') && l !== lines[2])
  if (statusLines.length > 0 || prose.filter(l => l === lines[2]).length > 1) {
    fail('the line-3 `Status:` line must be the only one in the file')
  }

  const h2s = prose.filter(l => l.startsWith('## ')).map(l => l.trimEnd())
  if (h2s[0] !== '## Problem') fail(`the first section must be \`## Problem\` (got ${JSON.stringify(h2s[0] ?? '<none>')})`)
  for (const required of REQUIRED[note.lifecycle] ?? []) {
    if (!h2s.includes(required)) fail(`missing the required \`${required}\` section`)
  }
  if (note.lifecycle === 'implemented') {
    for (const h2 of h2s.filter(h => BANNED_IMPLEMENTED.test(h))) {
      fail(`\`${h2}\` is a proposal-era heading; an implemented Agent Note states what is (fold it into Decision/Consequences/Testing)`)
    }
  }

  const hasSection = h2s.includes('## Alternatives considered')
  const hasGrandfather = prose.includes(GRANDFATHER)
  if (hasSection && hasGrandfather) fail('carries both `## Alternatives considered` and the grandfather comment — drop the comment')
  if (!hasSection && !hasGrandfather) fail('missing `## Alternatives considered` (a pre-format Agent Note whose alternatives are not reconstructible carries the grandfather comment instead — see .agents/notes/README.md § The file format)')
  if (hasGrandfather && note.date >= FORMAT_ADOPTED) fail(`the grandfather comment is only valid for Agent Notes dated before ${FORMAT_ADOPTED}`)

  if (prose.some(line => LEGACY_MARKERS.some(marker => line.includes(marker)))) fail('carries the retired legacy-format debt marker')
}

if (errors.length === 0) {
  console.log(`verify-agent-note-format: ${notes.length} Agent Note(s) checked, all conform to .agents/notes/README.md § The file format.`)
  process.exit(0)
}

console.error('verify-agent-note-format: violations found:')
for (const e of errors) console.error(`  ${e}`)
process.exit(1)
