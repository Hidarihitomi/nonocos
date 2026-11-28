package com.xixi.nocos.hook

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.SystemClock
import android.util.Log
import com.xixi.nocos.common.MultiAppConfig
import com.xixi.nocos.data.MultiAppSettings
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 应用分身增强。
 *
 * 参考 ONextBox：系统进程负责分身白名单、黑名单与数量上限，分身应用
 * （com.oplus.multiapp）负责界面里的可选应用列表与创建数量。两边同时放开，
 * 才能在「应用分身」里看到并创建任意应用的分身。
 */
class MultiAppModule : XposedModule() {

    private val appHooksInstalled = AtomicBoolean(false)
    private val systemHooksInstalled = AtomicBoolean(false)

    @Volatile
    private var unlockApps = false

    @Volatile
    private var extendedSlots = false

    @Volatile
    private var eligiblePackages: Set<String> = emptySet()

    @Volatile
    private var eligibleRefreshedAt = 0L

    private val querying = ThreadLocal<Boolean>()
    private val collecting = AtomicBoolean()

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != MultiAppConfig.targetPackageName) return
        if (!appHooksInstalled.compareAndSet(false, true)) return

        runCatching {
            readSettings()
            if (!unlockApps && !extendedSlots) {
                log(Log.INFO, TAG, "Multi app hooks disabled, skip ${param.packageName}")
                return
            }
            hookApp(param.classLoader)
            log(Log.INFO, TAG, "Hooks installed for ${param.packageName}")
        }.onFailure {
            appHooksInstalled.set(false)
            log(Log.ERROR, TAG, "Failed to install multi app hooks for ${param.packageName}", it)
        }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        if (!systemHooksInstalled.compareAndSet(false, true)) return

        runCatching {
            readSettings()
            if (!unlockApps && !extendedSlots) {
                log(Log.INFO, TAG, "Multi app system hooks disabled, skip system_server")
                return
            }
            hookSystem(param.classLoader)
            log(Log.INFO, TAG, "System hooks installed for system_server")
        }.onFailure {
            systemHooksInstalled.set(false)
            log(Log.ERROR, TAG, "Failed to install multi app system hooks", it)
        }
    }

    private fun readSettings() {
        runCatching {
            val settings = MultiAppSettings(
                getRemotePreferences(MultiAppConfig.remotePrefsGroup)
            )
            unlockApps = settings.isUnlockAppsEnabled()
            extendedSlots = settings.isExtendedSlotsEnabled()
        }.onFailure {
            log(Log.WARN, TAG, "Unable to read multi app settings, keep defaults", it)
        }
    }

    // ---------------------------------------------------------------------
    // 应用侧
    // ---------------------------------------------------------------------

    private fun hookApp(classLoader: ClassLoader) {
        val platform = findClass("com.oplus.multiapp.platform.MultiAppAPI", classLoader)
        if (platform != null && unlockApps) {
            hookList(
                platform.getDeclaredMethod("getAllowedMultiApp"),
                classLoader,
                system = false
            )
            platform.declaredMethods
                .filter { it.name == "getAllowedMultiApp" }
                .forEach(::deoptimizeQuietly)
        }

        val data = findClass("com.oplus.multiapp.data.MultiAppDataManager", classLoader)
        if (data != null && unlockApps) {
            hookList(data.getDeclaredMethod("getAllowedAppList"), classLoader, system = false)
            hookBlacklist(data.getDeclaredMethod("getBlackApps"), classLoader, system = false)
        }

        val helper = findClass("com.oplus.multiapp.utils.CustomHelper", classLoader)
        if (helper != null && unlockApps) {
            hook(helper.getDeclaredMethod("isAppLimited")).intercept { chain ->
                if (unlockApps) false else chain.proceed()
            }
            hook(helper.getDeclaredMethod("getMaxCreateNum")).intercept { chain ->
                val native = chain.proceed() as? Int ?: 0
                if (unlockApps && native > 0) {
                    maxOf(native, eligible(classLoader, system = false).size)
                } else {
                    native
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // 系统侧
    // ---------------------------------------------------------------------

    private fun hookSystem(classLoader: ClassLoader) {
        val config = findClass("com.android.server.am.OplusMultiAppConfigManager", classLoader)
        val service = findClass("com.android.server.am.OplusMultiAppManagerService", classLoader)

        if (config != null && unlockApps) {
            hookList(config.getDeclaredMethod("getAllowedList"), classLoader, system = true)
            runCatching {
                hook(config.getDeclaredMethod("getMaxCreatedNum")).intercept { chain ->
                    val native = chain.proceed() as? Int ?: 0
                    if (unlockApps && native > 0) {
                        maxOf(native, eligible(classLoader, system = true).size)
                    } else {
                        native
                    }
                }
            }.onFailure { log(Log.WARN, TAG, "getMaxCreatedNum unavailable", it) }

            config.declaredMethods
                .filter {
                    it.name == "isAllowed" &&
                        it.returnType == Boolean::class.javaPrimitiveType &&
                        it.parameterTypes.firstOrNull() == String::class.java
                }
                .forEach { method ->
                    runCatching {
                        hook(method).intercept { chain ->
                            val name = chain.getArg(0) as? String
                            if (unlockApps && name != null &&
                                name in eligible(classLoader, system = true)
                            ) {
                                true
                            } else {
                                chain.proceed()
                            }
                        }
                        deoptimizeQuietly(method)
                    }.onFailure { log(Log.WARN, TAG, "isAllowed hook failed", it) }
                }
        }

        if (service != null && unlockApps) {
            runCatching {
                hook(service.getDeclaredMethod("hasMaxCreatedNumLimitedFeature")).intercept { chain ->
                    if (unlockApps) false else chain.proceed()
                }
            }.onFailure { log(Log.WARN, TAG, "hasMaxCreatedNumLimitedFeature unavailable", it) }
        }

        if (config != null && extendedSlots) {
            runCatching {
                hook(config.getDeclaredMethod("getMaxCloneUserNum")).intercept { chain ->
                    if (extendedSlots) MultiAppConfig.extendedSlotCount else chain.proceed()
                }
            }.onFailure { log(Log.WARN, TAG, "getMaxCloneUserNum unavailable", it) }
        }

        if (unlockApps) hookSystemData(classLoader)
    }

    private fun hookSystemData(classLoader: ClassLoader) {
        val data = findClass("com.android.server.pm.OplusMultiAppDataManager", classLoader) ?: return
        runCatching {
            hookBlacklist(data.getDeclaredMethod("getBlackAppSnapshot"), classLoader, system = true)
        }.onFailure { log(Log.WARN, TAG, "getBlackAppSnapshot unavailable", it) }

        runCatching {
            val listMethod = data.getDeclaredMethod("getMultiAppInfos", Int::class.javaPrimitiveType)
            val rebuild = data.getDeclaredMethod("initMultiAppInfoList").apply { isAccessible = true }
            var lastMode: Pair<Boolean, Boolean>? = null
            var lastEligible: Set<String> = emptySet()

            hook(listMethod).intercept { chain ->
                val mode = unlockApps to extendedSlots
                val available = if (unlockApps) {
                    eligible(classLoader, system = true)
                } else {
                    emptySet()
                }
                if (lastMode != mode || lastEligible != available) {
                    val identity = Binder.clearCallingIdentity()
                    try {
                        runCatching { rebuild.invoke(chain.thisObject) }
                            .onFailure { log(Log.WARN, TAG, "Unable to rebuild clone cache", it) }
                        lastMode = mode
                        lastEligible = available
                    } finally {
                        Binder.restoreCallingIdentity(identity)
                    }
                }
                chain.proceed()
            }
        }.onFailure { log(Log.WARN, TAG, "getMultiAppInfos unavailable", it) }
    }

    // ---------------------------------------------------------------------
    // 公共
    // ---------------------------------------------------------------------

    private fun hookList(method: Method, classLoader: ClassLoader, system: Boolean) {
        runCatching {
            hook(method).intercept { chain ->
                val native = chain.proceed()
                if (!unlockApps) return@intercept native
                val list = native as? List<*> ?: return@intercept native
                val result = LinkedHashSet<String>()
                list.filterIsInstance<String>().forEach(result::add)
                result.addAll(eligible(classLoader, system))
                ArrayList(result)
            }
        }.onFailure { log(Log.WARN, TAG, "Unable to hook ${method.name}", it) }
    }

    private fun hookBlacklist(method: Method, classLoader: ClassLoader, system: Boolean) {
        runCatching {
            hook(method).intercept { chain ->
                val native = chain.proceed()
                if (!unlockApps) return@intercept native
                val blocked = eligible(classLoader, system)
                when (native) {
                    is List<*> -> native.filterNot { it is String && it in blocked }
                    is Set<*> -> native.filterNot { it is String && it in blocked }.toSet()
                    else -> native
                }
            }
        }.onFailure { log(Log.WARN, TAG, "Unable to hook ${method.name}", it) }
    }

    private fun eligible(classLoader: ClassLoader, system: Boolean): Set<String> {
        if (querying.get() == true) return eligiblePackages
        val now = SystemClock.elapsedRealtime()
        if (eligibleRefreshedAt != 0L && now - eligibleRefreshedAt < CACHE_MILLIS) {
            return eligiblePackages
        }
        if (!collecting.compareAndSet(false, true)) return eligiblePackages

        val token = if (system) Binder.clearCallingIdentity() else null
        querying.set(true)
        try {
            val context = applicationContext(classLoader, system) ?: return eligiblePackages
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val flags = PackageManager.MATCH_DIRECT_BOOT_AWARE or
                PackageManager.MATCH_DIRECT_BOOT_UNAWARE
            val apps = context.packageManager.queryIntentActivities(intent, flags)
            eligiblePackages = apps.mapNotNull { item ->
                item.activityInfo?.applicationInfo?.takeIf {
                    it.enabled &&
                        it.uid % 100_000 >= 10_000 &&
                        it.packageName !in MultiAppConfig.excludedPackages
                }?.packageName
            }.toSet()
            eligibleRefreshedAt = now
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "Unable to query launchable apps", t)
        } finally {
            querying.remove()
            collecting.set(false)
            if (token != null) Binder.restoreCallingIdentity(token)
        }
        return eligiblePackages
    }

    private fun applicationContext(classLoader: ClassLoader, system: Boolean): Context? =
        runCatching {
            val thread = Class.forName("android.app.ActivityThread", false, classLoader)
            if (!system) {
                thread.getDeclaredMethod("currentApplication").invoke(null) as? Context
            } else {
                val current = thread.getDeclaredMethod("currentActivityThread").invoke(null)
                thread.getDeclaredMethod("getSystemContext").invoke(current) as? Context
            }
        }.getOrNull()

    private fun findClass(name: String, classLoader: ClassLoader): Class<*>? =
        runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    private fun deoptimizeQuietly(executable: Executable) {
        runCatching { deoptimize(executable) }
    }

    companion object {
        private const val TAG = "NocosMultiApp"
        private const val CACHE_MILLIS = 30_000L
    }
}
