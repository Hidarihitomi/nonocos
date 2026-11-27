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
}
