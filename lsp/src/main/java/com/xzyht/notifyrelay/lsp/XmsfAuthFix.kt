package com.xzyht.notifyrelay.lsp

import android.os.Bundle
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 服务框架侧（com.xiaomi.xmsf）鉴权绕过。
 *
 * XMSF 收到 SystemUI 的焦点通知鉴权请求后，通过 AuthSession 向小米服务器查询应用
 * 权限（scope 20032）；未注册 scope 的应用会返回错误码（-300 scope mismatch / -210
 * 超时），SystemUI 据此撤销已渲染的焦点通知。
 *
 * 本 hook 在 AuthSession.getAuthError(Bundle) 返回错误前拦截：把 AuthError 的错误码
 * 置 0（成功），并直接返回 getAuthSuccess() 的成功 Bundle，使 XMSF 侧鉴权恒为成功。
 */
object XmsfAuthFix {
    private const val TAG = "NotifyRelay-XmsfAuthFix"

    fun hook(
        xposed: XposedInterface,
        param: PackageReadyParam,
    ) {
        val classLoader = param.getClassLoader()
        val authSessionClass =
            classLoader.loadClass("com.xiaomi.xms.auth.AuthSession")
        val getAuthError = findGetAuthError(authSessionClass) ?: return
        val getAuthSuccess = findGetAuthSuccess(authSessionClass) ?: return
        val errorCodeField = findErrorCodeField(classLoader) ?: return

        xposed.hook(getAuthError).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: Chain): Any? {
                val error = chain.getArg(0)
                try {
                    errorCodeField.set(error, 0)
                    // 返回成功鉴权 Bundle
                    val successBundle =
                        getAuthSuccess.invoke(chain.getThisObject()) as? Bundle
                    if (successBundle != null) {
                        xposed.log(android.util.Log.INFO, TAG, "已将鉴权错误强改为成功")
                        return successBundle
                    }
                } catch (_: Throwable) {
                    // 任何异常都回退为原逻辑（安全优先）
                }
                return chain.proceed()
            }
        })
        xposed.log(android.util.Log.INFO, TAG, "AuthSession hook 注入成功 (getAuthError)")
    }

    /** 查找 AuthSession.getAuthError(Bundle)：final、1 参数、返回 Bundle */
    private fun findGetAuthError(clazz: Class<*>): Method? =
        clazz.declaredMethods
            .firstOrNull {
                it.name == "getAuthError" &&
                    Modifier.isFinal(it.modifiers) &&
                    it.parameterCount == 1 &&
                    it.returnType == Bundle::class.java
            }
            ?.apply { isAccessible = true }

    /** 查找 AuthSession.getAuthSuccess()：final、0 参数、返回 Bundle */
    private fun findGetAuthSuccess(clazz: Class<*>): Method? =
        clazz.declaredMethods
            .firstOrNull {
                it.name == "getAuthSuccess" &&
                    Modifier.isFinal(it.modifiers) &&
                    it.parameterCount == 0 &&
                    it.returnType == Bundle::class.java
            }
            ?.apply { isAccessible = true }

    /** 查找 AuthError 类中 int 非静态字段（错误码） */
    private fun findErrorCodeField(classLoader: ClassLoader): Field? =
        try {
            val authError = classLoader.loadClass("com.xiaomi.xms.auth.AuthError")
            authError.declaredFields
                .firstOrNull { it.type == Int::class.java && !Modifier.isStatic(it.modifiers) }
                ?.apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }
}
