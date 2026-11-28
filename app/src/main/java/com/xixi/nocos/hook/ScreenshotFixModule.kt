package com.xixi.nocos.hook

import android.util.Log
import com.xixi.nocos.common.ScreenshotFixConfig
import com.xixi.nocos.data.ScreenshotFixSettings
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 截屏区域修复。
 *
 * 不再依赖混淆后的 getter 名，而是 hook 语义稳定的入口：
 *   com.oplus.screenshot.area.widget.AreaGestureScreenshotView#startEdit(EditorUIType, boolean)
 * 松手时如果原逻辑要进入 EDITOR_SCREENSHOT（直接带已选区域并播放放大动画），
 * 就把类型改成 EDITOR_AREA_SCREENSHOT（整图 + “拖动画出矩形”）。
 *
 * 这些类名、方法名和 EditorUIType 枚举在 17.7.10 / 17.9.0 上都未被混淆。
 * 如果某个版本改掉了入口，退回旧版对 e4.b$a.b()F 的 hook。
 */
class ScreenshotFixModule : XposedModule() {

    private val hooksInstalled = AtomicBoolean(false)

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != ScreenshotFixConfig.targetPackageName || !param.isFirstPackage) return
        if (!hooksInstalled.compareAndSet(false, true)) return

        runCatching {
            if (!readEnabled()) {
                log(Log.INFO, TAG, "Screenshot fix disabled, skip hooks for ${param.packageName}")
                return
            }
            installHooks(param.classLoader)
            log(Log.INFO, TAG, "Hooks installed for ${param.packageName}")
        }.onFailure {
            hooksInstalled.set(false)
            log(Log.ERROR, TAG, "Failed to install hooks for ${param.packageName}", it)
        }
    }

    private fun readEnabled(): Boolean {
        return runCatching {
            ScreenshotFixSettings(getRemotePreferences(ScreenshotFixConfig.remotePrefsGroup)).isEnabled()
        }.getOrDefault(ScreenshotFixConfig.defaultEnabled)
    }

    private fun installHooks(classLoader: ClassLoader) {
        if (installStartEditHook(classLoader)) return
        log(Log.WARN, TAG, "startEdit anchor unavailable, falling back to legacy getter hook")
        installLegacyHook(classLoader)
    }

    private fun installStartEditHook(classLoader: ClassLoader): Boolean {
        val viewClass = runCatching {
            Class.forName(ScreenshotFixConfig.anchorClassName, false, classLoader)
        }.getOrNull() ?: return false

        val editorTypeClass = runCatching {
            Class.forName(ScreenshotFixConfig.editorUiTypeClassName, false, classLoader)
        }.getOrNull() ?: return false

        val method = findStartEdit(viewClass, editorTypeClass) ?: return false

        val areaType = runCatching {
            editorTypeClass.getField(ScreenshotFixConfig.areaEditorTypeField).get(null)
        }.getOrNull() ?: return false

        val screenshotType = runCatching {
            editorTypeClass.getField(ScreenshotFixConfig.screenshotEditorTypeField).get(null)
        }.getOrNull() ?: return false

        hook(method).intercept { chain ->
            val args = chain.args
            if (args.size >= 2 && args[0] == screenshotType) {
                chain.proceed(arrayOf(areaType, args[1]))
            } else {
                chain.proceed()
            }
        }
        deoptimizeIfPossible(method)
        log(Log.INFO, TAG, "Hooked ${method.declaringClass.name}#${method.name} (startEdit strategy)")
        return true
    }

    /**
     * 先按方法名找；方法名被改时，退化为按参数类型定位：
     * 第一个参数是 EditorUIType、第二个是 boolean、返回 void。
     */
    private fun findStartEdit(viewClass: Class<*>, editorTypeClass: Class<*>): Method? {
        val byName = viewClass.declaredMethods.firstOrNull {
            it.name == ScreenshotFixConfig.anchorMethodName && isStartEdit(it, editorTypeClass)
        }
        if (byName != null) return byName
        return viewClass.declaredMethods.firstOrNull { isStartEdit(it, editorTypeClass) }
    }

    private fun isStartEdit(method: Method, editorTypeClass: Class<*>): Boolean {
        return method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == editorTypeClass &&
            method.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
            method.returnType == Void.TYPE
    }

    private fun installLegacyHook(classLoader: ClassLoader) {
        runCatching {
            val clazz = Class.forName(ScreenshotFixConfig.legacyClassName, false, classLoader)
            val getter = clazz.declaredMethods.firstOrNull {
                it.name == ScreenshotFixConfig.legacyMethodName &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType == Float::class.javaPrimitiveType
            } ?: error("${ScreenshotFixConfig.legacyClassName}#${ScreenshotFixConfig.legacyMethodName}()F not found")

            hook(getter).intercept { 0f }
            deoptimizeIfPossible(getter)
            log(Log.INFO, TAG, "Hooked legacy ${clazz.name}#${getter.name}()F")
        }.onFailure {
            log(Log.WARN, TAG, "Legacy hook unavailable", it)
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
        private const val TAG = "ScreenshotFix"
    }
}
