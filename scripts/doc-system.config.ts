/**
 * 所有文档门禁扫描范围的唯一来源。
 *
 * 各门禁从此处读取扫描模式、预算清单与 Agent Note 布局，不硬编码仓库路径；
 * 采用本系统的项目只改这一个文件，无需逐个改九个脚本。
 *
 * @module scripts/doc-system.config
 */

import { resolve } from 'node:path'

/** 所有门禁共享的仓库绝对根路径。 */
export const repoRoot = resolve(import.meta.dirname, '..')

/** 一棵 Agent Note 树的封闭布局。 */
export interface AgentNoteLayout {
  /** 相对仓库的笔记根目录。 */
  readonly root: string
  /** 根目录下直接存在的生命周期文件夹封闭集合。 */
  readonly lifecycles: readonly string[]
  /** 每个生命周期下均存在的分类文件夹封闭集合。 */
  readonly classes: readonly string[]
  /** 冻结历史的文件夹名；其中的笔记永不编辑。 */
  readonly archived: string
}

/** 文档门禁读取的全部范围决策。 */
export interface DocSystemConfig {
  /**
   * 散文门禁扫描的相对仓库 glob，顺序固定。归档 Agent Note 由遍历器
   * 排除，不由这些模式排除。
   */
  readonly markdownPatterns: readonly string[]
  /** 词数上限清单的相对仓库路径。 */
  readonly budgetManifest: string
  /** Agent Note 树布局。 */
  readonly agentNotes: AgentNoteLayout
  /**
   * 仓库是否维护英文/中文三元组。为 false 时，配对门禁不启用，归档笔记
   * 以无对侧的方式封印。
   */
  readonly bilingual: boolean
}

/** 文档系统配置的范围。 */
export const docSystem: DocSystemConfig = {
  markdownPatterns: [
    'README.md',
    'AGENTS.md',
    'Docs/**/*.md',
    '.agents/notes/**/*.md',
    '.agents/skills/**/*.md',
  ],
  budgetManifest: 'scripts/doc-budgets.manifest.json',
  agentNotes: {
    root: '.agents/notes',
    lifecycles: ['proposed', 'implemented', 'rejected'],
    classes: ['feature', 'bug-fix', 'simplification', 'architecture', 'process', 'testing'],
    archived: 'archived',
  },
  bilingual: false,
}
