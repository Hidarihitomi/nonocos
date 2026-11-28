package com.xixi.nocos.common

/**
 * 模块列表。每个模块对应一个被 Hook 的目标应用与一份独立的远程配置。
 */
enum class ModuleId(val prefsGroup: String) {
    SCREENSHOT_FIX(ScreenshotFixConfig.remotePrefsGroup),
    LAUNCHER_MENU(LauncherMenuConfig.remotePrefsGroup),
    THEME_VIP(ThemeVipConfig.remotePrefsGroup),
    GAME_START_ANIMATION(GameStartAnimationConfig.remotePrefsGroup),
    KEYBOARD(KeyboardConfig.remotePrefsGroup),
    PERMISSION_MANAGER(PermissionManagerConfig.remotePrefsGroup),
    MULTI_APP(MultiAppConfig.remotePrefsGroup)
}

/**
 * 截屏区域修复配置常量
 */
object ScreenshotFixConfig {

    const val targetPackageName: String = "com.oplus.screenshot"

    const val remotePrefsGroup: String = "screenshot_fix"
    const val prefsKeyEnabled: String = "enabled"
    const val defaultEnabled: Boolean = false

    /**
     * 语义稳定的入口：截屏 17.7.10 / 17.9.0 中均未被混淆。
     * 松手时把 EDITOR_SCREENSHOT 改成 EDITOR_AREA_SCREENSHOT，直接进入框选模式。
     */
    const val anchorClassName: String = "com.oplus.screenshot.area.widget.AreaGestureScreenshotView"
    const val anchorMethodName: String = "startEdit"
    const val editorUiTypeClassName: String = "com.oplus.screenshot.ui.editor.EditorUIType"
    const val areaEditorTypeField: String = "EDITOR_AREA_SCREENSHOT"
    const val screenshotEditorTypeField: String = "EDITOR_SCREENSHOT"

    /** 旧版兜底：AreaPressInfo.getMaxMoveDistance() 的混淆名（17.7.10 / 17.9.0 为 e4.b$a.b） */
    const val legacyClassName: String = "e4.b\$a"
    const val legacyMethodName: String = "b"
}

/**
 * 桌面二级菜单配置常量。
 *
 * 桌面 17.3.12 的长按弹窗是 COUI 弹窗列表：
 *   - 一级菜单：深度快捷方式 + 卸载等系统快捷方式 + 一个可展开的「更多功能」项
 *   - 二级菜单：「更多功能」项携带的子项（应用信息、分享、编辑、隐私锁等）
 * 本模块在弹窗构建前，把「更多功能」项替换成它的子项，使所有项目平铺在一级。
 */
object LauncherMenuConfig {

    const val targetPackageName: String = "com.android.launcher"

    const val remotePrefsGroup: String = "launcher_menu"
    const val prefsKeyEnabled: String = "enabled"
    const val defaultEnabled: Boolean = false

    /** 隐藏最近任务底部的「清理」按键 */
    const val prefsKeyHideClearAll: String = "hide_clear_all"
    const val defaultHideClearAll: Boolean = false

    /** 最近任务清理面板：hook 它的 onFinishInflate/setAlpha 隐藏「清理」按键 */
    const val clearAllPanelClassName: String = "com.oplus.quickstep.views.OplusClearAllPanelView"
    const val clearButtonIdName: String = "btn_clear"

    const val couiContainerClassName: String =
        "com.android.launcher3.popup.view.COUIPopupListWindowContainer"
    const val baseContainerClassName: String =
        "com.android.launcher3.popup.view.BasePopupListWindowContainer"

    /** 携带二级列表的「更多功能」项 */
    const val mordFunctionsClassName: String =
        "com.android.launcher3.popup.OplusBaseSystemShortcut\$MordFunctions"
    const val moreShortcutsGetterName: String = "getMoreSystemShortcutList"

    const val populateAndShowName: String = "populateAndShow"
    const val systemShortcutsGetterName: String = "getSystemShortcutsIn"
}

/**
 * 主题商店 VIP 配置常量。
 *
 * 主题商店 17.16.0 通过三个 DTO 方法判断 VIP 状态与商品权限：
 *   VipUserDto#getVipStatus()I
 *   VipUserDto#getVipDays()I
 *   PublishProductItemDto#getIsVipAvailable()I
 * 这里统一改成固定返回值。
 */
object ThemeVipConfig {

    const val targetPackageName: String = "com.heytap.themestore"

    const val remotePrefsGroup: String = "theme_vip"
    const val prefsKeyEnabled: String = "enabled"
    const val defaultEnabled: Boolean = false

    const val prefsKeyHideAds: String = "hide_ads"
    const val defaultHideAds: Boolean = false
}

