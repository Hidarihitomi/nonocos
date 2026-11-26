package com.xixi.nocos.data

import android.os.Build

/**
 * 设备信息：系统版本与市场机型名。
 *
 * 这些值只存在于系统属性里，普通应用无法通过公开 API 读取，
 * 因此先尝试反射 SystemProperties，失败再退回到 `getprop` 命令。
 */
object DeviceInfo {

    /** 系统版本号，例如 “PKR110_17.0.0.101(CN01)”。 */
    val systemVersion: String by lazy { resolveSystemVersion() }

    /** 例如 “一加 Ace 5 Pro”。 */
    val modelName: String by lazy { resolveModelName() }

    private fun resolveSystemVersion(): String {
        val displayId = readProperty("ro.build.display.id")
        if (!displayId.isNullOrBlank()) return displayId

        val rom = readProperty("ro.build.version.oplusrom")
            ?: readProperty("ro.build.version.oneplusrom")
        val normalized = rom?.trim()?.trimStart('V', 'v')
        if (!normalized.isNullOrBlank()) return "ColorOS $normalized"

        val display = readProperty("ro.build.version.oplusrom.display")
        if (!display.isNullOrBlank()) return "ColorOS $display"

        return "Android ${Build.VERSION.RELEASE}"
    }

    private fun resolveModelName(): String {
        val marketName = readProperty("ro.vendor.oplus.market.name")
            ?: readProperty("ro.product.marketname")
            ?: readProperty("ro.oplus.market.name")
        if (!marketName.isNullOrBlank()) return marketName

        return listOfNotNull(
            Build.MANUFACTURER?.takeIf { it.isNotBlank() },
            Build.MODEL?.takeIf { it.isNotBlank() }
        ).joinToString(" ").ifBlank { "未知机型" }
    }

    private fun readProperty(key: String): String? {
        readByReflection(key)?.let { return it }
        return readByGetprop(key)
    }

    private fun readByReflection(key: String): String? {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)
            (get.invoke(null, key) as? String)?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun readByGetprop(key: String): String? {
        return runCatching {
            val process = ProcessBuilder("getprop", key).start()
            val value = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()
            value.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
