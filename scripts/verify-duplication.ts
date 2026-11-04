/**
 * 重复代码门禁：用 jscpd 扫描仓库的 Kotlin、Rust 与 TypeScript 源码，出现基线之外的
 * 克隆即失败。
 *
 * 语料由 git 给出，不由 glob 拼出：`git ls-files` 逐级应用各级 `.gitignore`，构建产物、
 * `node_modules` 与本地配置因此永不进入扫描，新增规则也只需改 `.gitignore`。已跟踪与未
 * 跟踪但未被忽略的文件都在语料内，未提交的新源码因此与提交后一样受检。
 *
 * 子模块内容按 `--recurse-submodules` 列出，各自应用所属仓库的 `.gitignore`；子模块里
 * 未跟踪的文件不在语料内。
 *
 * 基线记的是当前已接受的重复（文件对 + 片段长度 + 片段内容摘要）及其出现次数，只允许
 * 调低；确实无法消除的（如跨构建目标的类型镜像）才调高，并在提交信息里说明理由。
 *
 * 同一指纹在基线与当前各出现 N 次时视为未新增：jscpd 会把一处重复按相邻窗口合并成多条
 * 克隆，只比对实例存在与否会让基线随首处重复的条数变化而失效。
 *
 * 用法：tsx scripts/verify-duplication.ts [--update]
 *   --update  按当前扫描结果重写基线。
 *
 * @module scripts/verify-duplication
 */

import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { relative, resolve } from 'node:path'
import { detectClones } from 'jscpd'
import { repoRoot } from './doc-system.config.ts'
import { gateHint } from './gate-hint.ts'

const root = repoRoot
const baselinePath = resolve(root, 'duplication-baseline.json')

/** 扫描参数；与基线一致，改动会让整份基线失效。 */
const MIN_LINES = 5
const MIN_TOKENS = 50

/** 语料扩展名。 */
const SOURCE_PATTERNS = ['*.kt', '*.kts', '*.rs', '*.ts']

/** jscpd 分词格式，覆盖 SOURCE_PATTERNS 的全部扩展名。 */
const FORMATS = ['kotlin', 'rust', 'typescript']

/** 失败时逐条列出的指纹上限；全量数百条会把真正的第一条挤出视野。 */
const DETAIL_LIMIT = 20

/**
 * 列出待扫描的源文件。
 *
 * jscpd 对 Windows 反斜杠绝对路径会静默扫不到文件（返回空结果），因此路径统一成正斜杠
 * 形式。
 *
 * @returns 绝对路径数组。
 */
function sourceFiles(): string[] {
  const list = (extra: readonly string[]): string[] =>
    execFileSync('git', ['ls-files', '-z', ...extra, '--', ...SOURCE_PATTERNS], { cwd: root, encoding: 'utf8' })
      .split('\0')
      .filter(Boolean)
  return [...list(['--cached', '--recurse-submodules']), ...list(['--others', '--exclude-standard'])]
    .map(path => resolve(root, path).replaceAll('\\', '/'))
}

/**
 * 扫描语料中的克隆。
 *
 * @returns 克隆列表。
 */
function scanClones() {
  return detectClones({
    path: sourceFiles(),
    minLines: MIN_LINES,
    minTokens: MIN_TOKENS,
    format: FORMATS,
    absolute: true,
    silent: true,
    reporters: [],
  })
}

/**
 * 计算片段内容摘要。
 *
 * 先归一化行尾再摘要：同一份片段在检出为 CRLF 与 LF 的机器上文本不同，不归一化会让基线
 * 只在某一侧通过。摘要取两侧片段中字节序较小的一侧，使指纹与 A/B 侧归属无关（两侧片段
 * 通常不同）。
 *
 * @param a - 第一侧片段文本。
 * @param b - 第二侧片段文本。
 * @returns 摘要的前 16 位十六进制。
 */
function fragmentDigest(a: string, b: string): string {
  const normalize = (text: string) => text.replace(/\r\n?/g, '\n')
  const first = normalize(a)
  const second = normalize(b)
  const [head, tail] = first <= second ? [first, second] : [second, first]
  return createHash('sha256').update(`${head}\u0000${tail}`).digest('hex').slice(0, 16)
}

/**
 * 把克隆折成指纹。
 *
 * 指纹不含行号：行号随无关改动漂移。文件对 + 片段行数 + 片段内容摘要共同定位一处重复，
 * 因此同文件对、同长度但片段内容被替换时视为新的重复。
 *
 * 行数取自片段文本而非定位：jscpd 对同一文件内的克隆会给出反向区间，定位差为负。
 *
 * @param clones - 克隆列表。
 * @returns 指纹数组，已排序。
 */
function fingerprints(clones: Awaited<ReturnType<typeof scanClones>>): string[] {
  return clones
    .map(clone => {
      const a = clone.duplicationA
      const b = clone.duplicationB
      const pair = [a.sourceId, b.sourceId]
        .map(name => relative(root, name).split('\\').join('/'))
        .sort()
        .join('|')
      const fragmentA = a.fragment ?? ''
      const fragmentB = b.fragment ?? ''
      const lines = Math.min(fragmentA.split('\n').length, fragmentB.split('\n').length)
      return `${pair}|${lines}|${fragmentDigest(fragmentA, fragmentB)}`
    })
    .sort()
}

/**
 * 统计指纹出现次数。
 *
 * @param list - 指纹数组。
 * @returns 指纹到次数的映射。
 */
function countByFingerprint(list: readonly string[]): Map<string, number> {
  const counts = new Map<string, number>()
  for (const item of list) counts.set(item, (counts.get(item) ?? 0) + 1)
  return counts
}

const current = fingerprints(await scanClones())

if (process.argv.includes('--update')) {
  writeFileSync(baselinePath, `${JSON.stringify({
    note: '当前已接受的重复；只允许调低。调高须先确认无法消除，并在提交信息里说明理由。',
    minLines: MIN_LINES,
    minTokens: MIN_TOKENS,
    count: current.length,
    fingerprints: current,
  }, null, 2)}\n`)
  console.log(`verify-duplication: baseline rewritten with ${current.length} clone(s).`)
  process.exit(0)
}

if (!existsSync(baselinePath)) {
  console.error(`verify-duplication: missing baseline ${relative(root, baselinePath).replaceAll('\\', '/')}; regenerate it with --update`)
  console.error(gateHint('verify-duplication'))
  process.exit(1)
}

const baseline = JSON.parse(readFileSync(baselinePath, 'utf8')) as { fingerprints?: string[] }
const accepted = baseline.fingerprints ?? []
const known = countByFingerprint(accepted)
const seen = countByFingerprint(current)
const added: string[] = []
for (const [item, times] of seen) {
  for (let index = known.get(item) ?? 0; index < times; index += 1) added.push(item)
}

if (added.length > 0) {
  console.error(`verify-duplication: ${added.length} clone(s) beyond the accepted baseline (accepted ${accepted.length}, current ${current.length}):`)
  for (const item of added.slice(0, DETAIL_LIMIT)) console.error(`  - ${item}`)
  if (added.length > DETAIL_LIMIT) console.error(`  … ${added.length - DETAIL_LIMIT} more`)
  console.error('Eliminate the duplication and rerun; keeping it requires --update with a reason in the commit message.')
  console.error(gateHint('verify-duplication'))
  process.exit(1)
}

console.log(`verify-duplication: ${current.length} clone(s), none beyond the accepted baseline of ${accepted.length}.`)
