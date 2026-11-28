package com.xixi.nocos.data

import android.content.SharedPreferences
import com.xixi.nocos.common.KeyboardConfig

/**
 * 小布输入法配置。
 *
 * 布尔项按 key 直接读写（莫奈、材质解锁）；滑杆数值由 hook 侧直接读取
 * RemotePreferences（存于 key_value），这里不再提供读写方法。
 */
class KeyboardSettings(private val preferences: SharedPreferences) {

    fun getBoolean(key: String, default: Boolean): Boolean =
        preferences.getBoolean(key, default)

    fun setBoolean(key: String, value: Boolean): Boolean =
        preferences.edit().putBoolean(key, value).commit()

    fun isMaterialUnlockEnabled(): Boolean =
        getBoolean(KeyboardConfig.prefsKeyMaterialUnlock, KeyboardConfig.defaultMaterialUnlock)

    fun setMaterialUnlockEnabled(value: Boolean): Boolean =
        setBoolean(KeyboardConfig.prefsKeyMaterialUnlock, value)
}
