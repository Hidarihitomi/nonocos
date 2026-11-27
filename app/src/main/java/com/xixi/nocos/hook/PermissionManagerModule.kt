package com.xixi.nocos.hook

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import com.xixi.nocos.common.PermissionManagerConfig
import com.xixi.nocos.data.PermissionManagerSettings
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 权限管理增强。
 *
 * 参考 ONextBox 的实现，按进程分别安装：
 *   - com.oplus.securitypermission：旧版启动弹窗、始终允许、永久白名单、自动解除受限设置
 *   - system：跳过恶意应用启动拦截
 *   - com.android.settings / com.android.permissioncontroller：国际版权限页面
 */
class PermissionManagerModule : XposedModule() {

    private val hooksInstalled = AtomicBoolean(false)
    private val systemHooksInstalled = AtomicBoolean(false)

    @Volatile private var oldStartDialog = false

    @Volatile
    private var alwaysAllow = false

    @Volatile
    private var unlockRestricted = false

    @Volatile
    private var disableMalicious = false

    @Volatile
    private var exportPages = false

    override fun onPackageReady(param: PackageReadyParam) {
        // system_server 的包名是 android，由 onSystemServerStarting 处理。
        if (param.packageName == "android" ||
            param.packageName == PermissionManagerConfig.systemPackage
        ) {
            return
        }
        if (param.packageName !in APP_TARGET_PACKAGES) return
        if (!hooksInstalled.compareAndSet(false, true)) return

        runCatching {
            readSettings()
            if (!anyEnabled()) {
                log(Log.INFO, TAG, "Permission hooks disabled, skip ${param.packageName}")
                return
            }
            when (param.packageName) {
                PermissionManagerConfig.securityPermissionPackage ->
                    hookSecurityPermission(param.classLoader)

                PermissionManagerConfig.settingsPackage ->
                    hookSettings(param.classLoader)

                PermissionManagerConfig.permissionControllerPackage ->
                    hookPermissionController(param.classLoader)
            }
            log(Log.INFO, TAG, "Hooks installed for ${param.packageName}")
        }.onFailure {
            hooksInstalled.set(false)
            log(Log.ERROR, TAG, "Failed to install permission hooks for ${param.packageName}", it)
        }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        if (!systemHooksInstalled.compareAndSet(false, true)) return

        runCatching {
            readSettings()
            if (!disableMalicious) {
                log(Log.INFO, TAG, "System permission hooks disabled, skip system_server")
                return
            }
            hookSystemServer(param.classLoader)
            log(Log.INFO, TAG, "System hooks installed for system_server")
        }.onFailure {
            systemHooksInstalled.set(false)
            log(Log.ERROR, TAG, "Failed to install system permission hooks", it)
        }
    }

    private fun readSettings() {
        runCatching {
            val settings = PermissionManagerSettings(
                getRemotePreferences(PermissionManagerConfig.remotePrefsGroup)
            )
            oldStartDialog = settings.isOldStartDialogEnabled()
            alwaysAllow = settings.isAlwaysAllowEnabled()
            unlockRestricted = settings.isUnlockRestrictedEnabled()
            disableMalicious = settings.isDisableMaliciousEnabled()
            exportPages = settings.isExportPagesEnabled()
        }.onFailure {
            log(Log.WARN, TAG, "Unable to read permission settings, keep defaults", it)
        }
    }

    private fun anyEnabled(): Boolean =
        oldStartDialog || alwaysAllow || unlockRestricted || disableMalicious || exportPages

    // ---------------------------------------------------------------------
    // com.oplus.securitypermission
    // ---------------------------------------------------------------------

    private fun hookSecurityPermission(classLoader: ClassLoader) {
        if (oldStartDialog) hookOldStartDialog(classLoader)
        if (alwaysAllow) {
            hookAlwaysAllowButton(classLoader)
            hookPermanentWhitelist(classLoader)
        }
        if (unlockRestricted) hookRestrictedSettings(classLoader)
    }

