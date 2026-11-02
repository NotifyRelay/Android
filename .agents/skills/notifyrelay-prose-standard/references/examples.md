# 提炼后的散文示例

用这些示例识别主导原则，而不是把它们当作文本模板。「Balanced」（平衡版）指以该位置所需的最少解释保留每个承重命题。

被点评的英文例句是英文散文问题的示范本体，逐字保留原文；点评与说明使用中文。

<a id="preserve-every-factual-clause"></a>

## 保留每一个事实分句

**原文：** “The coordinator carefully serializes writes per session, flushes buffered events before disposal resolves, and reports backend failures to the caller.”

**过度精简：** “The coordinator serializes persistence.”

**平衡：** “The coordinator serializes writes per session, flushes buffered events before disposal resolves, and reports backend failures to the caller.”

删除装饰与重复，而不是删除命题。主体、按 Session 的范围、dispose 的顺序与失败的可见性，是彼此独立的事实。

<a id="explicit-skill-scope-is-functional"></a>

## 显式的技能范围是功能性的

**过度精简：** “Read the sources and use judgment.”

**平衡：** “This skill is guidance, not a complete checklist. Use judgment beyond the named checks; documented requirements still apply.”

**过度详细：** 用好几段论证为何清单不能替代独立推理。

保留这条显式限制，因为它会改变 agent 套用该工作流的方式。精简反复的说服，而不是护栏。

<a id="a-cookbook-keeps-action-and-verification"></a>

## 操作手册保留动作与校验

**过度精简：** “Add tests for the tool.”

**平衡：** “Test registration and disposal at unit level, exercise the tool through the real loader path, and add a snapshot when its rendered output changes. Verify the assertion observes the external result rather than the model's report.”

**过度详细：** 把示例代码里已经可见的每个 fixture 文件与断言都走查一遍。

保留测试层级、必需动作、真实入口路径与可观察校验。删除 fixture 叙述。

<a id="preserve-ownership-and-timing"></a>

## 保留归属与时序

**过度精简：** “Provider work is cancelled during teardown.”

**平衡：** “The runtime requests provider cancellation before releasing the child scope; the provider remains responsible for joining its workers before disposal resolves.”

**过度详细：** 按时间顺序叙述实现 teardown 的每一个 promise 与回调。

主体、顺序、归属转移点与完成保证，是彼此独立的事实分句。

<a id="event-jsdoc-preserves-boundary-timing"></a>

## 事件 JSDoc 保留边界时序

**过度精简：** “Composes and caches the session prefix.”

**平衡：** “Composes the session prefix once before the first pre-step and model request. Listener appends join the current request, and pre-step pressure accounting receives the composed prefix.”

**过度详细：** 走查实现该顺序的循环 helper、缓存字段与 promise 回调。

事件顺序及其对当前请求的后果是调用方可见的行为，不是实现叙述。

<a id="orient-complicated-code-without-narrating-it"></a>

## 为复杂代码提供指向，而不是叙述它

**过度精简：** “Worker realm support.”

**平衡：** “Owns the worker realm and its host bridge. Realm initialization is single-shot; disposal terminates the worker and rejects later calls. See the worker-isolation Agent Note for the protocol rationale.”

**过度详细：** 对下方每个类与 helper 函数逐段预告。

保留模块的角色、依赖、职责与非显然的生命周期行为。链接架构理由，让代码自己展示局部控制流。

<a id="public-jsdoc-includes-failures"></a>

## 公开 JSDoc 包含失败

**过度精简：** “Returns the realm global.”

**平衡：** “Returns the initialized realm global. Throws if initialization has not completed or the realm has already been disposed.”

**过度详细：** 导致每次抛出的内部状态机分支与确切 helper 调用。

抛出与状态前置条件是调用方可见的契约事实。

<a id="keep-a-concise-implementation-mapping"></a>

## 保留简明的实现映射

**过度精简：** “Search provider backed by an external API.”

**平衡：** “Maps each provider result to the shared search-result fields, preserving the title, URL, and text while omitting provider-only ranking metadata.”

**过度详细：** 逐字段复述映射代码，包括名称相同、赋值显然的字段。

保留说明适配器在何处丢弃或改变信息的映射细节。

<a id="link-rationale-while-keeping-the-local-contract"></a>

## 链接理由，同时保留局部契约

**过度精简：** “Disposal is documented in the lifecycle Agent Note.”

**平衡：** “Disposal aborts the run and waits for provider quiescence. See the lifecycle Agent Note for ownership and race handling.”

