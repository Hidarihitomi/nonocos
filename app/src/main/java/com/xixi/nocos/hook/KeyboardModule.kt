package com.xixi.nocos.hook

import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.EditorInfo
import com.xixi.nocos.common.KeyboardConfig
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 小布输入法增强，只保留两个功能：
 *   - 解锁更多应用的键盘材质（系统模糊材质白名单放行）
 *   - ColorOS 17 键盘莫奈配色（见 [KeyboardAppearance]）
 */
class KeyboardModule : XposedModule() {

    private val installed = AtomicBoolean(false)

    @Volatile
    private var prefs: SharedPreferences? = null

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != KeyboardConfig.targetPackageName) return
        if (!installed.compareAndSet(false, true)) return

        runCatching {
            val preferences = getRemotePreferences(KeyboardConfig.remotePrefsGroup)
            prefs = preferences
            runCatching {
                preferences.registerOnSharedPreferenceChangeListener { _, _ -> }
            }

            installMaterialHook(param.classLoader)
            KeyboardAppearance(this, preferences, param.classLoader).install()
            log(Log.INFO, TAG, "Keyboard hooks installed for ${param.packageName}")
        }.onFailure {
            installed.set(false)
            log(Log.ERROR, TAG, "Failed to install keyboard hooks", it)
        }
    }

    // ---------------------------------------------------------------------
    // 材质模糊
    // ---------------------------------------------------------------------

    private fun installMaterialHook(classLoader: ClassLoader) {
        // 1.9.25.17 的「哪些应用可用材质」白名单在 AppInfoUtils.D(EditorInfo)，
        // 与 ONextBox 的 applyBlurEnable pkg= 门是同一组系统应用；直接放行。
        runCatching {
            val gate = Class.forName("com.oplus.drs.core.db.a", false, classLoader)
                .getDeclaredMethod("D", EditorInfo::class.java)
            hook(gate).intercept { chain ->
                val editor = chain.getArg(0) as? EditorInfo
                val packageName = editor?.packageName
                if (isEnabled(KeyboardConfig.prefsKeyMaterialUnlock, KeyboardConfig.defaultMaterialUnlock) &&
                    !packageName.isNullOrEmpty() && packageName.length <= 255
                ) {
                    true
                } else {
                    chain.proceed()
                }
            }
            log(Log.INFO, TAG, "Material app allowlist hook installed")
        }.onFailure {
            log(Log.WARN, TAG, "Material app allowlist hook unavailable", it)
        }

        runCatching {
            val system = Class.forName("android.provider.Settings\$System", false, classLoader)
            listOf(
                system.getDeclaredMethod(
                    "getInt",
                    android.content.ContentResolver::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                ),
                system.getDeclaredMethod(
                    "getInt",
                    android.content.ContentResolver::class.java,
                    String::class.java
                )
            ).forEach { method ->
                hook(method).intercept { chain ->
                    val name = chain.getArg(1) as? String
                    if (name == MATERIAL_BLUR_SETTING &&
                        isEnabled(KeyboardConfig.prefsKeyMaterialUnlock, KeyboardConfig.defaultMaterialUnlock)
                    ) {
                        1
                    } else {
                        chain.proceed()
                    }
                }
            }
            log(Log.INFO, TAG, "Material setting hook installed")
        }.onFailure {
            log(Log.WARN, TAG, "Material setting hook unavailable", it)
        }

        // 1.9.25.17 的材质总开关是 AbstractC0925f.b()（真实类名 base.util.f），
        // 它还会检查系统开关与日夜模式；直接放行可确保首次显示就使用材质。
        runCatching {
            val gate = Class.forName("com.oplus.keyboard.base.util.f", false, classLoader)
                .getDeclaredMethod("b")
            hook(gate).intercept { chain ->
                if (isEnabled(KeyboardConfig.prefsKeyMaterialUnlock, KeyboardConfig.defaultMaterialUnlock)) {
                    true
                } else {
                    chain.proceed()
                }
            }
            deoptimize(gate)
            log(Log.INFO, TAG, "Material gate hook installed")
        }.onFailure {
            log(Log.WARN, TAG, "Material gate hook unavailable", it)
        }

        runCatching {
            val switch = Class.forName("com.oplus.keyboard.base.data.o", false, classLoader)
                .getDeclaredMethod("k")
            hook(switch).intercept { chain ->
                if (isEnabled(KeyboardConfig.prefsKeyMaterialUnlock, KeyboardConfig.defaultMaterialUnlock)) {
                    true
                } else {
                    chain.proceed()
                }
            }
            log(Log.INFO, TAG, "Material switch hook installed")
        }.onFailure {
            log(Log.WARN, TAG, "Material switch hook unavailable", it)
        }
    }

    internal fun isEnabled(key: String, default: Boolean): Boolean =
        runCatching { prefs?.getBoolean(key, default) ?: default }.getOrDefault(default)

    companion object {
        private const val TAG = "NocosKeyboard"
        private const val MATERIAL_BLUR_SETTING = "system_material_blur_enable"
    }
}
