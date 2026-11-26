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
import com.xixi.nocos.common.LauncherMenuConfig
import com.xixi.nocos.common.ModuleId
import com.xixi.nocos.common.ScreenshotFixConfig
import com.xixi.nocos.common.SpoofConfig
import com.xixi.nocos.common.ThemeVipConfig
import com.xixi.nocos.data.LauncherMenuSettings
import com.xixi.nocos.data.ScreenshotFixSettings
import com.xixi.nocos.data.SpoofSettings
import com.xixi.nocos.data.ThemeVipSettings
import com.xixi.nocos.data.XposedServiceBridge
import com.xixi.nocos.ui.screen.CosaSpoofScreen
import com.xixi.nocos.ui.screen.HomeScreen
import com.xixi.nocos.ui.screen.LauncherMenuScreen
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

    private var serviceStatusState by mutableStateOf(ServiceStatus.WAITING)
    private var currentFakeValueState by mutableStateOf(SpoofConfig.defaultFakePrjname)
    private var inputValueState by mutableStateOf(SpoofConfig.defaultFakePrjname)
    private var lastSyncedFakeValueState by mutableStateOf(SpoofConfig.defaultFakePrjname)

    private var screenshotFixEnabledState by mutableStateOf(ScreenshotFixConfig.defaultEnabled)
    private var isSavingScreenshotFixState by mutableStateOf(false)
    private var launcherMenuEnabledState by mutableStateOf(LauncherMenuConfig.defaultEnabled)
    private var launcherHideClearAllState by mutableStateOf(LauncherMenuConfig.defaultHideClearAll)
    private var isSavingLauncherMenuState by mutableStateOf(false)
    private var isSavingLauncherHideClearAllState by mutableStateOf(false)
    private var themeVipEnabledState by mutableStateOf(ThemeVipConfig.defaultEnabled)
    private var isSavingThemeVipState by mutableStateOf(false)
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
            val appColors = remember(isDark) { if (isDark) AppColors.Dark else AppColors.Light }
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
                            themeVipEnabledState
                        } else {
                            null
                        },
                        onOpenModule = { selectedModuleState = it }
                    )
                } else {
                    BackHandler { selectedModuleState = null }
                    when (module) {
                        ModuleId.COSA_SPOOF -> CosaSpoofScreen(
                            colors = appColors,
                            serviceStatus = serviceStatusState,
                            currentValue = currentFakeValueState,
                            inputValue = inputValueState,
                            onInputValueChange = { inputValueState = it },
                            onSaveClicked = {
                                saveFakePrjname(inputValueState)
                            },
                            onBack = { selectedModuleState = null }
                        )

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
                            isSaving = isSavingThemeVipState,
                            onToggle = { setThemeVipEnabled(it) },
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
            serviceStatusState = ServiceStatus.WAITING
            currentFakeValueState = SpoofConfig.defaultFakePrjname
            screenshotFixEnabledState = ScreenshotFixConfig.defaultEnabled
            launcherMenuEnabledState = LauncherMenuConfig.defaultEnabled
            launcherHideClearAllState = LauncherMenuConfig.defaultHideClearAll
            themeVipEnabledState = ThemeVipConfig.defaultEnabled
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
            spoofSettings = settings
            screenshotFixSettings = screenshotSettings
            launcherMenuSettings = launcherSettings
            themeVipSettings = themeSettings
            serviceStatusState = ServiceStatus.CONNECTED
            val currentValue = settings.getFakePrjname()
            currentFakeValueState = currentValue ?: SpoofConfig.defaultFakePrjname
            if (inputValueState.isBlank() || inputValueState == lastSyncedFakeValueState) {
                inputValueState = currentFakeValueState
            }
            lastSyncedFakeValueState = currentFakeValueState
            screenshotFixEnabledState = screenshotSettings.isEnabled()
            launcherMenuEnabledState = launcherSettings.isEnabled()
            launcherHideClearAllState = launcherSettings.isHideClearAllEnabled()
            themeVipEnabledState = themeSettings.isEnabled()
        }.onFailure {
            spoofSettings = null
            screenshotFixSettings = null
            launcherMenuSettings = null
            themeVipSettings = null
            serviceStatusState = ServiceStatus.ERROR
            currentFakeValueState = SpoofConfig.defaultFakePrjname
            screenshotFixEnabledState = ScreenshotFixConfig.defaultEnabled
            launcherMenuEnabledState = LauncherMenuConfig.defaultEnabled
            launcherHideClearAllState = LauncherMenuConfig.defaultHideClearAll
            themeVipEnabledState = ThemeVipConfig.defaultEnabled
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
}
