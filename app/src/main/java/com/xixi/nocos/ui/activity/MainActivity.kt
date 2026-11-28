package com.xixi.nocos.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import com.xixi.nocos.R
import com.xixi.nocos.common.GameStartAnimationConfig
import com.xixi.nocos.common.KeyboardConfig
import com.xixi.nocos.common.LauncherMenuConfig
import com.xixi.nocos.common.ModuleId
import com.xixi.nocos.common.MultiAppConfig
import com.xixi.nocos.common.PermissionManagerConfig
import com.xixi.nocos.common.ScreenshotFixConfig
import com.xixi.nocos.common.SpoofConfig
import com.xixi.nocos.common.ThemeVipConfig
import com.xixi.nocos.data.GameStartAnimationSettings
import com.xixi.nocos.data.KeyboardSettings
import com.xixi.nocos.data.LauncherMenuSettings
import com.xixi.nocos.data.MultiAppSettings
import com.xixi.nocos.data.PermissionManagerSettings
import com.xixi.nocos.data.ScreenshotFixSettings
import com.xixi.nocos.data.SpoofSettings
import com.xixi.nocos.data.ThemeVipSettings
import com.xixi.nocos.data.XposedServiceBridge
import com.xixi.nocos.ui.screen.GameStartAnimationScreen
import com.xixi.nocos.ui.screen.HomeScreen
import com.xixi.nocos.ui.screen.KeyboardScreen
import com.xixi.nocos.ui.screen.LauncherMenuScreen
import com.xixi.nocos.ui.screen.MultiAppScreen
import com.xixi.nocos.ui.screen.PermissionManagerScreen
import com.xixi.nocos.ui.screen.ScreenshotFixScreen
import com.xixi.nocos.ui.screen.ThemeVipScreen
import com.xixi.nocos.ui.theme.AppColors
import com.xixi.nocos.ui.theme.AppTheme
import com.xixi.nocos.ui.theme.ServiceStatus
import io.github.libxposed.service.XposedService

class MainActivity : ComponentActivity() {

    private var spoofSettings: SpoofSettings? = null
    private var screenshotFixSettings: ScreenshotFixSettings? = null
    private var launcherMenuSettings: LauncherMenuSettings? = null
    private var themeVipSettings: ThemeVipSettings? = null
    private var gameStartAnimationSettings: GameStartAnimationSettings? = null
    private var keyboardSettings: KeyboardSettings? = null
    private var permissionManagerSettings: PermissionManagerSettings? = null
    private var multiAppSettings: MultiAppSettings? = null

    private var serviceStatusState by mutableStateOf(ServiceStatus.WAITING)
    private var currentFakeValueState by mutableStateOf(SpoofConfig.defaultFakePrjname)
    private var inputValueState by mutableStateOf(SpoofConfig.defaultFakePrjname)
    private var lastSyncedFakeValueState by mutableStateOf(SpoofConfig.defaultFakePrjname)
    private var spoofEnabledState by mutableStateOf(SpoofConfig.defaultEnabled)
    private var isSavingSpoofState by mutableStateOf(false)

