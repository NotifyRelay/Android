/**
 * 重复代码门禁：出现基线之外的克隆即失败；`--report` 重写人工可读的清单。
 *
 * 扫描口径、语料来源、合并规则与分层判定见 `scripts/duplication-report.ts` 的模块注释。
 *
 * 基线记的是已接受的重复指纹（文件对 + 片段长度 + 片段内容摘要）及其出现次数，另记整层接受
 * 的层标识。已接受条目只允许调低；确实无法消除的才调高，并在提交信息里说明理由。
 *
 * 同一指纹在基线与当前各出现 N 次时视为未新增：jscpd 会把一处重复按相邻窗口合并成多条
 * 克隆，只比对实例存在与否会让基线随首处重复的条数变化而失效。
 *
 * 用法：tsx scripts/verify-duplication.ts [--update | --accept <layer> | --pin <layer> | --report]
 *   --update         按当前扫描结果整体重写基线，并清空整层接受记录。
 *   --accept <layer> 把该层整层接受进基线（记层标识而非逐条指纹），其余层仍按原基线判定。
 *                    该层此后新出现的重复同样不再拦截，因此接受须在提交信息里说明理由。
 *   --pin <layer>    把该层当前的克隆逐条固定进基线。用于「这一层确实是无法消除的结构重复，
 *                    但同类新重复仍须拦截」的情形：整层接受会放过该层今后出现的任何重复。
 *   --report         按当前扫描结果重写 `duplication-report.md`；清单不参与门禁判定，门禁也
 *                    不会自动刷新它。
 *
 * @module scripts/verify-duplication
 */

import { createHash } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { relative, resolve } from 'node:path'
import { LAYERS, MIN_LINES, MIN_TOKENS, layerOfClone, renderReport, scanClones, type Clone } from './duplication-report.ts'
import { repoRoot } from './doc-system.config.ts'
import { gateHint } from './gate-hint.ts'

const root = repoRoot
const baselinePath = resolve(root, 'duplication-baseline.json')
const reportPath = resolve(root, 'duplication-report.md')

/** 失败时逐条列出的指纹上限；全量数百条会把真正的第一条挤出视野。 */
const DETAIL_LIMIT = 20

/** 基线文件的内容。 */
interface Baseline {
  /** 人类可读的维护说明。 */
  readonly note: string
  /** 扫描参数；与现值不符即视为基线失效。 */
  readonly minLines: number
  /** 扫描参数；与现值不符即视为基线失效。 */
  readonly minTokens: number
  /** 已接受的指纹总数。 */
  readonly count: number
  /** 已接受的指纹，按字典序排列。 */
  readonly fingerprints: readonly string[]
  /** 已整层接受的层标识；这些层的当前克隆不计入失败。 */
  readonly acceptedLayers?: readonly string[]
}

/**
 * 计算片段内容摘要。
 *
 * 先归一化行尾再摘要：同一份片段在检出为 CRLF 与 LF 的机器上文本不同，不归一化会让基线
 * 只在某一侧通过。摘要取两侧片段中字节序较小的一侧，使指纹与 A/B 侧归属无关。
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
 * 同文件对、同长度但片段内容被替换时因此算新的重复。
 *
 * 行数取自片段文本而非定位：jscpd 对同一文件内的克隆会给出反向区间，定位差为负。
 *
 * @param clone - 一段克隆。
 * @returns 指纹。
 */
