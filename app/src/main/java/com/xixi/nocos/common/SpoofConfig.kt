package com.xixi.nocos.common

/**
 * 伪装配置常量
 */
object SpoofConfig {

    const val targetPackageName: String = "com.oplus.cosa"

    const val systemPropertiesClassName: String = "android.os.SystemProperties"
    const val prjnamePropertyKey: String = "ro.boot.prjname"

    const val defaultFakePrjname: String = "24831"
    const val remotePrefsGroup: String = "cosa_spoofer"
    const val prefsKeyFakePrjname: String = "fake_prjname"

    /** 伪装总开关，默认关闭。 */
    const val prefsKeyEnabled: String = "enabled"
    const val defaultEnabled: Boolean = false
}