    private var screenshotFixEnabledState by mutableStateOf(ScreenshotFixConfig.defaultEnabled)
    private var isSavingScreenshotFixState by mutableStateOf(false)
    private var launcherMenuEnabledState by mutableStateOf(LauncherMenuConfig.defaultEnabled)
    private var launcherHideClearAllState by mutableStateOf(LauncherMenuConfig.defaultHideClearAll)
    private var isSavingLauncherMenuState by mutableStateOf(false)
    private var isSavingLauncherHideClearAllState by mutableStateOf(false)
    private var themeVipEnabledState by mutableStateOf(ThemeVipConfig.defaultEnabled)
    private var themeVipHideAdsState by mutableStateOf(ThemeVipConfig.defaultHideAds)
    private var isSavingThemeVipState by mutableStateOf(false)
    private var isSavingThemeVipHideAdsState by mutableStateOf(false)
    private var gameStartAnimationEnabledState by mutableStateOf(GameStartAnimationConfig.defaultEnabled)
    private var isSavingGameStartAnimationState by mutableStateOf(false)
    private var keyboardBooleansState by mutableStateOf<Map<String, Boolean>>(emptyMap())
    private var isSavingKeyboardState by mutableStateOf(false)
    private var permissionOldStartDialogState by mutableStateOf(PermissionManagerConfig.defaultOldStartDialog)
    private var permissionAlwaysAllowState by mutableStateOf(PermissionManagerConfig.defaultAlwaysAllow)
    private var permissionUnlockRestrictedState by mutableStateOf(PermissionManagerConfig.defaultUnlockRestricted)
    private var permissionDisableMaliciousState by mutableStateOf(PermissionManagerConfig.defaultDisableMalicious)
    private var permissionExportPagesState by mutableStateOf(PermissionManagerConfig.defaultExportPages)
    private var isSavingPermissionState by mutableStateOf(false)
    private var multiAppUnlockState by mutableStateOf(MultiAppConfig.defaultUnlockApps)
    private var multiAppSlotsState by mutableStateOf(MultiAppConfig.defaultExtendedSlots)
    private var isSavingMultiAppState by mutableStateOf(false)
    private var selectedModuleState by mutableStateOf<ModuleId?>(null)

