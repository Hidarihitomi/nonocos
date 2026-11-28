package com.xixi.nocos.hook

import android.content.Context
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.Log
import com.xixi.nocos.common.GameStartAnimationConfig
import com.xixi.nocos.data.GameStartAnimationSettings
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.io.File
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 屏蔽游戏助手启动游戏时的「一加风驰游戏内核」启动动画。
 *
 * 游戏助手 11.0.4 中该动画由 newarch 能力 startup_animation 实现：
 *   isSupported(...) 返回 true 后，initialize(...) 会调用
 *   business.secondarypanel.start.anime.a.m(...) 加载
 *   start_anime_page_oneplus*.webp，然后显示风驰卡片。
 *
 * 混淆后的类名（capabilities.d、start.anime.a）每次发版都可能变化，所以这里：
 *   1. 按 featureId 字符串 "startup_animation" 动态定位实现类，hook isSupported；
 *   2. 动态发现失败时才退回已知类名；
 *   3. 仍然失败时，兜底 hook 动画资源加载方法返回 null。
 * 只要包名结构和 featureId 字符串不变，游戏助手升级后仍可继续生效。
 */
class GameStartAnimationModule : XposedModule() {

    private val hooksInstalled = AtomicBoolean(false)
    private val interceptLogged = AtomicBoolean(false)

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != GameStartAnimationConfig.targetPackageName) return
        if (!hooksInstalled.compareAndSet(false, true)) return

        runCatching {
            if (!readEnabled()) {
                log(Log.INFO, TAG, "Game start animation hook disabled, skip ${param.packageName}")
                return
            }

            val featureHooked = hookFeatureSupported(param.classLoader)
            val loadHooked = if (featureHooked) false else hookAnimationLoad(param.classLoader)
            if (!featureHooked && !loadHooked) {
                error("no game start animation hook installed")
            }

            log(
                Log.INFO,
                TAG,
                "Hooks installed for ${param.packageName} (feature=$featureHooked, load=$loadHooked)"
            )
        }.onFailure {
            hooksInstalled.set(false)
            log(Log.ERROR, TAG, "Failed to install hooks for ${param.packageName}", it)
        }
    }

    private fun readEnabled(): Boolean {
        return runCatching {
            GameStartAnimationSettings(
                getRemotePreferences(GameStartAnimationConfig.remotePrefsGroup)
            ).isEnabled()
        }.getOrDefault(GameStartAnimationConfig.defaultEnabled)
    }

    /**
     * 定位 startup_animation 的实现类并让 isSupported 恒为 false。
     * 先按 featureId 动态发现，找不到再退回已知类名。
     */
    private fun hookFeatureSupported(classLoader: ClassLoader): Boolean {
        val candidates = findStartupAnimationClasses(classLoader)
        if (candidates.isEmpty()) {
            log(Log.WARN, TAG, "startup_animation feature class not found")
            return false
        }

        var hooked = false
        for (clazz in candidates) {
            val method = clazz.declaredMethods.firstOrNull {
                it.name == GameStartAnimationConfig.featureMethodName &&
                    it.parameterTypes.size == 2 &&
                    it.returnType == Any::class.java
            }
            if (method == null) {
                log(Log.WARN, TAG, "isSupported not found in ${clazz.name}")
                continue
            }
            runCatching {
                hook(method).intercept {
                    if (interceptLogged.compareAndSet(false, true)) {
                        log(Log.INFO, TAG, "isSupported intercepted for ${clazz.name}")
                    }
                    false
                }
                deoptimizeIfPossible(method)
                hooked = true
                log(Log.INFO, TAG, "Hooked ${clazz.name}#${method.name}")
            }.onFailure {
                log(Log.WARN, TAG, "Failed to hook ${clazz.name}#${method.name}", it)
            }
        }
        return hooked
    }

    private fun findStartupAnimationClasses(classLoader: ClassLoader): List<Class<*>> {
        // 快速路径：当前版本已知类名，命中就不必扫描 dex。
        loadClass(GameStartAnimationConfig.legacyFeatureClassName, classLoader)?.let {
            if (isStartupAnimationFeature(it)) {
                log(Log.INFO, TAG, "Using known feature class ${it.name}")
                return listOf(it)
            }
        }

        val shaped = listClassNames(
            classLoader,
            GameStartAnimationConfig.featurePackagePrefix
        ).mapNotNull { loadClass(it, classLoader) }
            .filter(::hasFeatureShape)

        if (shaped.isEmpty()) return emptyList()

        // 按 featureId 精确筛选；实例化失败时保留 shape 匹配项作为候选。
        val matched = shaped.filter(::reportsStartupAnimationId)
        return if (matched.isNotEmpty()) matched else shaped
    }

    private fun hasFeatureShape(clazz: Class<*>): Boolean {
        val methods = clazz.declaredMethods
        return methods.any {
            it.name == GameStartAnimationConfig.featureMethodName &&
                it.parameterTypes.size == 2 &&
                it.returnType == Any::class.java
        } && methods.any {
            it.name == "initialize" &&
                it.parameterTypes.size == 2 &&
                it.returnType == Any::class.java
        } && methods.any {
            it.name == GameStartAnimationConfig.featureIdMethodName &&
                it.parameterTypes.isEmpty() &&
                it.returnType == String::class.java
        }
    }

    private fun isStartupAnimationFeature(clazz: Class<*>): Boolean {
        return hasFeatureShape(clazz) && reportsStartupAnimationId(clazz)
    }

    private fun reportsStartupAnimationId(clazz: Class<*>): Boolean {
        return runCatching {
            val ctor = clazz.getDeclaredConstructor().apply { isAccessible = true }
            val instance = ctor.newInstance()
            val method = clazz
                .getDeclaredMethod(GameStartAnimationConfig.featureIdMethodName)
                .apply { isAccessible = true }
            method.invoke(instance) == GameStartAnimationConfig.featureId
        }.getOrDefault(false)
    }

    /**
     * 兜底：让动画资源加载方法返回 null。类名可能变化，按包名扫描
     * (String, String) -> Drawable 的静态方法。
     */
    private fun hookAnimationLoad(classLoader: ClassLoader): Boolean {
        val classNames = listClassNames(
            classLoader,
            GameStartAnimationConfig.animationPackagePrefix
        )
        if (classNames.isEmpty()) {
            log(Log.WARN, TAG, "animation helper package not found")
            return false
        }

        var hooked = false
        for (name in classNames) {
            val clazz = loadClass(name, classLoader) ?: continue
            for (method in clazz.declaredMethods) {
                if (!isAnimationLoadMethod(method)) continue
                runCatching {
                    hook(method).intercept { null }
                    deoptimizeIfPossible(method)
                    hooked = true
                    log(Log.INFO, TAG, "Hooked ${clazz.name}#${method.name}")
                }.onFailure {
                    log(Log.WARN, TAG, "Failed to hook ${clazz.name}#${method.name}", it)
                }
            }
        }
        return hooked
    }

    private fun isAnimationLoadMethod(method: Method): Boolean {
        val types = method.parameterTypes
        if (types.size != 2) return false
        if (types[0] != String::class.java || types[1] != String::class.java) return false
        return Drawable::class.java.isAssignableFrom(method.returnType)
    }

    private fun loadClass(name: String, classLoader: ClassLoader): Class<*>? {
        return runCatching { Class.forName(name, false, classLoader) }.getOrNull()
    }

    /**
     * 读取 classLoader 关联 dex 中的类名。只解析 class_defs，不加载类。
     * 失败时返回空列表，由调用方回退到已知类名。
     */
    private fun listClassNames(classLoader: ClassLoader, prefix: String): List<String> {
        val result = LinkedHashSet<String>()
        runCatching {
            val baseDex = Class.forName("dalvik.system.BaseDexClassLoader")
            val pathListField = baseDex.getDeclaredField("pathList").apply { isAccessible = true }
            val pathList = pathListField.get(classLoader) ?: return emptyList()
            val elementsField = pathList.javaClass
                .getDeclaredField("dexElements")
                .apply { isAccessible = true }
            val elements = elementsField.get(pathList) as? Array<*> ?: return emptyList()

            for (element in elements) {
                if (element == null) continue
                val file = runCatching {
                    element.javaClass.getDeclaredField("path")
                        .apply { isAccessible = true }
                        .get(element) as? File
                }.getOrNull() ?: continue
                if (!file.isFile) continue
                collectDexEntries(file, prefix, result)
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to enumerate dex classes", it)
        }
        return result.toList()
    }

    private fun collectDexEntries(file: File, prefix: String, out: MutableSet<String>) {
        runCatching {
            val dexFile = dalvik.system.DexFile(file.absolutePath)
            try {
                val entries = dexFile.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement()
                    if (name.startsWith(prefix)) out.add(name)
                }
            } finally {
                runCatching { dexFile.close() }
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to read dex ${file.name}", it)
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
        private const val TAG = "GameStartAnimation"
    }
}
