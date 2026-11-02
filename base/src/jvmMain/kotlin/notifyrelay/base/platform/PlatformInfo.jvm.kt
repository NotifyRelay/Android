package notifyrelay.base.platform

// 桌面端以操作系统名作为平台标识（如 Windows 11 / Linux / Mac OS X）。
actual val platformName: String = System.getProperty("os.name") ?: "JVM"