/**
 * 游戏助手启动动画（风驰游戏内核弹窗）配置。
 *
 * 游戏助手 11.0.4 的启动动画由 newarch 能力 startup_animation 负责：
 *   isSupported(...) 返回 true 后，initialize(...) 会加载
 *   start_anime_page_oneplus*.webp 并显示「一加风驰游戏内核」卡片。
 *
 * 混淆后的实现类名（如 capabilities.d）每次发版都可能变化，因此不把类名当作
 * 唯一依据：优先按 featureId 字符串动态定位实现类，失败时才退回已知类名。
 */
object GameStartAnimationConfig {

    const val targetPackageName: String = "com.oplus.games"

    const val remotePrefsGroup: String = "game_start_animation"
    const val prefsKeyEnabled: String = "enabled"
    const val defaultEnabled: Boolean = false

    /** 语义稳定：newarch 能力 ID，源码中的字符串常量，混淆不会改写。 */
    const val featureId: String = "startup_animation"

    /** 实现类所在包；R8 未做 repackage，包名可跨版本沿用。 */
    const val featurePackagePrefix: String =
        "com.oplus.games.arch.newarch.capabilities."

    /** 11.0.4 实测类名，作为动态发现失败时的快速路径。 */
    const val legacyFeatureClassName: String =
        "com.oplus.games.arch.newarch.capabilities.d"

    const val featureMethodName: String = "isSupported"
    const val featureIdMethodName: String = "getFeatureId"

    /** 动画资源加载辅助类所在包；类名会变，包名保留。 */
    const val animationPackagePrefix: String =
        "business.secondarypanel.start.anime."

    /** 11.0.4 实测类名，作为动态发现失败时的快速路径。 */
    const val legacyAnimationHelperClassName: String =
        "business.secondarypanel.start.anime.a"

    const val animationLoadMethodName: String = "m"


}

/**
 * 小布输入法（com.oplus.keyboard）配置。
 *
 * 对齐 ONextBox 的小布输入法页：布局修复、材质解锁、旗帜 Emoji，
 * 以及 ColorOS 17 的键盘美化（配色、模糊、工具栏、按键、角标）。
 */
object KeyboardConfig {

    const val targetPackageName: String = "com.oplus.keyboard"

    const val remotePrefsGroup: String = "keyboard"

    /** 材质 */
    const val prefsKeyMaterialUnlock: String = "material_unlock"
    const val defaultMaterialUnlock: Boolean = false

    /** 配色 */
    const val prefsKeyMonet: String = "monet"
    const val defaultMonet: Boolean = false

    /** 材质（模糊） */
    const val prefsKeyBlurRadius: String = "blur_radius"
    const val defaultBlurRadius: Int = 150
    const val minBlurRadius: Int = 0
    const val maxBlurRadius: Int = 250
    const val prefsKeyBlurOpacity: String = "blur_opacity"
    const val defaultBlurOpacity: Int = 14
    const val minBlurOpacity: Int = 0
    const val maxBlurOpacity: Int = 100
    const val prefsKeyBlurCorner: String = "blur_corner"
    const val defaultBlurCorner: Int = 30
    const val minBlurCorner: Int = 0
    const val maxBlurCorner: Int = 100

    /** 工具栏 */
    const val prefsKeyToolbarIconAlpha: String = "toolbar_icon_alpha"
    const val defaultToolbarIconAlpha: Int = 100
    const val prefsKeyToolbarBgAlpha: String = "toolbar_bg_alpha"
    const val defaultToolbarBgAlpha: Int = 63

    /** 按键 */
    const val prefsKeyKeyCorner: String = "key_corner"
    const val defaultKeyCorner: Int = 100
    const val prefsKeyKeyAlpha: String = "key_alpha"
    const val defaultKeyAlpha: Int = 100
    const val prefsKeyKeyFontScale: String = "key_font_scale"
    const val defaultKeyFontScale: Int = 100
    const val minKeyFontScale: Int = 50
    const val maxKeyFontScale: Int = 150

    data class NumberOption(
        val key: String,
        val default: Int,
        val min: Int,
        val max: Int,
        val unit: String
    )

    val booleanKeys: List<String> = listOf(
        prefsKeyMonet,
        prefsKeyMaterialUnlock
    )

    val numberOptions: List<NumberOption> = listOf(
        NumberOption(prefsKeyBlurRadius, defaultBlurRadius, minBlurRadius, maxBlurRadius, "px"),
        NumberOption(prefsKeyBlurOpacity, defaultBlurOpacity, minBlurOpacity, maxBlurOpacity, "%"),
        NumberOption(prefsKeyBlurCorner, defaultBlurCorner, minBlurCorner, maxBlurCorner, "%"),
        NumberOption(prefsKeyToolbarIconAlpha, defaultToolbarIconAlpha, 0, 100, "%"),
        NumberOption(prefsKeyToolbarBgAlpha, defaultToolbarBgAlpha, 0, 100, "%"),
        NumberOption(prefsKeyKeyCorner, defaultKeyCorner, 0, 100, "%"),
        NumberOption(prefsKeyKeyAlpha, defaultKeyAlpha, 0, 100, "%"),
        NumberOption(prefsKeyKeyFontScale, defaultKeyFontScale, minKeyFontScale, maxKeyFontScale, "%")
    )

}

