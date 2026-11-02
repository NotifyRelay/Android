# Agent Note: 桌面端剪贴板回灌与回包解码

Status: implemented

## Problem

桌面端剪贴板同步把远端内容写回本机剪贴板时会触发自己的变更监听，同一份内容被再次送进发送流程。判定的唯一权威是 Rust core，但平台交给它的内容经过一次裸切 JSON 提取：路径里的反斜杠被加倍、换行变成字面反斜杠加字母、含引号的内容在引号处截断。Rust 的防循环与"内容未改变"都以内容字符串相等为前提，前提失效后两道闸门同时落空。图片另有一条独立缺口：它在剪贴板中的形态是平台重编码后的 PNG，与线上 Base64 不同，解码再正确也认不出。平台侧还自持一个 32 位内容哈希，既是对 Rust 去重的重复实现，又在写入之后才赋值、与判定分处两个线程。同一条链路在安卓端以另一种形态存在：安卓的剪贴板监听是 Fcitx5 的剪贴板广播（`FcitxClipboardReceiver`），收到远端内容后写回系统剪贴板同样会被广播回来并再次发出。

## Decision

判定全部留在 Rust core，平台不持有任何同步状态——与 Windows `ClipboardService`、Android `ClipboardSyncManager` 同形。平台只做两件事：回包按 JSON 语法解码（`decodeResultField`），以及把内容归一到 `LF` 后写回剪贴板、采集时再归一一遍；平台原有的 `lastSignature` 内容哈希删除。安卓端写回形态同时收口：图片从 data URL 文本改为内容 URI（`FileProvider` + `cacheDir/clipboard/`），因为 Fcitx5 的广播只在剪贴板条目文本非空时发出，文本形态会让收到的图片被当作文本转发回对端，粘贴时也只能得到 Base64 串。

Rust 侧扩展防循环：`normalize_content` 把收发两侧的换行统一为 `LF`，使平台回读值与接收登记值逐字相等；判定按归一化后的内容比较、不再要求类型一致，图片另按类型与 1000ms 窗口兜底。去掉类型条件是因为安卓把接收到的图片以 `data:image/png;base64,` 前缀的文本写回剪贴板（`normalize_content` 会去掉前缀），经 Fcitx5 广播回来时类型已是 `text` 而登记类型是 `image`，限定类型会漏判并让 Base64 文本落进对端剪贴板。改在 core 而不是各平台，是因为两处缺口都在共享机制里：Windows 宿主用 `JsonDocument` 解码虽正确，但接收侧同样不做换行归一、图片同样经重编码，它和桌面端踩的是同一条；一个 core 的修复同时覆盖所有带监听器的宿主。当前契约见[桌面端](../../../../Docs/桌面端.md)。

## Alternatives considered

**平台侧写入前登记闸门。** 这是 Sefirah `ClipboardFeature.SetContentAsync` 的同类做法：平台记住自己写回的内容，窗口内一致的事件丢弃，本次先实现后撤销。Sefirah 没有 core，平台就是全部判定；NotifyRelay 已把判定收进 Rust，再在平台记一份状态就是对同一决策的第二处实现，与 `ClipboardState` 声明的"平台端不再持有任何同步状态"冲突，而且图片判定还得依赖 JVM 是否仍拥有剪贴板。

**照 Sefirah 做因果一次性抑制：不比对内容，抑制写回后的下一个事件。** 参考实现有两种形态——PC 的 `isInternalUpdate` 布尔量（写入前置真，用低优先级调度器在 `ContentChanged` 跑过之后再清除），Android 的 `suppressOutbound` 加 shell worker 的 `suppressNextOutbound` 计数（`ClipboardListener.suppressNextOutbound` 累加一次，跳过该次 `setPrimaryClip` 的桩）。它不依赖内容形态，图片也能覆盖，但正确性依赖平台回调同步且一次写入只产生一个事件：Android 的 `OnPrimaryClipChangedListener` 在 `setPrimaryClip` 内同步触发，PC 用调度器优先级排序，而 Win32 的 `WM_CLIPBOARDUPDATE` 是异步投递，一次写入经 `EmptyClipboard` 与 `SetClipboardData` 可能投递多条；只计一次会让第二条回灌被当成新的本机复制发出。故 Rust 侧改用内容/类型加时间窗：事件条数不再影响判定。

**安卓继续用 data URL 文本写回图片（省掉 FileProvider）。** 少一个 provider 与一套临时文件管理，但 Fcitx5 会把这段文本记成剪贴板条目并按文本广播回对端，且用户粘贴得到 Base64 串而不是图片；改用内容 URI 后 Fcitx5 的条目文本为空、不广播，粘贴恢复为图片。临时文件只放 `cacheDir/clipboard/`，每次写入后清掉旧文件。

**防循环限定类型一致（本次先这样实现后放宽）。** 内容相等再加类型相等看似更严，但类型是平台写入形态的属性，不是内容的属性：安卓把收到的图片写成 data URL 文本，回读类型与登记类型必然不同，限定类型会漏判这条回灌，把 Base64 文本当成新复制发出去并落进对端剪贴板。放开类型后，内容相等本身仍是强判据。

**只修平台解码，不动 Rust。** 文本回灌确实能被防循环挡住，但图片回灌挡不住：回读值经重编码，与线上 Base64 永不相等，只能等频率限制掐断回环，期间多出一次假的图片发送；接收到的 CRLF 文本也会因登记值与回读值不同而多绕一跳，Windows 宿主同理。

**在平台把剪贴板的换行写成 `CRLF` 以贴合 `CF_UNICODETEXT`。** 与项目既定的 `LF` 口径冲突，且没有证据表明第三方应用对裸 `LF` 的渲染有问题。

**把解码收进 `NativeCore`。** Rust 回包的 JSON 契约确实由 `NativeCore` 持有，但 Android 消费方用平台自带 JSON，桌面端是唯一需要新解码的调用点，为单一调用点扩公共 API 不划算。

## Consequences

平台对 Rust 多了一条显式义务：交给 `clipboardOnChanged` 的内容必须是回读时的那一个字符串；口径一旦不一致，防循环就失效。内容的第二处判定消失后，回灌、重复与限频都只有一条路径，日志里的 `reason` 成为区分"本机复制"与"远端回灌"的唯一依据。

安卓侧多一个 FileProvider 与 `cacheDir/clipboard/` 临时目录，每次收到图片写文件、替换剪贴板后删掉旧文件；旧的手动同步模式（`setManualSyncMode` / `manualSyncClipboard` / 按前台与 Fcitx 判定的读取闸门）随之删除——透明 Activity 已经拿到前台并在 UI 线程同步读取，那套闸门不再有任何调用者。

代价是图片按类型与时间窗判定：窗口中用户新复制的另一张图片会被当成回灌跳过（只影响转发，不影响剪贴板内容）；文本仍按内容判定，无误伤。窗口为 1000ms，平台事件晚于窗口到达时会多发一次同样的内容，下一跳即收敛。