    private fun hookOldStartDialog(classLoader: ClassLoader) {
        val target = findClass(
            PermissionManagerConfig.startDialogActivityClassName,
            classLoader
        ) ?: return logMissing("AppStartConfirmDialogActivity")

        var count = 0
        listOf("onCreate", "onNewIntent").forEach { methodName ->
            target.declaredMethods.filter { it.name == methodName }.forEach { method ->
                runCatching {
                    hook(method).intercept { chain ->
                        val activity = chain.thisObject as? Activity
                        val intent = chain.getArg(0) as? Intent ?: activity?.intent
                        intent?.putExtra(PermissionManagerConfig.startDialogVersionExtra, 0)
                        chain.proceed()
                    }
                    count++
                }.onFailure { log(Log.WARN, TAG, "Unable to hook $methodName", it) }
            }
        }
        log(Log.INFO, TAG, "Old app-start dialog: $count hooks")
    }

    private fun hookAlwaysAllowButton(classLoader: ClassLoader) {
        val listenerType = DialogInterface.OnClickListener::class.java
        val signature = arrayOf(Int::class.javaPrimitiveType, listenerType, Boolean::class.javaPrimitiveType)
        var count = 0

        PermissionManagerConfig.dialogBuilderClassNames
            .mapNotNull { findClass(it, classLoader) }
            .distinct()
            .forEach { target ->
                target.declaredMethods
                    .filter { it.parameterTypes.contentEquals(signature) }
                    .forEach { method ->
                        runCatching {
                            hook(method).intercept { chain ->
                                val builder = chain.thisObject
                                val context = runCatching {
                                    builder.javaClass.getMethod("getContext").invoke(builder) as? Context
                                }.getOrNull()
                                if (context?.packageName !=
                                    PermissionManagerConfig.securityPermissionPackage
                                ) {
                                    return@intercept chain.proceed()
                                }
                                val resources = context.resources
                                val allow30 = resources.getIdentifier(
                                    PermissionManagerConfig.allow30StringName,
                                    "string",
                                    PermissionManagerConfig.securityPermissionPackage
                                )
                                val always = resources.getIdentifier(
                                    PermissionManagerConfig.alwaysAllowStringName,
                                    "string",
                                    PermissionManagerConfig.securityPermissionPackage
                                )
                                if (allow30 == 0 || always == 0) return@intercept chain.proceed()
                                val args = chain.getArgs().toMutableList()
                                if (args.getOrNull(0) != allow30) return@intercept chain.proceed()
                                args[0] = always
                                chain.proceed(args.toTypedArray())
                            }
                            count++
                        }.onFailure {
                            log(Log.WARN, TAG, "Unable to hook ${target.name}#${method.name}", it)
                        }
                    }
            }
        log(Log.INFO, TAG, "Always-allow button: $count hooks")
    }

    private fun hookPermanentWhitelist(classLoader: ClassLoader) {
        var count = 0
        PermissionManagerConfig.whitelistClassNames
            .mapNotNull { findClass(it, classLoader) }
            .distinct()
            .forEach { target ->
                val hasServiceField = target.declaredFields.any {
                    it.type.name == "android.os.ISecurityPermissionService"
                }
                target.declaredMethods
                    .filter { method ->
                        method.parameterTypes.contentEquals(arrayOf(Bundle::class.java)) &&
                            (method.name == PermissionManagerConfig.putWhitelistMethodName ||
                                (hasServiceField &&
                                    method.name == PermissionManagerConfig.obfuscatedWhitelistMethodName))
                    }
                    .forEach { method ->
                        runCatching {
                            hook(method).intercept { chain ->
                                val original = chain.getArg(0) as? Bundle
                                    ?: return@intercept chain.proceed()
                                val srcDst = original.getStringArrayList(
                                    PermissionManagerConfig.whitelistSrcDstKey
                                )
                                if (srcDst?.size != 2 || !original.containsKey(
                                        PermissionManagerConfig.whitelistValidTimeKey
                                    )
                                ) {
                                    return@intercept chain.proceed()
                                }
                                val cleaned = Bundle(original).apply {
                                    remove(PermissionManagerConfig.whitelistValidTimeKey)
                                }
                                val args = chain.getArgs().toMutableList()
                                args[0] = cleaned
                                chain.proceed(args.toTypedArray())
                            }
                            count++
                        }.onFailure {
                            log(Log.WARN, TAG, "Unable to hook ${target.name}#${method.name}", it)
                        }
                    }
            }
        log(Log.INFO, TAG, "Permanent allow whitelist: $count hooks")
    }

