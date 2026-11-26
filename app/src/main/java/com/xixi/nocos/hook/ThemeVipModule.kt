package com.xixi.nocos.hook

import android.util.Log
import com.xixi.nocos.common.ThemeVipConfig
import com.xixi.nocos.data.ThemeVipSettings
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Executable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 主题商店 VIP 解锁。
 *
 * 主题商店 17.16.0 用三个无参 int getter 判断 VIP 状态：
 *   VipUserDto#getVipStatus()             -> 1
 *   VipUserDto#getVipDays()               -> 100
 *   PublishProductItemDto#getIsVipAvailable() -> 1
 * 这里把它们的返回值固定，使界面与商品判断都按已开通处理。
 */
class ThemeVipModule : XposedModule() {

    private val hooksInstalled = AtomicBoolean(false)

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != ThemeVipConfig.targetPackageName || !param.isFirstPackage) return
        if (!hooksInstalled.compareAndSet(false, true)) return

        runCatching {
            if (!readEnabled()) {
                log(Log.INFO, TAG, "Theme VIP disabled, skip hooks for ${param.packageName}")
                return
            }
            val classLoader = param.classLoader
            hookIntGetter(
                classLoader,
                ThemeVipConfig.vipUserDtoClassName,
                ThemeVipConfig.vipStatusMethodName,
                ThemeVipConfig.fakeVipStatus
            )
            hookIntGetter(
                classLoader,
                ThemeVipConfig.vipUserDtoClassName,
                ThemeVipConfig.vipDaysMethodName,
                ThemeVipConfig.fakeVipDays
            )
            hookIntGetter(
                classLoader,
                ThemeVipConfig.publishProductItemDtoClassName,
                ThemeVipConfig.vipAvailableMethodName,
                ThemeVipConfig.fakeVipAvailable
            )
            log(Log.INFO, TAG, "Hooks installed for ${param.packageName}")
        }.onFailure {
            hooksInstalled.set(false)
            log(Log.ERROR, TAG, "Failed to install hooks for ${param.packageName}", it)
        }
    }

    private fun readEnabled(): Boolean {
        return runCatching {
            ThemeVipSettings(getRemotePreferences(ThemeVipConfig.remotePrefsGroup)).isEnabled()
        }.getOrDefault(ThemeVipConfig.defaultEnabled)
    }

    private fun hookIntGetter(
        classLoader: ClassLoader,
        className: String,
        methodName: String,
        fakeValue: Int
    ) {
        runCatching {
            val clazz = Class.forName(className, false, classLoader)
            val method = clazz.declaredMethods.firstOrNull {
                it.name == methodName &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType == Int::class.javaPrimitiveType
            } ?: error("$className#$methodName()I not found")

            hook(method).intercept { fakeValue }
            deoptimizeIfPossible(method)
        }.onFailure {
            log(Log.WARN, TAG, "Hook unavailable: $className#$methodName", it)
        }
    }

    private fun deoptimizeIfPossible(executable: Executable) {
        runCatching {
            if (!deoptimize(executable)) {
                log(Log.WARN, TAG, "Deoptimize returned false for $executable")
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to deoptimize $executable", it)
        }
    }

    companion object {
        private const val TAG = "ThemeVip"
    }
}
