package com.xixi.nocos.hook

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.xixi.nocos.common.LauncherMenuConfig
import com.xixi.nocos.data.LauncherMenuSettings
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 桌面模块。
 *
 * 一、二级菜单平铺：
 * 长按桌面图标时，COUIPopupListWindowContainer.populateAndShow(View, PopupMenuData)
 * 会用 PopupMenuData.getSystemShortcutsIn() 里的系统快捷方式构建弹窗列表。
 * 其中 OplusBaseSystemShortcut$MordFunctions 是一个可展开项（「更多功能」），
 * 它的子项在 getMoreSystemShortcutList() 里，点击后才显示成二级列表。
 * 这里在弹窗构建前，把 MordFunctions 替换成它的子项，
 * 让原本的二级列表直接平铺在一级菜单里，不再需要点「更多功能」。
 *
 * 二、隐藏最近任务底部的「清理」按键：
 * OplusClearAllPanelView 在 onFinishInflate 里绑定 btn_clear，
 * 之后 setAlpha 会在面板可见时把按钮重新设为 VISIBLE。
 * 因此在 onFinishInflate 和 setAlpha 之后都把按钮设为 GONE。
 */
class LauncherMenuModule : XposedModule() {

    private val hooksInstalled = AtomicBoolean(false)

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != LauncherMenuConfig.targetPackageName) return
        if (!hooksInstalled.compareAndSet(false, true)) return

        runCatching {
            val settings = readSettings()
            if (!settings.flattenMenu && !settings.hideClearAll) {
                log(Log.INFO, TAG, "Launcher hooks disabled, skip ${param.packageName}")
                return
            }
            installHooks(param.classLoader, settings.flattenMenu, settings.hideClearAll)
            log(
                Log.INFO,
                TAG,
                "Hooks installed for ${param.packageName} " +
                    "(flattenMenu=${settings.flattenMenu}, hideClearAll=${settings.hideClearAll})"
            )
        }.onFailure {
            hooksInstalled.set(false)
            log(Log.ERROR, TAG, "Failed to install hooks for ${param.packageName}", it)
        }
    }

    private data class Settings(val flattenMenu: Boolean, val hideClearAll: Boolean)

    private fun readSettings(): Settings {
        return runCatching {
            val settings = LauncherMenuSettings(
                getRemotePreferences(LauncherMenuConfig.remotePrefsGroup)
            )
            Settings(settings.isEnabled(), settings.isHideClearAllEnabled())
        }.getOrDefault(
            Settings(
                LauncherMenuConfig.defaultEnabled,
                LauncherMenuConfig.defaultHideClearAll
            )
        )
    }

    private fun installHooks(
        classLoader: ClassLoader,
        flattenMenu: Boolean,
        hideClearAll: Boolean
    ) {
        var installed = false

        if (flattenMenu) {
            val mordFunctionsClass = runCatching {
                Class.forName(LauncherMenuConfig.mordFunctionsClassName, false, classLoader)
            }.getOrNull()
            val moreShortcutsGetter = mordFunctionsClass?.let { clazz ->
                runCatching {
                    clazz.getDeclaredMethod(LauncherMenuConfig.moreShortcutsGetterName)
                }.getOrNull()
            }

            if (mordFunctionsClass == null || moreShortcutsGetter == null) {
                log(Log.WARN, TAG, "MordFunctions#getMoreSystemShortcutList not found")
            }

            installed = hookCouiContainer(classLoader, mordFunctionsClass, moreShortcutsGetter) || installed
            installed = hookBaseContainer(classLoader, mordFunctionsClass, moreShortcutsGetter) || installed

            if (!installed) {
                log(Log.WARN, TAG, "populateAndShow not found, menu flatten unavailable")
            }
        }

        if (hideClearAll) {
            installed = hookClearAllPanel(classLoader) || installed
        }

        if (!installed) {
            error("no launcher hook installed")
        }
    }

    /** COUI 弹窗：populateAndShow(View, PopupMenuData) */
    private fun hookCouiContainer(
        classLoader: ClassLoader,
        mordFunctionsClass: Class<*>?,
        moreShortcutsGetter: Method?
    ): Boolean {
        return runCatching {
            val clazz = Class.forName(LauncherMenuConfig.couiContainerClassName, false, classLoader)
            val method = clazz.declaredMethods.firstOrNull {
                it.name == LauncherMenuConfig.populateAndShowName &&
                    it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == View::class.java
            } ?: error("COUI populateAndShow(View, PopupMenuData) not found")

            hook(method).intercept { chain ->
                flattenSystemShortcuts(
                    data = chain.getArg(1),
                    mordFunctionsClass = mordFunctionsClass,
                    moreShortcutsGetter = moreShortcutsGetter
                )
                chain.proceed()
            }
            deoptimizeIfPossible(method)
            true
        }.onFailure {
            log(Log.WARN, TAG, "COUI container hook unavailable", it)
        }.getOrDefault(false)
    }

    /** 兼容 AOSP 弹窗：populateAndShow(BubbleTextView, int, List, List) */
    private fun hookBaseContainer(
        classLoader: ClassLoader,
        mordFunctionsClass: Class<*>?,
        moreShortcutsGetter: Method?
    ): Boolean {
        return runCatching {
            val clazz = Class.forName(LauncherMenuConfig.baseContainerClassName, false, classLoader)
            val method = clazz.declaredMethods.firstOrNull {
                it.name == LauncherMenuConfig.populateAndShowName &&
                    it.parameterTypes.size == 4 &&
                    List::class.java.isAssignableFrom(it.parameterTypes[3])
            } ?: error("base populateAndShow(..., List) not found")

            hook(method).intercept { chain ->
                val shortcuts = chain.getArg(3)
                if (shortcuts is MutableList<*>) {
                    flattenList(shortcuts, mordFunctionsClass, moreShortcutsGetter)
                }
                chain.proceed()
            }
            deoptimizeIfPossible(method)
            true
        }.onFailure {
            log(Log.WARN, TAG, "base container hook unavailable", it)
        }.getOrDefault(false)
    }

    /** 隐藏最近任务底部的「清理」按键 */
    private fun hookClearAllPanel(classLoader: ClassLoader): Boolean {
        return runCatching {
            val clazz = Class.forName(LauncherMenuConfig.clearAllPanelClassName, false, classLoader)

            val onFinishInflate = clazz.getDeclaredMethod("onFinishInflate")
            hook(onFinishInflate).intercept { chain ->
                chain.proceed()
                hideClearButton(chain.getThisObject())
            }
            deoptimizeIfPossible(onFinishInflate)

            val setAlpha = clazz.getDeclaredMethod("setAlpha", Float::class.javaPrimitiveType)
            hook(setAlpha).intercept { chain ->
                chain.proceed()
                // 面板变可见时系统会把按钮重新设为 VISIBLE，这里再隐藏一次
                hideClearButton(chain.getThisObject())
            }
            deoptimizeIfPossible(setAlpha)

            true
        }.onFailure {
            log(Log.WARN, TAG, "clear-all panel hook unavailable", it)
        }.getOrDefault(false)
    }

    private fun hideClearButton(panel: Any?) {
        val view = panel as? View ?: return
        val button = findClearButton(view) ?: return
        if (button.visibility != View.GONE) {
            button.visibility = View.GONE
        }
    }

    private fun findClearButton(panel: View): View? {
        val id = runCatching {
            panel.resources.getIdentifier(
                LauncherMenuConfig.clearButtonIdName,
                "id",
                panel.context.packageName
            )
        }.getOrDefault(0)
        if (id != 0) {
            panel.findViewById<View>(id)?.let { return it }
        }
        return (panel as? ViewGroup)?.let { findPressFeedbackButton(it) }
    }

    /** 资源 id 名称被裁剪时，退回按类型查找清理按钮 */
    private fun findPressFeedbackButton(group: ViewGroup): View? {
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            if (child.javaClass.name == PRESS_FEEDBACK_BUTTON_CLASS) return child
            if (child is ViewGroup) {
                findPressFeedbackButton(child)?.let { return it }
            }
        }
        return null
    }

    /** 从 PopupMenuData 取出系统快捷方式列表并平铺 */
    private fun flattenSystemShortcuts(
        data: Any?,
        mordFunctionsClass: Class<*>?,
        moreShortcutsGetter: Method?
    ) {
        if (data == null) return
        runCatching {
            val list = data.javaClass
                .getMethod(LauncherMenuConfig.systemShortcutsGetterName)
                .invoke(data)
            if (list is MutableList<*>) {
                flattenList(list, mordFunctionsClass, moreShortcutsGetter)
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to flatten system shortcuts", it)
        }
    }

    private fun flattenList(
        list: MutableList<*>,
        mordFunctionsClass: Class<*>?,
        moreShortcutsGetter: Method?
    ) {
        if (mordFunctionsClass == null || moreShortcutsGetter == null) return

        @Suppress("UNCHECKED_CAST")
        val mutable = list as MutableList<Any>

        // 一级菜单里可能已经有应用详情、分享、应用锁等同类快捷方式，
        // 只把「更多功能」中尚未出现且已启用的项目补进来，避免重复。
        val occupiedTypes = mutable
            .filterNot { mordFunctionsClass.isInstance(it) }
            .filter { isShortcutEnabled(it) }
            .mapTo(HashSet()) { it.javaClass }

        var index = 0
        while (index < mutable.size) {
            val item = mutable[index]
            if (mordFunctionsClass.isInstance(item)) {
                val children = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    (moreShortcutsGetter.invoke(item) as? List<Any>)?.toList().orEmpty()
                }.getOrDefault(emptyList())

                mutable.removeAt(index)
                val inserted = children.filter { occupiedTypes.add(it.javaClass) }
                if (inserted.isNotEmpty()) {
                    mutable.addAll(index, inserted)
                    index += inserted.size
                }
                log(
                    Log.INFO,
                    TAG,
                    "Flattened ${inserted.size}/${children.size} more-shortcuts into first level"
                )
            } else {
                index++
            }
        }
    }

    /** 判断快捷方式当前是否可用；反射失败时按不可用处理，宁可保留重复也不丢项 */
    private fun isShortcutEnabled(shortcut: Any): Boolean {
        return runCatching {
            shortcut.javaClass.getMethod("isEnabled").invoke(shortcut) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun deoptimizeIfPossible(executable: Executable) {
        runCatching {
            if (!deoptimize(executable)) {
                log(Log.WARN, TAG, "Deoptimize returned false for $executable")
            }
        }.onFailure {
            log(Log.WARN, TAG, "Failed to deoptimize $executable", it)
        }
    }

    companion object {
        private const val TAG = "LauncherMenu"
        private const val PRESS_FEEDBACK_BUTTON_CLASS =
            "com.android.launcher.views.PressFeedbackButton"
    }
}
