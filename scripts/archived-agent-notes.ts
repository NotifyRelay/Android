/**
 * 纯函数式的归档格式与不可变清单辅助。归档树封印冻结的历史笔记；本模块
 * 拥有哈希与布局规则，而 `verify-archived-agent-notes.ts` 拥有文件系统
 * 遍历与 Git 基线。
 *
 * @module scripts/archived-agent-notes
 */

import { createHash } from 'node:crypto'
import { basename } from 'node:path'
import { AGENT_NOTE_CLASSES } from './agent-note-tree.ts'
import { splitLines } from './repo-files.ts'

/** 冻结内容清单中的版本化字段。 */
export interface ArchiveManifest {
  version: 1
  files: Readonly<Record<string, string>>
}

/** 以与仓库 Git 对象格式无关的方式哈希一个归档产物。 */
function archiveContentHash(content: Buffer): string {
  return `sha256:${createHash('sha256').update(content).digest('hex')}`
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** 解析归档清单，并拒绝其封闭 schema 之外的字段或哈希。 */
export function parseArchiveManifest(content: string): ArchiveManifest {
  const value: unknown = JSON.parse(content)
  if (!isRecord(value)) throw new Error('expected a JSON object')
  const fields = Object.keys(value).sort()
  if (fields.join(',') !== 'files,version') throw new Error('expected exactly the fields `version` and `files`')
  if (value.version !== 1) throw new Error('unsupported manifest version (expected 1)')
  if (!isRecord(value.files)) throw new Error('`files` must be an object')
  const files: Record<string, string> = {}
  for (const [path, hash] of Object.entries(value.files)) {
    if (typeof hash !== 'string' || !/^sha256:[0-9a-f]{64}$/.test(hash)) {
      throw new Error(`invalid content hash for ${path}`)
    }
    files[path] = hash
  }
  return { version: 1, files }
}

/** 以确定的路径顺序渲染归档清单。 */
export function renderArchiveManifest(files: Readonly<Record<string, string>>): string {
  return `${JSON.stringify({
    version: 1,
    files: Object.fromEntries(Object.entries(files).sort(([left], [right]) => left.localeCompare(right))),
  }, null, 2)}\n`
}

/**
 * 拒绝改动已封印的产物。归档只追加：已存在的路径不得获得新哈希。
 * @param baseline - 从可信的变更前提交读取的清单。
 * @param current - 从工作树计算出的清单。
 * @returns 每条缺失或哈希被更改的封印条目对应一条诊断。
 */
export function validateArchiveManifestExtension(
  baseline: ArchiveManifest,
  current: ArchiveManifest,
): string[] {
  const errors: string[] = []
  for (const [path, expected] of Object.entries(baseline.files)) {
    const actual = current.files[path]
    if (actual === undefined) errors.push(`${path}: sealed manifest entry is missing`)
    else if (actual !== expected) errors.push(`${path}: sealed manifest hash changed`)
  }
  return errors
}

function validDate(value: string): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (match === null) return false
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(Date.UTC(year, month - 1, day))
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
}

function validateMetadata(path: string, content: Buffer, sourceBase: string): { errors: string[]; date: string | undefined } {
  const errors: string[] = []
  const lines = splitLines(content.toString('utf8'))
  const status = lines.findIndex(line => line.startsWith('Status:'))
  if (lines[status] !== 'Status: implemented') errors.push(`${path}: requires \`Status: implemented\``)
  const archived = /^Archived: (\d{4}-\d{2}-\d{2})$/.exec(lines[status + 1] ?? '')?.[1]
  if (status < 0 || archived === undefined || !validDate(archived)) {
    errors.push(`${path}: requires \`Archived: YYYY-MM-DD\` with a valid date immediately after the status`)
  } else if (archived < sourceBase.slice(0, 10)) {
    errors.push(`${path}: archive date ${archived} predates the note filename`)
  }
  return { errors, date: archived }
}

/**
 * 校验封闭 kind 树与归档笔记元数据。每条笔记封印一个 `.md`。
 * @param artifacts - 以相对归档根的路径为键的归档文件。
 * @returns 每条结构或元数据违规对应一条诊断。
 */
export function validateArchiveArtifacts(artifacts: ReadonlyMap<string, Buffer>): string[] {
  const errors: string[] = []
  const notes = new Map<string, Buffer>()
  for (const [path, content] of artifacts) {
    const match = /^([^/]+)\/(\d{4}-\d{2}-\d{2}-.+?)\.md$/.exec(path)
    if (match?.[1] === undefined || match[2] === undefined) {
      errors.push(`${path}: expected {kind}/yyyy-mm-dd-topic.md`)
      continue
    }
    if (!(AGENT_NOTE_CLASSES as readonly string[]).includes(match[1])) {
      errors.push(`${path}: unknown Agent Note kind ${JSON.stringify(match[1])}`)
      continue
    }
    notes.set(`${match[1]}/${match[2]}`, content)
  }

  for (const [key, content] of [...notes].sort(([left], [right]) => left.localeCompare(right))) {
    errors.push(...validateMetadata(`${key}.md`, content, basename(key)).errors)
  }
  return errors
}

/** 保留每条已封印的路径与哈希，并为新归档的产物追加哈希。 */
export function extendArchiveManifest(
  existing: ArchiveManifest,
  artifacts: ReadonlyMap<string, Buffer>,
): { files: Record<string, string>; added: string[]; errors: string[] } {
  const errors: string[] = []
  const files: Record<string, string> = { ...existing.files }
  for (const [path, expected] of Object.entries(existing.files)) {
    const content = artifacts.get(path)
    if (content === undefined) errors.push(`${path}: sealed artifact is missing`)
    else if (archiveContentHash(content) !== expected) errors.push(`${path}: sealed content hash changed`)
  }
  const added: string[] = []
  for (const [path, content] of [...artifacts].sort(([left], [right]) => left.localeCompare(right))) {
    if (files[path] !== undefined) continue
    files[path] = archiveContentHash(content)
    added.push(path)
  }
  return { files, added, errors }
}