    private fun hookRestrictedSettings(classLoader: ClassLoader) {
        val target = findClass(
            PermissionManagerConfig.permissionGroupsActivityClassName,
            classLoader
        ) ?: return logMissing("PermissionGroupsActivity")

        var count = 0
        target.declaredMethods.filter { it.name == "onCreate" }.forEach { method ->
            runCatching {
                hook(method).intercept { chain ->
                    runCatching {
                        val activity = chain.thisObject as? Activity ?: return@runCatching
                        val intent = activity.intent ?: return@runCatching
                        val packageName = intent.getStringExtra("packageName")
                            ?: intent.getStringExtra("mPackageName")
                        if (!packageName.isNullOrBlank()) {
                            @Suppress("DEPRECATION")
                            val user = intent.getParcelableExtra<UserHandle>(Intent.EXTRA_USER)
                            val context = if (user == null) {
                                activity
                            } else {
                                createContextAsUser(activity, user)
                            }
                            unlockRestriction(context, packageName, classLoader)
                        }
                    }.onFailure {
                        log(Log.WARN, TAG, "Unable to clear restricted settings", it)
                    }
                    chain.proceed()
                }
                count++
            }.onFailure { log(Log.WARN, TAG, "Unable to hook onCreate", it) }
        }
        log(Log.INFO, TAG, "Restricted settings unlock: $count hooks")
    }

    private fun unlockRestriction(context: Context, packageName: String, classLoader: ClassLoader) {
        val flags = findClass(
            "com.android.internal.hidden_from_bootclasspath.android.permission.flags.Flags",
            classLoader
        )
        val ecmEnabled = flags != null && runCatching {
            flags.getDeclaredMethod("enhancedConfirmationModeApisEnabled").apply {
                isAccessible = true
            }.invoke(null) == true
        }.getOrDefault(false)

        if (ecmEnabled) {
            val manager = context.getSystemService("ecm_enhanced_confirmation") ?: return
            val managerClass = manager.javaClass
            val restricted = runCatching {
                managerClass.getMethod("isRestricted", String::class.java, String::class.java)
                    .invoke(manager, packageName, "android:bind_accessibility_service") == true
            }.getOrDefault(false)
            val clearAllowed = runCatching {
                managerClass.getMethod("isClearRestrictionAllowed", String::class.java)
                    .invoke(manager, packageName) == true
            }.getOrDefault(false)
            if (!restricted && !clearAllowed) return
            if (!clearAllowed) {
                runCatching {
                    managerClass.getMethod("setClearRestrictionAllowed", String::class.java)
                        .invoke(manager, packageName)
                }
            }
            runCatching {
                managerClass.getMethod("clearRestriction", String::class.java)
                    .invoke(manager, packageName)
            }
        } else {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return
            @Suppress("DEPRECATION")
            val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
            val op = AppOpsManager::class.java
                .getField("OP_ACCESS_RESTRICTED_SETTINGS").getInt(null)
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow("android:access_restricted_settings", uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow("android:access_restricted_settings", uid, packageName)
            }
            if (mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT) return
            runCatching {
                AppOpsManager::class.java.getMethod(
                    "setMode",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    Int::class.javaPrimitiveType
                ).invoke(appOps, op, uid, packageName, AppOpsManager.MODE_ALLOWED)
            }.onFailure {
                log(Log.WARN, TAG, "Unable to set restricted-settings app-op", it)
            }
        }
        log(Log.INFO, TAG, "Cleared restriction for $packageName")
    }

    // ---------------------------------------------------------------------
    // system_server
    // ---------------------------------------------------------------------

