# 通知转发应用
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/NotifyRelay/Android)
![GitHub Downloads (all assets, latest release)](https://img.shields.io/github/downloads/NotifyRelay/Android/latest/total)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/NotifyRelay/Android/total)
![CodeRabbit Pull Request Reviews](https://img.shields.io/coderabbit/prs/github/NotifyRelay/Android?utm_source=oss&utm_medium=github&utm_campaign=NotifyRelay%2FAndroid&labelColor=171717&color=FF570A&link=https%3A%2F%2Fcoderabbit.ai&label=CodeRabbit+Reviews)
## 应用简介

NotifyRelay 是一套**跨设备通知与状态同步**方案。Android 端与 Windows 端在局域网内直连配对，把一台设备上的通知、剪贴板、媒体状态等实时同步到另一台设备，数据不经过公网服务器。

| 端 | 仓库 |
|---|---|
| Android 端（本仓库） | https://github.com/NotifyRelay/Android |
| Windows 端 | https://github.com/NotifyRelay/Windows |
| 跨平台 Rust 核心 | https://github.com/NotifyRelay/notify-relay-core |

### 功能特性

- **通知双向转发**：读取本机通知原文并转发到已配对设备，设备间可双向互转。
- **通知回跳**：转发消息携带原通知的应用跳转信息，接收端安装了对应应用时，点击即可跳转至与发送方一致的界面。
- **超级岛 / 实况通知**：将远端通知渲染为小米超级岛悬浮窗或实况通知，支持进度、计时、图文等模板。
- **剪贴板同步**：跨设备同步剪贴板文本与图片，已适配 Fcitx 输入法。
- **媒体控制**：同步媒体播放状态，并可在对端控制播放。
- **音频转发**：将本机音频转发到其他设备播放。
- **应用列表同步与远程拉起**：同步已安装应用列表与图标，可查看并拉起对端应用。
- **通知过滤与历史**：支持黑/白名单与关键词过滤，本地留存通知历史与超级岛历史。
- **设备发现与配对**：局域网内自动发现设备，配对码认证，基于 ECDH 派生密钥加密传输。
- **屏幕镜像**：内置 Scrcpy 能力，可镜像并控制 Android 设备。


## 开始使用
进入应用后显示欢迎界面,请授权所有的必须权限;
权限使用说明如下:

### 必须权限
- **通知访问权限**: 用于读取通知内容，实现转发功能
- **应用列表权限**: 用于发现本机已安装应用，辅助通知跳转
- **通知发送权限 (Android 13+)**: 用于发送本地通知，部分功能需开启
- **自启动权限**: 必须启用，否则监听服务无法启动

### 可选权限
- **蓝牙连接权限**: 用于优化设备发现速度，显示真实设备名
- **后台无限制权限**: 用于确保应用在后台正常运行，防止被系统杀死
- **悬浮通知权限**: 请手动选择并打开具体的通知类别的悬浮通知权限，以提升通知体验
- **敏感通知访问权限 (Android 15+，可选)**: 未授权时部分通知内容只能获取到'已隐藏敏感通知',因此建议开启以完整接收通知。

## Star History

<a href="https://www.star-history.com/?repos=NotifyRelay%2FAndroid&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/image?repos=NotifyRelay/Android&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/image?repos=NotifyRelay/Android&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/image?repos=NotifyRelay/Android&type=date&legend=top-left" />
 </picture>
</a>
