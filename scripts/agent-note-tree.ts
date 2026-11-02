/**
 * Agent Note 树的结构唯一来源。生命周期与分类集合来自
 * `doc-system.config.ts`；导入本模块无副作用。
 *
 * @module scripts/agent-note-tree
 */

import { globSync, readdirSync } from 'node:fs'
import { resolve, sep } from 'node:path'
import { docSystem, repoRoot } from './doc-system.config.ts'

/** Agent Note 根目录的绝对路径。 */
export const agentNoteRoot = resolve(repoRoot, docSystem.agentNotes.root)

const {
  lifecycles: AGENT_NOTE_LIFECYCLES,
  classes: AGENT_NOTE_CLASSES,
  archived: AGENT_NOTE_ARCHIVE,
} = docSystem.agentNotes

export { AGENT_NOTE_ARCHIVE, AGENT_NOTE_CLASSES, AGENT_NOTE_LIFECYCLES }

/** 允许直接位于生命周期根目录的非 Agent Note Markdown。 */
const ROOT_ALLOWLIST = new Set(['AGENTS.md'])

/** 遍历器发现的一个 Agent Note 文件。 */
export interface AgentNote {
  lifecycle: string
  /** 相对 Agent Note 根目录的路径。 */
  rel: string
  /** 取自文件名的 `yyyy-mm-dd`。 */
  date: string
}

/**
 * 遍历 Agent Note 树并执行结构规则。返回全部有效 Agent Note，以及每条违规
 * 对应的一条错误字符串（未知生命周期或分类文件夹、深度不符、文件名不符）。
 * 调用方把非空错误列表视为致命。
 */
export function walkAgentNoteTree(): { notes: AgentNote[]; errors: string[] } {
  const notes: AgentNote[] = []
  const errors: string[] = []
  // 生命周期集合同样是封闭的：笔记根目录下任何未知生命周期的目录，其
  // Agent Note 会对遍历不可见。
  for (const entry of readdirSync(agentNoteRoot, { withFileTypes: true })) {
    if (entry.name === 'INDEX.md') {
      errors.push('structure: INDEX.md — centralized Agent Note indexes are forbidden; browse the lifecycle/class tree or search the repository')
      continue
    }
    if (entry.isDirectory()
      && entry.name !== AGENT_NOTE_ARCHIVE
      && !AGENT_NOTE_LIFECYCLES.includes(entry.name)) {
      errors.push(`structure: ${entry.name}/ — unknown lifecycle folder (allowed: ${AGENT_NOTE_LIFECYCLES.join(', ')}, plus ${AGENT_NOTE_ARCHIVE}/)`)
    }
  }
  for (const lifecycle of AGENT_NOTE_LIFECYCLES) {
    for (const match of globSync(`${lifecycle}/**/*.md`, { cwd: agentNoteRoot }).map(path => path.split(sep).join('/')).sort()) {
      const segs = match.split('/')
      // 白名单文件直接位于生命周期根目录（如 implemented/AGENTS.md）。
      if (segs.length === 2 && ROOT_ALLOWLIST.has(segs[1] ?? '')) continue
      // 中文对侧是同一个 Agent Note，通过其英文文件名索引；一致性由配对
      // 门禁负责。
      if (docSystem.bilingual && match.endsWith('.zh.md')) continue
      const cls = segs[1]
      const base = segs[2]
      if (segs.length !== 3 || cls === undefined || base === undefined) {
        errors.push(`structure: ${match} — expected {lifecycle}/{class}/file.md (got depth ${segs.length})`)
        continue
      }
      if (!AGENT_NOTE_CLASSES.includes(cls)) {
        errors.push(`structure: ${match} — unknown class folder "${cls}" (allowed: ${AGENT_NOTE_CLASSES.join(', ')})`)
        continue
      }
      if (!/^\d{4}-\d{2}-\d{2}-.+\.md$/.test(base)) {
        errors.push(`structure: ${match} — filename must be yyyy-mm-dd-topic.md`)
        continue
      }
      notes.push({ lifecycle, rel: match, date: base.slice(0, 10) })
    }
  }
  return { notes, errors }
}