/**
 * 权限管理配置。
 *
 * 涉及三个应用进程和一个系统进程：
 *   - com.oplus.securitypermission：应用启动确认弹窗、永久允许、受限设置
 *   - com.android.settings：权限页面入口
 *   - com.android.permissioncontroller：国际版权限页 / 原生权限弹窗
 *   - system：恶意应用启动拦截
 */
object PermissionManagerConfig {

    const val remotePrefsGroup: String = "permission_manager"

    const val securityPermissionPackage: String = "com.oplus.securitypermission"
    const val settingsPackage: String = "com.android.settings"
    const val permissionControllerPackage: String = "com.android.permissioncontroller"
    const val systemPackage: String = "system"

    /** 旧版应用启动确认弹窗 */
    const val prefsKeyOldStartDialog: String = "old_start_dialog"
    const val defaultOldStartDialog: Boolean = false

    /** 把「允许 30 天」改成「始终允许」 */
    const val prefsKeyAlwaysAllow: String = "always_allow"
    const val defaultAlwaysAllow: Boolean = false

    /** 打开权限管理页时自动解除受限设置 */
    const val prefsKeyUnlockRestricted: String = "unlock_restricted"
    const val defaultUnlockRestricted: Boolean = false

    /** 跳过恶意应用启动拦截 */
    const val prefsKeyDisableMalicious: String = "disable_malicious"
    const val defaultDisableMalicious: Boolean = false

    /** 国际版权限页面 */
    const val prefsKeyExportPages: String = "export_pages"
    const val defaultExportPages: Boolean = false

    const val startDialogActivityClassName: String =
        "com.oplusos.securitypermission.permission.ui.AppStartConfirmDialogActivity"
    const val startDialogVersionExtra: String = "activity_start_confirm_version"

    val dialogBuilderClassNames: List<String> = listOf(
        "com.coui.appcompat.dialog.COUIAlertDialogBuilder",
        "com.coui.appcompat.dialog.e",
        "b2.b"
    )
    const val allow30StringName: String = "app_start_dialog_allow_30"
    const val alwaysAllowStringName: String = "app_start_dialog_always_allow"

    val whitelistClassNames: List<String> = listOf(
        "android.content.pm.OplusPermissionManager",
        "com.oplusos.securitypermission.permission.utils.OplusPermissionManager",
        "s9.d",
        "r8.c"
    )
    const val putWhitelistMethodName: String = "putActivityStartWhiteList"
    const val obfuscatedWhitelistMethodName: String = "d"
    const val whitelistSrcDstKey: String = "src_and_dst"
    const val whitelistValidTimeKey: String = "valid_time"

    const val permissionGroupsActivityClassName: String =
        "com.oplusos.securitypermission.permission.PermissionGroupsActivity"

    const val maliciousManagerClassName: String =
        "com.android.server.wm.OplusAppStartConfirmManager"
    const val maliciousCheckMethodName: String = "checkMaliciousIntercept"

    const val settingsFeatureUtilsClassName: String =
        "com.oplus.settings.utils.CustomizeFeatureUtils"
    const val settingsExpVersionMethodName: String = "isExpVersion"
    const val settingsAppInfoFeatureClassName: String =
        "com.oplus.settings.feature.appmanager.AppInfoFeature"
    const val settingsAndroidFunctionMethodName: String = "getIsAndroidFuction"

    val permissionControllerGateClassNames: List<String> = listOf("j8.a", "ka.a")
    val permissionControllerGateMethodNames: List<String> = listOf("g", "f")
}

/**
 * 应用分身配置。
 *
 * 系统侧（system_server）与分身应用（com.oplus.multiapp）各有一份白名单和
 * 数量上限，两边都要放开，分身列表和实际可创建数量才会同时生效。
 */
object MultiAppConfig {

    const val targetPackageName: String = "com.oplus.multiapp"
    const val systemPackage: String = "system"

    const val remotePrefsGroup: String = "multi_app"

    /** 解除可分身应用限制 */
    const val prefsKeyUnlockApps: String = "unlock_apps"
    const val defaultUnlockApps: Boolean = false

    /** 分身数量上限提高到 10 */
    const val prefsKeyExtendedSlots: String = "extended_slots"
    const val defaultExtendedSlots: Boolean = false

    const val extendedSlotCount: Int = 10

    /** 不做分身处理的系统包 */
    val excludedPackages: Set<String> = setOf(
        "android",
        "com.xixi.nocos",
        "com.android.systemui",
        "com.android.launcher",
        "com.android.settings",
        "com.oplus.multiapp",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller"
    )
}
