package com.xixi.nocos.common

/**
 * 模块列表。每个模块对应一个被 Hook 的目标应用与一份独立的远程配置。
 */
enum class ModuleId(val prefsGroup: String) {
    COSA_SPOOF(SpoofConfig.remotePrefsGroup),
    SCREENSHOT_FIX(ScreenshotFixConfig.remotePrefsGroup),
    LAUNCHER_MENU(LauncherMenuConfig.remotePrefsGroup),
    THEME_VIP(ThemeVipConfig.remotePrefsGroup)
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

    const val vipUserDtoClassName: String = "com.oppo.cdo.card.theme.dto.vip.VipUserDto"
    const val publishProductItemDtoClassName: String =
        "com.oppo.cdo.theme.domain.dto.response.PublishProductItemDto"

    const val vipStatusMethodName: String = "getVipStatus"
    const val vipDaysMethodName: String = "getVipDays"
    const val vipAvailableMethodName: String = "getIsVipAvailable"

    const val fakeVipStatus: Int = 1
    const val fakeVipDays: Int = 100
    const val fakeVipAvailable: Int = 1
}
