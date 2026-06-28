# TODO - 密钥存储迁移计划

## 当前状态

`SecureKeyStorage.kt` 已使用 Android Keystore 作为后端。主密钥（AES-256-GCM）存储在 Android Keystore 中，加密后的数据存入 SharedPreferences。

## 待迁移密钥清单

| 密钥用途 | 当前存储方式 | 优先级 |
|---------|-------------|--------|
| Fcitx5 剪贴板密钥 (`fcitx5_clipboard_key`) | SecureKeyStorage (Android Keystore 保护) | 高 |
| Scrcpy RSA 私钥 | 明文文件 | 高 |
| 设备 sharedSecret | 明文/SharedPreferences | 中 |
| `device_pubkey` | 明文/SharedPreferences | 中 |

## 迁移步骤

### 1. Scrcpy RSA 私钥迁移

- 将现有 RSA 私钥文件写入 SecureKeyStorage
- 删除明文私钥文件

### 2. 设备 sharedSecret 迁移

- 将设备配对时的 sharedSecret 存入 SecureKeyStorage
- 移除代码中的明文引用

### 3. device_pubkey 迁移

- 将设备公钥存入 SecureKeyStorage
- 确保兼容现有已配对的设备

## 注意事项

- 迁移后需测试所有功能（剪贴板同步、Scrcpy 投屏、设备连接）是否正常
- 迁移过程中需处理旧密钥的兼容性，避免用户数据丢失
