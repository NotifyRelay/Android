# TODO - 密钥存储迁移计划

## 当前状态

所有密钥已迁移至 Android Keystore 保护。应用消息的加解密由 Keystore 中的 Cipher API 完成，应用层不接触密钥明文。

## 迁移完成状态

| 密钥用途 | 保护方式 | 状态 |
|---------|---------|------|
| Fcitx5 剪贴板密钥 (`fcitx5_clipboard_key`) | SecureKeyStorage (AES-256-GCM, Keystore 主密钥) | ✅ 已完成 |
| 设备 sharedSecret (`aes_device_secret_{uuid}`) | Android Keystore SecretKeyEntry (不可导出) | ✅ 已完成 |
| 设备间消息加解密 | Android Keystore Cipher API (AES/GCM/NoPadding) | ✅ 已完成 |

## 已执行的迁移

### 1. 设备 sharedSecret → Android Keystore
- `EncryptionManager` 新增 `importAesKeyToKeystore()` / `encryptWithDeviceKey()` / `decryptWithDeviceKey()`
- 配对时（`ServerLineRouter.kt`）：生成 sharedSecret 后立即导入 Keystore，AuthInfo 不保留明文
- 加载时（`DeviceConnectionManager.loadAuthedDevices()`）：遗留 DB 数据自动导入 Keystore 并清空 DB
- 保存时：`sharedSecret` 字段有意存为空字符串，密钥由 Keystore 保护（空字符串避免序列化歧义，配合迁移代码确保兼容）

### 3. 设备间消息加解密 → Keystore Cipher API
- `DeviceConnectionManager.encryptData()/decryptData()`: 从 `key: String` 改为 `uuid: String`，内部调用 `EncryptionManager.encryptWithDeviceKey()/decryptWithDeviceKey()`
- 加密由 Keystore 中的 AES SecretKey + Cipher API 完成，应用层不接触密钥明文
- 同步更新所有调用方：`ProtocolRouter`、`ProtocolSender`、`ServerLineRouter`、`NotificationProcessor`

## 注意事项

### 迁移后测试矩阵
- 剪贴板同步：Fcitx5 配对/解配对/数据解密端到端
- 设备连接：ECDH 握手 + Keystore 加解密往返
- 超级岛：列表模式切换、通知替换逻辑、超时行为
- FTP：Keystore 凭据派生（待修复后）

### FTP 凭据派生
- FTP 服务器启动时不再引用 `auth.sharedSecret`（已为空字符串），改为每次启动生成随机用户名/密码
- 随机凭据通过 DATA_FTP 响应中的 `username`/`password` 字段返回给 PC 端
- 当前响应未包含 username/password，需补充响应字段以支持客户端连接
- **[待修复]** 在 ProtocolRouter DATA_FTP `start` 响应的 JSON 中增加 `username`/`password` 字段
