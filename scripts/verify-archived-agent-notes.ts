/**
 * 校验并追加封印冻结的 Agent Note 归档。归档笔记是不可变历史：本门禁证明
 * 每条已封印哈希仍然匹配，仅经 `--write` 接纳新产物，且从不编辑已封印
 * 文件。
 *
 * @module scripts/verify-archived-agent-notes
 */

import { spawnSync } from 'node:child_process'
import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { AGENT_NOTE_CLASSES, AGENT_NOTE_ARCHIVE, agentNoteRoot } from './agent-note-tree.ts'
import { docSystem, repoRoot as projectRoot } from './doc-system.config.ts'
import {
  extendArchiveManifest,
  parseArchiveManifest,
  renderArchiveManifest,
  validateArchiveArtifacts,
  validateArchiveManifestExtension,
  type ArchiveManifest,
} from './archived-agent-notes.ts'

const args = process.argv.slice(2)
const writeMode = args.length === 1 && args[0] === '--write'
if (args.length > 0 && !writeMode) {
  console.error('verify-archived-agent-notes: usage: tsx scripts/verify-archived-agent-notes.ts [--write]')
  process.exit(1)
}

const archiveRoot = resolve(agentNoteRoot, AGENT_NOTE_ARCHIVE)
const archiveRel = `${docSystem.agentNotes.root}/${AGENT_NOTE_ARCHIVE}`
const manifestPath = resolve(archiveRoot, 'manifest.json')
const manifestRepoPath = `${archiveRel}/manifest.json`
const errors: string[] = []
const allowedRootFiles = new Set(['AGENTS.md', 'manifest.json'])
const kinds = new Set<string>()

if (!existsSync(resolve(archiveRoot, 'AGENTS.md'))) errors.push(`${archiveRel}/AGENTS.md is required`)
const artifacts = new Map<string, Buffer>()
for (const entry of readdirSync(archiveRoot, { withFileTypes: true })) {
  if (entry.isFile()) {
    if (!allowedRootFiles.has(entry.name)) errors.push(`${archiveRel}/${entry.name}: unexpected root file`)
    continue
  }
  if (!entry.isDirectory()) {
    errors.push(`${archiveRel}/${entry.name}: only regular files and kind directories are allowed`)
    continue
  }
  if (!AGENT_NOTE_CLASSES.includes(entry.name)) {
    errors.push(`${archiveRel}/${entry.name}/: unknown Agent Note kind`)
    continue
  }
  kinds.add(entry.name)
  for (const child of readdirSync(resolve(archiveRoot, entry.name), { withFileTypes: true })) {
    const rel = `${entry.name}/${child.name}`
    if (!child.isFile()) {
      errors.push(`${archiveRel}/${rel}: archived kind directories contain regular files only`)
      continue
    }
    // 版本控制占位文件让空的 kind 目录留在树中；它们不是归档产物，永不封印。
    if (!child.name.endsWith('.md') && !child.name.endsWith('.i18n.yaml')) continue
    artifacts.set(rel, readFileSync(resolve(archiveRoot, rel)))
  }
}
for (const kind of AGENT_NOTE_CLASSES) {
  if (!kinds.has(kind)) errors.push(`${archiveRel}/${kind}/: required kind directory is missing`)
}
errors.push(...validateArchiveArtifacts(artifacts))

function runGit(gitArgs: string[]): string {
  const result = spawnSync('git', gitArgs, { cwd: projectRoot, encoding: 'utf8' })
  if (result.error !== undefined) throw result.error
  if (result.status !== 0) throw new Error(result.stderr.trim() || `git exited with status ${result.status}`)
  return result.stdout
}

function readBaselineManifest(ref: string): ArchiveManifest {
  runGit(['cat-file', '-e', `${ref}^{commit}`])
  const manifestEntry = runGit(['ls-tree', '--name-only', ref, '--', manifestRepoPath]).trim()
  if (manifestEntry === '') return { version: 1, files: {} }
  return parseArchiveManifest(runGit(['show', `${ref}:${manifestRepoPath}`]))
}

let manifest: ArchiveManifest = { version: 1, files: {} }
if (existsSync(manifestPath)) {
  try {
    manifest = parseArchiveManifest(readFileSync(manifestPath, 'utf8'))
  } catch (error: unknown) {
    errors.push(`${archiveRel}/manifest.json: ${error instanceof Error ? error.message : String(error)}`)
  }
} else if (!writeMode) {
  errors.push(`${archiveRel}/manifest.json is required; seal new artifacts with \`verify-archived-agent-notes --write\``)
}

// CI 提供其可信的变更前提交；本地写入以已提交的 HEAD 为基线。
const baselineRef = process.env.NOTIFYRELAY_ARCHIVE_BASE_REF ?? 'HEAD'
try {
  const baseline = readBaselineManifest(baselineRef)
  errors.push(...validateArchiveManifestExtension(baseline, manifest))
} catch (error: unknown) {
  errors.push(`${archiveRel}/manifest.json: cannot read baseline ${JSON.stringify(baselineRef)}: ${error instanceof Error ? error.message : String(error)}`)
}

const extended = extendArchiveManifest(manifest, artifacts)
errors.push(...extended.errors)
if (!writeMode) {
  for (const path of extended.added) errors.push(`${path}: archived artifact is not sealed in manifest.json`)
}

if (errors.length > 0) {
  console.error('verify-archived-agent-notes: archive rules violated:')
  for (const error of errors) console.error(`  ${error}`)
  process.exit(1)
}

if (writeMode) {
  const rendered = renderArchiveManifest(extended.files)
  if (!existsSync(manifestPath) || readFileSync(manifestPath, 'utf8') !== rendered) {
    writeFileSync(manifestPath, rendered)
  }
  console.log(`verify-archived-agent-notes: sealed ${extended.added.length} new artifact(s); existing seals unchanged.`)
} else {
  console.log(`verify-archived-agent-notes: ${artifacts.size} frozen artifact(s) checked across ${kinds.size} kind(s).`)
}
