package com.xixi.nocos.data

import android.content.SharedPreferences
import com.xixi.nocos.common.GameStartAnimationConfig

class GameStartAnimationSettings(private val preferences: SharedPreferences) {

    fun isEnabled(): Boolean {
        return preferences.getBoolean(
            GameStartAnimationConfig.prefsKeyEnabled,
            GameStartAnimationConfig.defaultEnabled
        )
    }

    fun setEnabled(value: Boolean): Boolean {
        return preferences.edit()
            .putBoolean(GameStartAnimationConfig.prefsKeyEnabled, value)
            .commit()
    }
}