function fingerprintOf(clone: Clone): string {
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

/**
 * 写出基线。
 *
 * @param accepted - 已接受的指纹。
 * @param layers - 已整层接受的层标识。
 */
function writeBaseline(accepted: readonly string[], layers: readonly string[]): void {
  const sorted = [...accepted].sort()
  const baseline: Baseline = {
    note: '已接受的重复指纹；只允许调低。调高须先确认无法消除，并在提交信息里说明理由。',
    minLines: MIN_LINES,
    minTokens: MIN_TOKENS,
    count: sorted.length,
    fingerprints: sorted,
    acceptedLayers: [...layers].sort(),
  }
  writeFileSync(baselinePath, `${JSON.stringify(baseline, null, 2)}\n`)
}

/**
 * 读取基线。
 *
 * 扫描参数与基线记录的不符即报错而不是静默沿用：阈值一变，指纹的构成就变了，旧基线
 * 既不能证明未新增，也不能证明已消除。
 *
 * @returns 基线，或失败原因。
 */
/**
 * 读取并校验层级参数，非法时直接终止进程。
 *
 * @param flag 触发本次调用的参数名，用于错误提示。
 * @param index 该参数在 argv 中的下标。
 * @returns 合法的层标识。
 */
function requireLayerArg(flag: string, index: number): string {
  const layer = process.argv[index + 1]
  if (layer === undefined || !(LAYERS as readonly string[]).includes(layer)) {
    console.error(`verify-duplication: ${flag} needs one of ${LAYERS.join(', ')}`)
    process.exit(1)
  }
  return layer
}

function readBaseline(): { baseline: Baseline } | { error: string } {
  if (!existsSync(baselinePath)) {
    return { error: `missing baseline ${relative(root, baselinePath).replaceAll('\\', '/')}; regenerate it with --update` }
  }
  const baseline = JSON.parse(readFileSync(baselinePath, 'utf8')) as Baseline
  if (baseline.minLines !== MIN_LINES) {
    return { error: `baseline recorded with minLines ${baseline.minLines}, scanning with ${MIN_LINES}; regenerate it with --update` }
  }
  if (baseline.minTokens !== MIN_TOKENS) {
    return { error: `baseline recorded with minTokens ${baseline.minTokens}, scanning with ${MIN_TOKENS}; regenerate it with --update` }
  }
  return { baseline }
}

const clones = await scanClones()

if (process.argv.includes('--report')) {
  writeFileSync(reportPath, renderReport(clones))
  console.log(`verify-duplication: report rewritten from ${clones.length} clone(s).`)
  process.exit(0)
}

const current = clones.map(fingerprintOf)
const layerByFingerprint = new Map<string, string>()
clones.forEach((clone, index) => layerByFingerprint.set(current[index] as string, layerOfClone(clone)))

if (process.argv.includes('--update')) {
  writeBaseline(current, [])
  console.log(`verify-duplication: baseline rewritten with ${current.length} clone(s).`)
  process.exit(0)
}

const acceptIndex = process.argv.indexOf('--accept')
if (acceptIndex !== -1) {
  const layer = requireLayerArg('--accept', acceptIndex)
  const read = readBaseline()
  const existing = 'baseline' in read ? read.baseline.fingerprints : []
  const layers = 'baseline' in read ? (read.baseline.acceptedLayers ?? []) : []
  const picked = current.filter(fingerprint => layerByFingerprint.get(fingerprint) === layer)
  // 整层接受只记层标识，不逐条写指纹：import 块会随新增 import 自然变长，逐条记录会让
  // 每次正常编辑都产生一批「新指纹」而反复变红，把这一类的接受变成一次性快照。
  const nextLayers = layers.includes(layer) ? layers : [...layers, layer]
  writeBaseline(existing, nextLayers)
  console.log(`verify-duplication: layer ${layer} accepted (${picked.length} clone(s) today); accepted layers now ${nextLayers.join(', ')}.`)
  process.exit(0)
}

const pinIndex = process.argv.indexOf('--pin')
if (pinIndex !== -1) {
  const layer = requireLayerArg('--pin', pinIndex)
  const read = readBaseline()
  const existing = 'baseline' in read ? read.baseline.fingerprints : []
  const layers = 'baseline' in read ? (read.baseline.acceptedLayers ?? []) : []
  const known = countByFingerprint(existing)
  const seen = countByFingerprint(current.filter(fingerprint => layerByFingerprint.get(fingerprint) === layer))
  // 逐条固定而不是整层接受：该层被认定为无法消除的结构重复，但新出现的同类重复仍要拦。
  // 只补「当前次数超出已记录次数」的部分：重复执行 --pin 不得把同一指纹叠加进基线。
  const picked: string[] = []
  for (const [item, times] of seen) {
    for (let index = known.get(item) ?? 0; index < times; index += 1) picked.push(item)
  }
  writeBaseline([...existing, ...picked], layers)
  console.log(`verify-duplication: pinned ${picked.length} clone(s) in layer ${layer}; baseline now ${existing.length + picked.length}.`)
  process.exit(0)
}

const read = readBaseline()
if ('error' in read) {
  console.error(`verify-duplication: ${read.error}`)
  console.error(gateHint('verify-duplication'))
  process.exit(1)
}
const accepted = read.baseline.fingerprints
const acceptedLayers = new Set(read.baseline.acceptedLayers ?? [])
const known = countByFingerprint(accepted)
const seen = countByFingerprint(current)
const added: string[] = []
for (const [item, times] of seen) {
  if (acceptedLayers.has(layerByFingerprint.get(item) ?? '')) continue
  for (let index = known.get(item) ?? 0; index < times; index += 1) added.push(item)
}

if (added.length > 0) {
  console.error(`verify-duplication: ${added.length} clone(s) beyond the accepted baseline (accepted ${accepted.length}, current ${current.length}):`)
  for (const item of added.slice(0, DETAIL_LIMIT)) console.error(`  - ${item}`)
  if (added.length > DETAIL_LIMIT) console.error(`  … ${added.length - DETAIL_LIMIT} more`)
  console.error('Eliminate the duplication and rerun; keeping it requires --accept <layer> or --update with a reason in the commit message.')
  console.error(gateHint('verify-duplication'))
  process.exit(1)
}

console.log(`verify-duplication: ${current.length} clone(s), none beyond the accepted baseline of ${accepted.length}.`)
