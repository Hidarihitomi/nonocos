package com.xixi.nocos.data

import android.content.SharedPreferences
import com.xixi.nocos.common.MultiAppConfig

class MultiAppSettings(private val preferences: SharedPreferences) {

    fun isUnlockAppsEnabled(): Boolean {
        return preferences.getBoolean(
            MultiAppConfig.prefsKeyUnlockApps,
            MultiAppConfig.defaultUnlockApps
        )
    }

    fun setUnlockAppsEnabled(value: Boolean): Boolean {
        return preferences.edit()
            .putBoolean(MultiAppConfig.prefsKeyUnlockApps, value)
            .commit()
    }

    fun isExtendedSlotsEnabled(): Boolean {
        return preferences.getBoolean(
            MultiAppConfig.prefsKeyExtendedSlots,
            MultiAppConfig.defaultExtendedSlots
        )
    }

    fun setExtendedSlotsEnabled(value: Boolean): Boolean {
        return preferences.edit()
            .putBoolean(MultiAppConfig.prefsKeyExtendedSlots, value)
            .commit()
    }
}
