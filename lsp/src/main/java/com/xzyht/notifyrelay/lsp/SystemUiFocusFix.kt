package com.xzyht.notifyrelay.lsp

import android.os.Bundle
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 系统 UI 侧（com.android.systemui）超级岛白名单/鉴权绕过。
 *
 * 对照 HyperCeiler FocusNotifLyric.initLoader：
 * 1. NotificationSettingsManager.canShowFocus() → 恒 true（允许所有应用发送焦点通知）
 * 2. NotificationSettingsManager.canCustomFocus() → 恒 true（允许所有应用发送自定义焦点通知，
 *    自定义 miui.focus.param 模板必需）
 * 3. HyperOS 3+：AuthManager$AuthServiceCallback$onAuthResult$1.invokeSuspend()
 *    将鉴权回调结果 bundle 的 "result_code" 置 0（成功），切断 SystemUI 对焦点通知的撤销。
 */
object SystemUiFocusFix {
    private const val TAG = "NotifyRelay-SystemUiFocusFix"

    fun hook(
        xposed: XposedInterface,
        param: PackageReadyParam,
    ) {
        val classLoader = param.getClassLoader()

        // 1. 焦点通知权限白名单放行
        try {
            val settingsManager =
                classLoader.loadClass("miui.systemui.notification.NotificationSettingsManager")
            findMethod(settingsManager, "canShowFocus")?.let { m ->
                xposed.hook(m).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: Chain): Any? = true
                })
                xposed.log(android.util.Log.INFO, TAG, "canShowFocus 已放行")
            }
            findMethod(settingsManager, "canCustomFocus")?.let { m ->
                xposed.hook(m).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: Chain): Any? = true
                })
                xposed.log(android.util.Log.INFO, TAG, "canCustomFocus 已放行")
            }
        } catch (e: Throwable) {
            xposed.log(android.util.Log.WARN, TAG, "NotificationSettingsManager hook 失败: ${e.message}")
        }

        // 2. HyperOS 3+ 鉴权回调强改成功（类名含 $ 需转义，用 getDeclaredClasses 枚举）
        try {
            val callbackInvoke = findOnAuthResultInvokeSuspend(classLoader) ?: run {
                xposed.log(android.util.Log.INFO, TAG, "onAuthResult invokeSuspend 未找到，跳过")
                return
            }
            xposed.hook(callbackInvoke).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: Chain): Any? {
                    try {
                        val bundle = findAuthBundleField(chain.getThisObject())
                        if (bundle != null) {
                            if (bundle.getInt("result_code", -1) != 0) {
                                bundle.putInt("result_code", 0)
                                xposed.log(android.util.Log.INFO, TAG, "onAuthResult result_code 已置 0")
                            }
                        }
                    } catch (_: Throwable) {
                        // 安全回退
                    }
                    return chain.proceed()
                }
            })
            xposed.log(android.util.Log.INFO, TAG, "onAuthResult invokeSuspend hook 注入成功")
        } catch (e: Throwable) {
            xposed.log(android.util.Log.WARN, TAG, "onAuthResult hook 失败: ${e.message}")
        }
    }

    private fun findMethod(
        clazz: Class<*>,
        name: String,
    ): Method? =
        clazz.declaredMethods
            .firstOrNull { it.name == name && it.parameterCount == 0 && it.returnType == Boolean::class.java }
            ?.apply { isAccessible = true }

    /**
     * 枚举 AuthManager$AuthServiceCallback$<onAuthResult> 内嵌类，找到 invokeSuspend。
     * kotlin.coroutines.Continuation 为参数的 suspend 方法在 dex 中为 (Continuation) 参数。
     */
    private fun findOnAuthResultInvokeSuspend(classLoader: ClassLoader): Method? {
        val authManagerClass =
            classLoader.loadClass("miui.systemui.notification.auth.AuthManager")

        val candidates = mutableListOf<Class<*>>()
        // 先找回调类（反编译类名通常为 AuthManager$AuthServiceCallback$onAuthResult$1 或 $1）
        authManagerClass.declaredClasses
            .filter {
                it.name.contains("AuthServiceCallback") ||
                    it.name.contains("onAuthResult") ||
                    it.name.contains("AuthResult")
            }
            .forEach { candidates.add(it) }
        // 兜底：所有内嵌类中找 invokeSuspend
        if (candidates.isEmpty()) {
            candidates.addAll(authManagerClass.declaredClasses.filter { c ->
                c.declaredMethods.any { it.name == "invokeSuspend" }
            })
        }

        for (clazz in candidates) {
            findInvokeSuspend(clazz)?.let { return it }
        }
        return null
    }

    private fun findInvokeSuspend(clazz: Class<*>): Method? =
        clazz.declaredMethods
            .firstOrNull { it.name == "invokeSuspend" }
            ?.apply { isAccessible = true }

    /** 从回调对象读取 $authBundle 字段（HyperCeiler 观察到该字段名） */
    private fun findAuthBundleField(obj: Any?): Bundle? {
        if (obj == null) return null
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            try {
                clazz.declaredFields
                    .firstOrNull { it.type == Bundle::class.java }
                    ?.let { f ->
                        f.isAccessible = true
                        return f.get(obj) as? Bundle
                    }
            } catch (_: Throwable) {
            }
            clazz = clazz.superclass
        }
        return null
    }
}
