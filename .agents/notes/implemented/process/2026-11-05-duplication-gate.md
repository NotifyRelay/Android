# Agent Note: 接入 jscpd 重复代码门禁并保留失败状态

Status: implemented

## Problem

仓库有七道文档门禁，但没有任何门禁检查代码重复。跨端复用让同一段逻辑在不同位置各写一遍：`:app` 与 `:desktop` 的同名端口实现、Kotlin 与 Rust 核心的镜像类、超级岛模板之间的复制粘贴。这些重复不会让编译失败，也不会让现有任何门禁变红，只在评审时靠人眼发现。

## Decision

`scripts/verify-duplication.ts` 用 jscpd 3.5.10 扫描 Kotlin、Rust 与 TypeScript 源码，与 `duplication-baseline.json` 的指纹比对，出现基线之外的克隆即以退出码 1 失败。门禁经 `pnpm run verify-duplication` 单独运行，挂在 `verify-all` 的第八道位置上，随 Gradle `verifyDocs` 与 PR 工作流的 `docs` job 执行。

语料取自 `git ls-files --cached --recurse-submodules` 与 `git ls-files --others --exclude-standard`：逐级 `.gitignore` 因此成为唯一的排除规则，构建产物、`node_modules` 与生成文件自动出局，未提交的新源码仍与提交后同样受检。子模块按 `--recurse-submodules` 计入，各自应用所属仓库的忽略规则。

基线当前为空（`count: 0`），因此门禁处于失败状态：现有 450 处克隆全部算作超出基线。这是有意的起点，先让新增重复无法通过，再逐批消除存量。

指纹由「文件对 + 片段行数 + 片段内容摘要」构成。行数取自片段文本而非 jscpd 的定位区间：jscpd 对同一文件内的克隆会给出反向区间，用定位差会得到负数。摘要按归一化行尾计算，使 CRLF 与 LF 检出得到同一指纹。比对按出现次数进行，因为 jscpd 会把一处重复按相邻窗口合并成多条克隆。

Gradle 的 `verifyDocs` 任务把各模块源码一并声明为 inputs；只改源码时若不声明，任务会因输入未变而跳过，门禁根本不会运行。

## Alternatives considered

**只扫 `scripts/`，与参考仓库 dsh-source-control 同构。** 参考仓库的重复集中在客户端与宿主解析层，扫它自己的 `src/` 已足够。本仓库的重复不在门禁脚本里，而在跨端与跨语言的业务实现中，只扫 `scripts/` 会让门禁长期通过而毫无作用。

**以当前结果作基线，只拦新增。** 这是参考仓库的做法，代价是基线一经生成就把存量重复固化为「已接受」，之后没人再有动力消除。空基线让每一处重复都是可见的失败，代价是在消除存量之前 `verify-all` 必然红。

**用 glob 拼语料。** 仓库已有 `repo-files.ts` 的自研遍历器，再叠一层排除规则就与 `.gitignore` 形成第二份真相：新增构建目录时两处都要改，漏改一处就让门禁扫到产物。`git ls-files` 直接复用忽略规则，不需要维护另一份清单。

**用 jscpd 5。** jscpd 3.5.10 已被上游标记为不再维护，提示升级到 v5 的 Rust 引擎。v5 的 Node API 与 reporter 结构都变了，而本门禁只需要 `detectClones` 返回的克隆结构；固定在 3.5.10 让指纹格式与参考仓库一致，迁移时能对照两侧结果。升级单独成事，不与接入同一改动。

## Consequences

`verify-all` 在存量重复消除前必然失败，`./gradlew check` 与 PR 工作流随之失败。这是接入时明确接受的状态，也意味着 `check` 暂时不能作为「全部通过」的信号。

消除重复时基线只允许调低；确实无法消除的（跨构建目标的类型镜像）才用 `pnpm run verify-duplication -- --update` 调高，并在提交信息里说明理由。

扫描耗时约 10 秒（609 个文件），随语料增长而增长；它与其余七道门禁并发运行，不延长 `verify-all` 的总时长。
