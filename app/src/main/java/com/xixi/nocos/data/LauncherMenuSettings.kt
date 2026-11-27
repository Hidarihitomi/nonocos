package com.xixi.nocos.data

import android.content.SharedPreferences
import com.xixi.nocos.common.LauncherMenuConfig

class LauncherMenuSettings(private val preferences: SharedPreferences) {

    fun isEnabled(): Boolean {
        return preferences.getBoolean(LauncherMenuConfig.prefsKeyEnabled, LauncherMenuConfig.defaultEnabled)
    }

    fun setEnabled(value: Boolean): Boolean {
        return preferences.edit()
            .putBoolean(LauncherMenuConfig.prefsKeyEnabled, value)
            .commit()
    }

    fun isHideClearAllEnabled(): Boolean {
        return preferences.getBoolean(
            LauncherMenuConfig.prefsKeyHideClearAll,
            LauncherMenuConfig.defaultHideClearAll
        )
    }

    fun setHideClearAllEnabled(value: Boolean): Boolean {
        return preferences.edit()
            .putBoolean(LauncherMenuConfig.prefsKeyHideClearAll, value)
            .commit()
    }
}
