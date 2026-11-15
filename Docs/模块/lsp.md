# `:lsp`（独立仓库）

LSPosed 模块位于独立仓库 `NotifyRelay-LSP`（独立 APK，headless、无 GUI），不参与本仓库构建，源码见该仓库 `app/src/main/java/com/xzyht/notifyrelay/lsp/`：

- `XposedInit` — LSPosed 模块入口（继承 `XposedModule`），负责作用域判定与 Hook 安装。
- `XmsfAuthFix` — 超级岛鉴权绕过实现。
