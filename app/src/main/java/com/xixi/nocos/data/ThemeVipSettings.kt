package com.xixi.nocos.data

import android.content.SharedPreferences
import com.xixi.nocos.common.ThemeVipConfig

class ThemeVipSettings(private val preferences: SharedPreferences) {

    fun isEnabled(): Boolean {
        return preferences.getBoolean(ThemeVipConfig.prefsKeyEnabled, ThemeVipConfig.defaultEnabled)
    }

    fun setEnabled(value: Boolean): Boolean {
        return preferences.edit()
            .putBoolean(ThemeVipConfig.prefsKeyEnabled, value)
            .commit()
    }

    fun isHideAdsEnabled(): Boolean {
        return preferences.getBoolean(ThemeVipConfig.prefsKeyHideAds, ThemeVipConfig.defaultHideAds)
    }

    fun setHideAdsEnabled(value: Boolean): Boolean {
        return preferences.edit()
            .putBoolean(ThemeVipConfig.prefsKeyHideAds, value)
            .commit()
    }

    fun isNightResourcesEnabled(): Boolean {
        return preferences.getBoolean(
            ThemeVipConfig.prefsKeyNightResources,
            ThemeVipConfig.defaultNightResources
        )
    }

    fun setNightResourcesEnabled(value: Boolean): Boolean {
        return preferences.edit()
            .putBoolean(ThemeVipConfig.prefsKeyNightResources, value)
            .commit()
    }
}