    private fun hookSystemServer(classLoader: ClassLoader) {
        if (!disableMalicious) return
        val target = findClass(PermissionManagerConfig.maliciousManagerClassName, classLoader)
            ?: return logMissing("OplusAppStartConfirmManager")

        var count = 0
        target.declaredMethods
            .filter {
                it.name == PermissionManagerConfig.maliciousCheckMethodName &&
                    it.returnType.name == "android.util.Pair"
            }
            .forEach { method ->
                runCatching {
                    hook(method).intercept { chain ->
                        // 返回 null 表示没有恶意拦截，系统会继续正常的启动确认流程。
                        if (disableMalicious) null else chain.proceed()
                    }
                    count++
                }.onFailure { log(Log.WARN, TAG, "Unable to hook malicious check", it) }
            }
        log(Log.INFO, TAG, "Malicious app-start intercept: $count hooks")
    }

    // ---------------------------------------------------------------------
    // com.android.settings
    // ---------------------------------------------------------------------

    private fun hookSettings(classLoader: ClassLoader) {
        if (!exportPages) return
        hookBooleanGate(
            classLoader,
            PermissionManagerConfig.settingsFeatureUtilsClassName,
            PermissionManagerConfig.settingsExpVersionMethodName
        )
        hookBooleanGate(
            classLoader,
            PermissionManagerConfig.settingsAppInfoFeatureClassName,
            PermissionManagerConfig.settingsAndroidFunctionMethodName
        )
        deoptimizeMethods(
            classLoader,
            "com.oplus.settings.feature.privacy.AppPermissionJumpPreferenceController",
            "displayPreference",
            "handleClick"
        )
    }

    // ---------------------------------------------------------------------
    // com.android.permissioncontroller
    // ---------------------------------------------------------------------

    private fun hookPermissionController(classLoader: ClassLoader) {
        if (!exportPages) return
        PermissionManagerConfig.permissionControllerGateClassNames
            .zip(PermissionManagerConfig.permissionControllerGateMethodNames)
            .forEach { (name, methodName) ->
                hookBooleanGate(classLoader, name, methodName)
            }
        listOf(
            "com.android.permissioncontroller.permission.ui.ManagePermissionsActivity" to "onCreate",
            "com.oplusos.permissioncontroller.permission.ui.ManagePermissionsActivityTrampoline" to "onCreate",
            "com.android.permissioncontroller.permission.ui.ReviewOngoingUsageActivity" to "onCreate",
            "com.oplusos.permissioncontroller.permission.ui.handheld.AppPermissionFragment" to "onCreate"
        ).forEach { (className, methodName) ->
            deoptimizeMethods(classLoader, className, methodName)
        }
    }

    private fun hookBooleanGate(classLoader: ClassLoader, className: String, methodName: String) {
        val target = findClass(className, classLoader) ?: return logMissing(className)
        var count = 0
        target.declaredMethods
            .filter {
                it.name == methodName &&
                    it.returnType == Boolean::class.javaPrimitiveType &&
                    it.parameterCount == 0
            }
            .forEach { method ->
                runCatching {
                    hook(method).intercept { chain ->
                        if (exportPages) true else chain.proceed()
                    }
                    count++
                }.onFailure { log(Log.WARN, TAG, "Unable to hook $className#$methodName", it) }
            }
        log(Log.INFO, TAG, "$className#$methodName: $count hooks")
    }

    private fun deoptimizeMethods(classLoader: ClassLoader, className: String, vararg names: String) {
        val target = findClass(className, classLoader) ?: return
        target.declaredMethods
            .filter { it.name in names }
            .forEach { deoptimizeQuietly(it) }
    }

    private fun deoptimizeQuietly(executable: Executable) {
        runCatching { deoptimize(executable) }
            .onFailure { log(Log.WARN, TAG, "Unable to deoptimize $executable", it) }
    }

    private fun createContextAsUser(context: Context, user: UserHandle): Context =
        Context::class.java.getMethod(
            "createContextAsUser",
            UserHandle::class.java,
            Int::class.javaPrimitiveType
        ).invoke(context, user, 0) as Context

    private fun findClass(name: String, classLoader: ClassLoader): Class<*>? =
        runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    private fun logMissing(name: String) {
        log(Log.WARN, TAG, "$name not found, skip")
    }

    companion object {
        private const val TAG = "NocosPermission"

        private val APP_TARGET_PACKAGES = setOf(
            PermissionManagerConfig.securityPermissionPackage,
            PermissionManagerConfig.settingsPackage,
            PermissionManagerConfig.permissionControllerPackage
        )
    }
}