**过度详细：** 在每个 disposer 旁重复 Agent Note 的 promise 编排与被否决的归属模型。

把调用方需要的行为与完成保证留在原处。算法与理由要果断链接；链接不能替代局部契约。

<a id="implemented-agent-notes-retain-verification-contracts"></a>

## 已实施的 Agent Note 保留校验契约

**过度精简：** 因为该 Agent Note 已经交付，就整段删除 Testing 章节。

**平衡：** “Unit tests cover cancellation before and after publication, disposal quiescence, and provider reload. A built-entry smoke covers the real loader path; snapshot coverage is deferred because the transport is process-specific.”

**过度详细：** 逐个文件走查 fixture 与断言，且没有补充任何行为区分。

删除迁移任务与测试叙述。保留层级、各层级钉住的行为、真实入口路径与具名覆盖缺口。

<a id="a-security-boundary-may-need-one-concrete-example"></a>

## 安全边界可能需要一个具体示例

**过度精简：** “Mounted plugins share the host's authority.”

**平衡：** “Mounted plugins share the host's authority; for example, access to `ctx.shell` permits commands with the host executor's privileges.”

**过度详细：** 列出插件可能滥用的每个服务与每种假想攻击。

当某个例子能让原本抽象的安全限制在操作上变得清晰时，保留这一个例子。

<a id="delete-reasoning-transcripts-entirely"></a>

## 彻底删除思考记录

**过度详细：** “First the loop checks whether the value is absent. If it is absent, the next branch returns early. Otherwise it continues, which is why the final assertion is safe.”

**平衡：** 代码已经表达这些分支时不写注释。若早退保护了一条非显然的不变量，就只陈述该不变量。

不要把思考记录压缩成更短的叙述；删除它。

<a id="configuration-comments-explain-what-the-tree-cannot"></a>

## 配置注释解释配置树无法表达的内容

**过度详细：** “This entry loads the local filesystem provider, followed by the policy plugin, followed by the read, write, and edit tools,”——而相邻条目已经展示了这个顺序。

**平衡：** “Load policy before the model-facing tools so their write and edit calls pass through the read-before-mutation gate.”

保留顺序带来的后果、意外的范围规则或安全边界。让配置自己展示其清单。

<a id="do-not-trim-for-word-count-alone"></a>

## 不要仅为词数而精简

**现状：** “The adapter converts provider errors into the shared error type so callers can handle authentication, rate-limit, and transient failures uniformly.”

**更短但更差：** “The adapter normalizes provider errors.”

**平衡的决定：** 保留现有句子，除非某处链接或周边契约已经列出这些失败类别。更短的版本丢失了后果与区分，却没有改善结构。

<a id="model-visible-text-follows-ownership"></a>

## 面向模型的文本遵循归属

**过度精简：** “The tool returns errors when a call fails.”

**过度详细：** 把另一个包的 schema 与渲染器字符串复制进本后端的 README。

**平衡：** 引用本包拥有的稳定提示词、结果与错误文本。schema 链接到生成的工具目录，属于另一个包的文本链接到消费方 README；本地只陈述本包的条件或差异。

抵达模型的措辞就是行为，但复制仍会漂移。精确性属于所有者。

纯散文审计可以指出可疑措辞，但在不存在所属可运行快照时，不得悄悄改动它。保持原样并报告推迟，或把授权改动扩展到包含所有者场景与必需的 GUI 证据。

<a id="generated-summaries-must-stand-alone"></a>

## 生成的摘要必须自足

**过度精简：** “Approval request and policy service.” 所有者后文解释了策略顺序与审计日志，但目录只导出其第一句。

**过度详细：** 把该服务完整的生命周期与提示通知行为都搬进那一句被抽取的句子。

**平衡：** “Approval service that applies session policy before answerers and logs every ask/outcome pair to the requesting session.” 把不属于目录的细节留在后续句子中。

弄清生成器抽取了什么。该片段必须保留其生成产物上所需的契约。

<a id="limitations-are-contracts-not-debt-inventories"></a>

## 限制是契约，不是债务清单

**过度精简：** 省略某个进程生命周期缓存，而它使得配置变更需要重载插件。

**过度详细：** 列出没有调用方或维护者后果的私有 helper 清理项与未使用的仅测试访问器。

**平衡：** “Provider selection is cached for the plugin lifetime; installing or repairing a provider requires reload.” 把普通清理留在其 TODO 或 Agent Note 中。

保留影响使用或安全维护的缺口与非显然约束。包 README 不是积压清单的倾倒场。
