package com.xixi.nocos.data

import android.content.SharedPreferences
import com.xixi.nocos.common.ScreenshotFixConfig

class ScreenshotFixSettings(private val preferences: SharedPreferences) {

    fun isEnabled(): Boolean {
        return preferences.getBoolean(ScreenshotFixConfig.prefsKeyEnabled, ScreenshotFixConfig.defaultEnabled)
    }

    fun setEnabled(value: Boolean): Boolean {
        return preferences.edit()
            .putBoolean(ScreenshotFixConfig.prefsKeyEnabled, value)
            .commit()
    }
}