    private val serviceListener = object : XposedServiceBridge.Listener {
        override fun onServiceChanged(service: XposedService?) {
            runOnUiThread { bindServiceState(service) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val isDark = isSystemInDarkTheme()
            val appColors = remember(isDark) {
                if (isDark) AppColors.Dark else AppColors.Light
            }
            AppTheme(appColors) {
                val selectedModule = selectedModuleState
                AnimatedContent(
                    targetState = selectedModule,
                    transitionSpec = {
                        val enteringDetail = initialState == null && targetState != null
                        if (enteringDetail) {
                            (slideInHorizontally(tween(280)) { it } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut(tween(160)))
                        } else {
                            (slideInHorizontally(tween(280)) { -it / 4 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(280)) { it } + fadeOut(tween(160)))
                        }
                    },
                    label = "module_transition"
                ) { module ->
                if (module == null) {
                    HomeScreen(
                        colors = appColors,
                        serviceStatus = serviceStatusState,
                        screenshotEnabled = if (serviceStatusState == ServiceStatus.CONNECTED) {
                            screenshotFixEnabledState
                        } else {
                            null
                        },
                        launcherMenuEnabled = if (serviceStatusState == ServiceStatus.CONNECTED) {
                            launcherMenuEnabledState || launcherHideClearAllState
                        } else {
                            null
                        },
                        themeVipEnabled = if (serviceStatusState == ServiceStatus.CONNECTED) {
                            themeVipEnabledState || themeVipHideAdsState
                        } else {
                            null
                        },
                        gameStartAnimationEnabled = if (serviceStatusState == ServiceStatus.CONNECTED) {
                            gameStartAnimationEnabledState
                        } else {
                            null
                        },
                        keyboardEnabled = if (serviceStatusState == ServiceStatus.CONNECTED) {
                            keyboardBooleansState.values.any { it }
                        } else {
                            null
                        },
                        permissionEnabled = if (serviceStatusState == ServiceStatus.CONNECTED) {
                            permissionOldStartDialogState || permissionAlwaysAllowState ||
                                permissionUnlockRestrictedState || permissionDisableMaliciousState ||
                                permissionExportPagesState
                        } else {
                            null
                        },
                        multiAppEnabled = if (serviceStatusState == ServiceStatus.CONNECTED) {
                            multiAppUnlockState || multiAppSlotsState
                        } else {
                            null
                        },
                        onOpenModule = { selectedModuleState = it }
                    )
                } else {
                    BackHandler { selectedModuleState = null }
                    when (module) {
                        ModuleId.SCREENSHOT_FIX -> ScreenshotFixScreen(
                            colors = appColors,
                            serviceStatus = serviceStatusState,
                            enabled = screenshotFixEnabledState,
                            isSaving = isSavingScreenshotFixState,
                            onToggle = { setScreenshotFixEnabled(it) },
                            onBack = { selectedModuleState = null }
                        )

                        ModuleId.LAUNCHER_MENU -> LauncherMenuScreen(
                            colors = appColors,
                            serviceStatus = serviceStatusState,
                            enabled = launcherMenuEnabledState,
                            hideClearAll = launcherHideClearAllState,
                            isSaving = isSavingLauncherMenuState,
                            isSavingHideClearAll = isSavingLauncherHideClearAllState,
                            onToggle = { setLauncherMenuEnabled(it) },
                            onToggleHideClearAll = { setLauncherHideClearAll(it) },
                            onBack = { selectedModuleState = null }
                        )

                        ModuleId.THEME_VIP -> ThemeVipScreen(
                            colors = appColors,
                            serviceStatus = serviceStatusState,
                            enabled = themeVipEnabledState,
                            hideAds = themeVipHideAdsState,
                            isSaving = isSavingThemeVipState,
                            isSavingHideAds = isSavingThemeVipHideAdsState,
                            onToggle = { setThemeVipEnabled(it) },
                            onToggleHideAds = { setThemeVipHideAds(it) },
                            onBack = { selectedModuleState = null }
                        )

                        ModuleId.GAME_START_ANIMATION -> GameStartAnimationScreen(
                            colors = appColors,
                            serviceStatus = serviceStatusState,
                            enabled = gameStartAnimationEnabledState,
                            isSaving = isSavingGameStartAnimationState,
                            onToggle = { setGameStartAnimationEnabled(it) },
                            spoofEnabled = spoofEnabledState,
                            isSavingSpoof = isSavingSpoofState,
                            onSpoofToggle = { setSpoofEnabled(it) },
                            spoofCurrentValue = currentFakeValueState,
                            spoofInputValue = inputValueState,
                            onSpoofInputChange = { inputValueState = it },
                            onSpoofSave = { saveFakePrjname(inputValueState) },
                            onBack = { selectedModuleState = null }
                        )

                        ModuleId.KEYBOARD -> KeyboardScreen(
                            colors = appColors,
                            serviceStatus = serviceStatusState,
                            booleans = keyboardBooleansState,
                            isSaving = isSavingKeyboardState,
                            onToggle = { key, value -> setKeyboardBoolean(key, value) },
                            onBack = { selectedModuleState = null }
                        )

                        ModuleId.PERMISSION_MANAGER -> PermissionManagerScreen(
                            colors = appColors,
                            serviceStatus = serviceStatusState,
                            oldStartDialog = permissionOldStartDialogState,
                            alwaysAllow = permissionAlwaysAllowState,
                            unlockRestricted = permissionUnlockRestrictedState,
                            disableMalicious = permissionDisableMaliciousState,
                            exportPages = permissionExportPagesState,
                            isSaving = isSavingPermissionState,
                            onToggleOldStartDialog = { setPermissionOldStartDialog(it) },
                            onToggleAlwaysAllow = { setPermissionAlwaysAllow(it) },
                            onToggleUnlockRestricted = { setPermissionUnlockRestricted(it) },
                            onToggleDisableMalicious = { setPermissionDisableMalicious(it) },
                            onToggleExportPages = { setPermissionExportPages(it) },
                            onBack = { selectedModuleState = null }
                        )

                        ModuleId.MULTI_APP -> MultiAppScreen(
                            colors = appColors,
                            serviceStatus = serviceStatusState,
                            unlockApps = multiAppUnlockState,
                            extendedSlots = multiAppSlotsState,
                            isSaving = isSavingMultiAppState,
                            onToggleUnlockApps = { setMultiAppUnlock(it) },
                            onToggleExtendedSlots = { setMultiAppSlots(it) },
                            onBack = { selectedModuleState = null }
                        )
                    }
                }
                }
            }
        }

        XposedServiceBridge.addListener(serviceListener)
    }

    override fun onDestroy() {
        XposedServiceBridge.removeListener(serviceListener)
        super.onDestroy()
    }

    private fun bindServiceState(service: XposedService?) {
        if (service == null) {
            spoofSettings = null
            screenshotFixSettings = null
            launcherMenuSettings = null
            themeVipSettings = null
            gameStartAnimationSettings = null
            keyboardSettings = null
            permissionManagerSettings = null
            multiAppSettings = null
            serviceStatusState = ServiceStatus.WAITING
            currentFakeValueState = SpoofConfig.defaultFakePrjname
            spoofEnabledState = SpoofConfig.defaultEnabled
            screenshotFixEnabledState = ScreenshotFixConfig.defaultEnabled
            launcherMenuEnabledState = LauncherMenuConfig.defaultEnabled
            launcherHideClearAllState = LauncherMenuConfig.defaultHideClearAll
            themeVipEnabledState = ThemeVipConfig.defaultEnabled
            themeVipHideAdsState = ThemeVipConfig.defaultHideAds
            gameStartAnimationEnabledState = GameStartAnimationConfig.defaultEnabled
            keyboardBooleansState = emptyMap()
            permissionOldStartDialogState = PermissionManagerConfig.defaultOldStartDialog
            permissionAlwaysAllowState = PermissionManagerConfig.defaultAlwaysAllow
            permissionUnlockRestrictedState = PermissionManagerConfig.defaultUnlockRestricted
            permissionDisableMaliciousState = PermissionManagerConfig.defaultDisableMalicious
            permissionExportPagesState = PermissionManagerConfig.defaultExportPages
            multiAppUnlockState = MultiAppConfig.defaultUnlockApps
            multiAppSlotsState = MultiAppConfig.defaultExtendedSlots
            return
        }

        runCatching {
            val settings = SpoofSettings(service.getRemotePreferences(SpoofConfig.remotePrefsGroup))
            val screenshotSettings = ScreenshotFixSettings(
                service.getRemotePreferences(ScreenshotFixConfig.remotePrefsGroup)
            )
            val launcherSettings = LauncherMenuSettings(
                service.getRemotePreferences(LauncherMenuConfig.remotePrefsGroup)
            )
            val themeSettings = ThemeVipSettings(
                service.getRemotePreferences(ThemeVipConfig.remotePrefsGroup)
            )
            val gameStartSettings = GameStartAnimationSettings(
                service.getRemotePreferences(GameStartAnimationConfig.remotePrefsGroup)
            )
            val keyboardPrefs = KeyboardSettings(
                service.getRemotePreferences(KeyboardConfig.remotePrefsGroup)
            )
            val permissionPrefs = PermissionManagerSettings(
                service.getRemotePreferences(PermissionManagerConfig.remotePrefsGroup)
            )
            val multiAppPrefs = MultiAppSettings(
                service.getRemotePreferences(MultiAppConfig.remotePrefsGroup)
            )
            spoofSettings = settings
            screenshotFixSettings = screenshotSettings
            launcherMenuSettings = launcherSettings
            themeVipSettings = themeSettings
            gameStartAnimationSettings = gameStartSettings
            keyboardSettings = keyboardPrefs
            permissionManagerSettings = permissionPrefs
            multiAppSettings = multiAppPrefs
            serviceStatusState = ServiceStatus.CONNECTED
            val currentValue = settings.getFakePrjname()
            currentFakeValueState = currentValue ?: SpoofConfig.defaultFakePrjname
            spoofEnabledState = settings.isEnabled()
            if (inputValueState.isBlank() || inputValueState == lastSyncedFakeValueState) {
                inputValueState = currentFakeValueState
            }
            lastSyncedFakeValueState = currentFakeValueState
            screenshotFixEnabledState = screenshotSettings.isEnabled()
            launcherMenuEnabledState = launcherSettings.isEnabled()
            launcherHideClearAllState = launcherSettings.isHideClearAllEnabled()
            themeVipEnabledState = themeSettings.isEnabled()
            themeVipHideAdsState = themeSettings.isHideAdsEnabled()
            gameStartAnimationEnabledState = gameStartSettings.isEnabled()
            keyboardBooleansState = KeyboardConfig.booleanKeys.associateWith {
                keyboardPrefs.getBoolean(it, defaultKeyboardBoolean(it))
            }
            permissionOldStartDialogState = permissionPrefs.isOldStartDialogEnabled()
            permissionAlwaysAllowState = permissionPrefs.isAlwaysAllowEnabled()
            permissionUnlockRestrictedState = permissionPrefs.isUnlockRestrictedEnabled()
            permissionDisableMaliciousState = permissionPrefs.isDisableMaliciousEnabled()
            permissionExportPagesState = permissionPrefs.isExportPagesEnabled()
            multiAppUnlockState = multiAppPrefs.isUnlockAppsEnabled()
            multiAppSlotsState = multiAppPrefs.isExtendedSlotsEnabled()
        }.onFailure {
            spoofSettings = null
            screenshotFixSettings = null
            launcherMenuSettings = null
            themeVipSettings = null
            gameStartAnimationSettings = null
            keyboardSettings = null
            permissionManagerSettings = null
            multiAppSettings = null
            serviceStatusState = ServiceStatus.ERROR
            currentFakeValueState = SpoofConfig.defaultFakePrjname
            spoofEnabledState = SpoofConfig.defaultEnabled
            screenshotFixEnabledState = ScreenshotFixConfig.defaultEnabled
            launcherMenuEnabledState = LauncherMenuConfig.defaultEnabled
            launcherHideClearAllState = LauncherMenuConfig.defaultHideClearAll
            themeVipEnabledState = ThemeVipConfig.defaultEnabled
            themeVipHideAdsState = ThemeVipConfig.defaultHideAds
            gameStartAnimationEnabledState = GameStartAnimationConfig.defaultEnabled
            keyboardBooleansState = emptyMap()
            permissionOldStartDialogState = PermissionManagerConfig.defaultOldStartDialog
            permissionAlwaysAllowState = PermissionManagerConfig.defaultAlwaysAllow
            permissionUnlockRestrictedState = PermissionManagerConfig.defaultUnlockRestricted
            permissionDisableMaliciousState = PermissionManagerConfig.defaultDisableMalicious
            permissionExportPagesState = PermissionManagerConfig.defaultExportPages
            multiAppUnlockState = MultiAppConfig.defaultUnlockApps
            multiAppSlotsState = MultiAppConfig.defaultExtendedSlots
        }
    }

    private fun saveFakePrjname(newValue: String) {
        val settings = spoofSettings
        if (settings == null) {
            Toast.makeText(this, R.string.service_unavailable_tip, Toast.LENGTH_SHORT).show()
            return
        }

        val success = settings.setFakePrjname(newValue)
        if (!success) {
            Toast.makeText(this, R.string.spoof_prjname_save_failed_tip, Toast.LENGTH_SHORT).show()
            return
        }

        currentFakeValueState = settings.getFakePrjname() ?: SpoofConfig.defaultFakePrjname
        inputValueState = currentFakeValueState
        lastSyncedFakeValueState = currentFakeValueState
        Toast.makeText(this, R.string.spoof_prjname_saved_toast, Toast.LENGTH_SHORT).show()
    }

    private fun setSpoofEnabled(enabled: Boolean) {
        if (isSavingSpoofState) return

        val settings = spoofSettings
        if (settings == null) {
            Toast.makeText(this, R.string.service_unavailable_tip, Toast.LENGTH_SHORT).show()
            return
        }

        isSavingSpoofState = true
        val success = settings.setEnabled(enabled)
        isSavingSpoofState = false
        if (success) {
            spoofEnabledState = settings.isEnabled()
            Toast.makeText(this, R.string.spoof_enable_saved_tip, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.spoof_enable_save_failed_tip, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setScreenshotFixEnabled(enabled: Boolean) {
        if (isSavingScreenshotFixState) return

        val settings = screenshotFixSettings
        if (settings == null) {
            Toast.makeText(this, R.string.service_unavailable_tip, Toast.LENGTH_SHORT).show()
            return
        }

        isSavingScreenshotFixState = true
        val success = settings.setEnabled(enabled)
        isSavingScreenshotFixState = false
        if (success) {
            screenshotFixEnabledState = settings.isEnabled()
            Toast.makeText(this, R.string.screenshot_fix_saved_tip, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.screenshot_fix_save_failed_tip, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setLauncherMenuEnabled(enabled: Boolean) {
        if (isSavingLauncherMenuState) return

        val settings = launcherMenuSettings
        if (settings == null) {
            Toast.makeText(this, R.string.service_unavailable_tip, Toast.LENGTH_SHORT).show()
            return
        }

        isSavingLauncherMenuState = true
        val success = settings.setEnabled(enabled)
        isSavingLauncherMenuState = false
        if (success) {
            launcherMenuEnabledState = settings.isEnabled()
            Toast.makeText(this, R.string.launcher_menu_saved_tip, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.launcher_menu_save_failed_tip, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setLauncherHideClearAll(enabled: Boolean) {
        if (isSavingLauncherHideClearAllState) return

        val settings = launcherMenuSettings
        if (settings == null) {
            Toast.makeText(this, R.string.service_unavailable_tip, Toast.LENGTH_SHORT).show()
            return
        }

        isSavingLauncherHideClearAllState = true
        val success = settings.setHideClearAllEnabled(enabled)
        isSavingLauncherHideClearAllState = false
        if (success) {
            launcherHideClearAllState = settings.isHideClearAllEnabled()
            Toast.makeText(this, R.string.launcher_menu_saved_tip, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.launcher_menu_save_failed_tip, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setThemeVipEnabled(enabled: Boolean) {
        if (isSavingThemeVipState) return

        val settings = themeVipSettings
        if (settings == null) {
            Toast.makeText(this, R.string.service_unavailable_tip, Toast.LENGTH_SHORT).show()
            return
        }

        isSavingThemeVipState = true
        val success = settings.setEnabled(enabled)
        isSavingThemeVipState = false
        if (success) {
            themeVipEnabledState = settings.isEnabled()
            Toast.makeText(this, R.string.theme_vip_saved_tip, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.theme_vip_save_failed_tip, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setThemeVipHideAds(enabled: Boolean) {
        if (isSavingThemeVipHideAdsState) return

        val settings = themeVipSettings
        if (settings == null) {
            Toast.makeText(this, R.string.service_unavailable_tip, Toast.LENGTH_SHORT).show()
            return
        }

        isSavingThemeVipHideAdsState = true
        val success = settings.setHideAdsEnabled(enabled)
        isSavingThemeVipHideAdsState = false
        if (success) {
            themeVipHideAdsState = settings.isHideAdsEnabled()
            Toast.makeText(this, R.string.theme_vip_saved_tip, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.theme_vip_save_failed_tip, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setGameStartAnimationEnabled(enabled: Boolean) {
        if (isSavingGameStartAnimationState) return

        val settings = gameStartAnimationSettings
        if (settings == null) {
            Toast.makeText(this, R.string.service_unavailable_tip, Toast.LENGTH_SHORT).show()
            return
        }

        isSavingGameStartAnimationState = true
        val success = settings.setEnabled(enabled)
        isSavingGameStartAnimationState = false
        if (success) {
            gameStartAnimationEnabledState = settings.isEnabled()
            Toast.makeText(this, R.string.game_start_animation_saved_tip, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                R.string.game_start_animation_save_failed_tip,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setKeyboardBoolean(key: String, enabled: Boolean) {
        if (isSavingKeyboardState) return

        val settings = keyboardSettings
        if (settings == null) {
            Toast.makeText(this, R.string.service_unavailable_tip, Toast.LENGTH_SHORT).show()
            return
        }

        isSavingKeyboardState = true
        val success = settings.setBoolean(key, enabled)
        isSavingKeyboardState = false
        if (success) {
            keyboardBooleansState = keyboardBooleansState + (key to settings.getBoolean(key, enabled))
            Toast.makeText(this, R.string.keyboard_saved_tip, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.keyboard_save_failed_tip, Toast.LENGTH_SHORT).show()
        }
    }

    private fun defaultKeyboardBoolean(key: String): Boolean = when (key) {
        KeyboardConfig.prefsKeyMaterialUnlock -> KeyboardConfig.defaultMaterialUnlock
        KeyboardConfig.prefsKeyMonet -> KeyboardConfig.defaultMonet
        else -> false
    }

    private fun setPermissionOldStartDialog(enabled: Boolean) {
        updatePermissionSetting(
            { it.setOldStartDialogEnabled(enabled) },
            { permissionOldStartDialogState = it.isOldStartDialogEnabled() }
        )
    }

    private fun setPermissionAlwaysAllow(enabled: Boolean) {
        updatePermissionSetting(
            { it.setAlwaysAllowEnabled(enabled) },
            { permissionAlwaysAllowState = it.isAlwaysAllowEnabled() }
        )
    }

    private fun setPermissionUnlockRestricted(enabled: Boolean) {
        updatePermissionSetting(
            { it.setUnlockRestrictedEnabled(enabled) },
            { permissionUnlockRestrictedState = it.isUnlockRestrictedEnabled() }
        )
    }

    private fun setPermissionDisableMalicious(enabled: Boolean) {
        updatePermissionSetting(
            { it.setDisableMaliciousEnabled(enabled) },
            { permissionDisableMaliciousState = it.isDisableMaliciousEnabled() }
        )
    }

    private fun setPermissionExportPages(enabled: Boolean) {
        updatePermissionSetting(
            { it.setExportPagesEnabled(enabled) },
            { permissionExportPagesState = it.isExportPagesEnabled() }
        )
    }

    private fun updatePermissionSetting(
        write: (PermissionManagerSettings) -> Boolean,
        read: (PermissionManagerSettings) -> Unit
    ) {
        if (isSavingPermissionState) return

        val settings = permissionManagerSettings
        if (settings == null) {
            Toast.makeText(this, R.string.service_unavailable_tip, Toast.LENGTH_SHORT).show()
            return
        }

        isSavingPermissionState = true
        val success = write(settings)
        isSavingPermissionState = false
        if (success) {
            read(settings)
            Toast.makeText(this, R.string.permission_saved_tip, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.permission_save_failed_tip, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setMultiAppUnlock(enabled: Boolean) {
        updateMultiAppSetting(
            { it.setUnlockAppsEnabled(enabled) },
            { multiAppUnlockState = it.isUnlockAppsEnabled() }
        )
    }

    private fun setMultiAppSlots(enabled: Boolean) {
        updateMultiAppSetting(
            { it.setExtendedSlotsEnabled(enabled) },
            { multiAppSlotsState = it.isExtendedSlotsEnabled() }
        )
    }

    private fun updateMultiAppSetting(
        write: (MultiAppSettings) -> Boolean,
        read: (MultiAppSettings) -> Unit
    ) {
        if (isSavingMultiAppState) return

        val settings = multiAppSettings
        if (settings == null) {
            Toast.makeText(this, R.string.service_unavailable_tip, Toast.LENGTH_SHORT).show()
            return
        }

        isSavingMultiAppState = true
        val success = write(settings)
        isSavingMultiAppState = false
        if (success) {
            read(settings)
            Toast.makeText(this, R.string.multi_app_saved_tip, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.multi_app_save_failed_tip, Toast.LENGTH_SHORT).show()
        }
    }
}
