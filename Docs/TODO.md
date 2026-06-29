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
- 保存时：`sharedSecret` 字段存空（密钥由 Keystore 保护）

### 3. 设备间消息加解密 → Keystore Cipher API
- `DeviceConnectionManager.encryptData()/decryptData()`: 从 `key: String` 改为 `uuid: String`，内部调用 `EncryptionManager.encryptWithDeviceKey()/decryptWithDeviceKey()`
- 加密由 Keystore 中的 AES SecretKey + Cipher API 完成，应用层不接触密钥明文
- 同步更新所有调用方：`ProtocolRouter`、`ProtocolSender`、`ServerLineRouter`、`NotificationProcessor`

## 注意事项

- 迁移后需测试所有功能（剪贴板同步、Scrcpy 投屏、设备连接、超级岛、FTP）是否正常
- FTP 服务器启动时仍引用 `auth.sharedSecret`，当前值为空字符串，可能需要单独处理凭据派生逻辑
