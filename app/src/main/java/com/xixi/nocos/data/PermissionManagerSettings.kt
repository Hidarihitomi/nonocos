package com.xixi.nocos.data

import android.content.SharedPreferences
import com.xixi.nocos.common.PermissionManagerConfig

class PermissionManagerSettings(private val preferences: SharedPreferences) {

    fun isOldStartDialogEnabled(): Boolean {
        return preferences.getBoolean(
            PermissionManagerConfig.prefsKeyOldStartDialog,
            PermissionManagerConfig.defaultOldStartDialog
        )
    }

    fun setOldStartDialogEnabled(value: Boolean): Boolean {
        return preferences.edit()
            .putBoolean(PermissionManagerConfig.prefsKeyOldStartDialog, value)
            .commit()
    }

    fun isAlwaysAllowEnabled(): Boolean {
        return preferences.getBoolean(
            PermissionManagerConfig.prefsKeyAlwaysAllow,
            PermissionManagerConfig.defaultAlwaysAllow
        )
    }

    fun setAlwaysAllowEnabled(value: Boolean): Boolean {
        return preferences.edit()
            .putBoolean(PermissionManagerConfig.prefsKeyAlwaysAllow, value)
            .commit()
    }

    fun isUnlockRestrictedEnabled(): Boolean {
        return preferences.getBoolean(
            PermissionManagerConfig.prefsKeyUnlockRestricted,
            PermissionManagerConfig.defaultUnlockRestricted
        )
    }

    fun setUnlockRestrictedEnabled(value: Boolean): Boolean {
        return preferences.edit()
            .putBoolean(PermissionManagerConfig.prefsKeyUnlockRestricted, value)
            .commit()
    }

    fun isDisableMaliciousEnabled(): Boolean {
        return preferences.getBoolean(
            PermissionManagerConfig.prefsKeyDisableMalicious,
            PermissionManagerConfig.defaultDisableMalicious
        )
    }

    fun setDisableMaliciousEnabled(value: Boolean): Boolean {
        return preferences.edit()
            .putBoolean(PermissionManagerConfig.prefsKeyDisableMalicious, value)
            .commit()
    }

    fun isExportPagesEnabled(): Boolean {
        return preferences.getBoolean(
            PermissionManagerConfig.prefsKeyExportPages,
            PermissionManagerConfig.defaultExportPages
        )
    }

    fun setExportPagesEnabled(value: Boolean): Boolean {
        return preferences.edit()
            .putBoolean(PermissionManagerConfig.prefsKeyExportPages, value)
            .commit()
    }
}
