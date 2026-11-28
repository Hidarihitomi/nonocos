package com.xixi.nocos.hook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import com.xixi.nocos.common.ThemeVipConfig
import com.xixi.nocos.data.ThemeVipSettings
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Executable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 主题商店：VIP 解锁、去广告、深色模式资源。
 *
 * 移植自 ONextBox 的 ThemeStoreHooker / ThemeStoreAdsHooker / ThemeNightResourcesHook：
 *   - VIP：固定 4 个 DTO getter 的返回值，并跳过试用过期广播
 *   - 去广告：开屏、首页弹窗、推广弹窗、广告 getter 与广告卡片过滤
 *   - 深色模式：主题资源被系统拒绝时放行已应用主题
 */
class ThemeVipModule : XposedModule() {

    private val hooksInstalled = AtomicBoolean(false)
    private val reported = ConcurrentHashMap.newKeySet<String>()

    private val expireActions = setOf(
        "THEME_TRIAL_EXPIRE", "FONT_TRIAL_EXPIRE", "LIVE_WP_TRIAL_EXPIRE",
        "SKU_SYSTEMUI_TRIAL_EXPIRE", "SKU_LOCKSCREEN_TRIAL_EXPIRE", "RESOURCE_EXPIRE", "VIP_EXPIRE",
    ).mapTo(hashSetOf()) { "com.nearme.themespace.action.$it" }

    private val adTypes = setOf(
        "com.oppo.cdo.card.theme.dto.ad.AdCardDto",
        "com.oppo.cdo.card.theme.dto.ad.template.TemplateAdCardDTO",
        "com.oppo.cdo.card.theme.dto.SearchBannerAdCardDto",
        "com.oppo.cdo.card.theme.dto.GoodsAdCardDto",
    )

    private val adCardGetters = mapOf(
        "page.HomePageDto" to "getCards",
        "page.ViewLayerWrapDto" to "getCards",
        "AssembleCardDto" to "getCards",
        "RichMultiCardDto" to "getCards",
        "MultiTabCardDto" to "getTabs",
        "MultiTabCardDtoV2" to "getTabs",
        "RankListCardDto" to "getRankCards",
        "TopicListCardDto" to "getTopicList",
        "TopicListV2CardDto" to "getTopicList",
        "TabItemCardDto" to "getItems",
        "TabItemCardDtoV2" to "getItems",
    )

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != ThemeVipConfig.targetPackageName || !param.isFirstPackage) return
        if (!hooksInstalled.compareAndSet(false, true)) return

        runCatching {
            val settings = readSettings()
            if (!settings.vip && !settings.ads && !settings.night) {
                log(Log.INFO, TAG, "Theme store hooks disabled, skip ${param.packageName}")
                return
            }

            val loader = param.classLoader
            if (settings.vip) installVipHooks(loader)
            if (settings.ads) installAdsHooks(loader)
            if (settings.night) installNightHooks(loader)
            log(
                Log.INFO,
                TAG,
                "Theme store hooks installed " +
                    "(vip=${settings.vip}, ads=${settings.ads}, night=${settings.night})"
            )
        }.onFailure {
            hooksInstalled.set(false)
            log(Log.ERROR, TAG, "Failed to install theme store hooks", it)
        }
    }

    private data class Settings(val vip: Boolean, val ads: Boolean, val night: Boolean)

    private fun readSettings(): Settings = runCatching {
        val settings = ThemeVipSettings(getRemotePreferences(ThemeVipConfig.remotePrefsGroup))
        Settings(
            settings.isEnabled(),
            settings.isHideAdsEnabled(),
            settings.isNightResourcesEnabled(),
        )
    }.getOrDefault(Settings(false, false, false))

    // ---------------------------------------------------------------------
    // VIP
    // ---------------------------------------------------------------------

    private fun installVipHooks(loader: ClassLoader) {
        val targets = mapOf(
            "com.oppo.cdo.card.theme.dto.vip.VipUserDto" to
                mapOf("getVipStatus" to 1, "getVipDays" to 999),
            "com.oppo.cdo.card.theme.dto.page.WeatherPageResponseDto" to
                mapOf("getVipStatus" to 1),
            "com.oppo.cdo.theme.domain.dto.response.ResourceItemDto" to
                mapOf("getIsVip" to 1, "getIsVipAvailable" to 1),
            "com.oppo.cdo.theme.domain.dto.response.PublishProductItemDto" to
                mapOf("getPrice" to 0, "getIsVipAvailable" to 1),
        )

        targets.forEach { (name, getters) ->
            val type = findClassIfExists(name, loader) ?: return@forEach
            getters.forEach { (getter, value) ->
                runCatching {
                    val method = type.getDeclaredMethod(getter)
                    val replacement = numericValue(method.returnType, value)
                        ?: error("Unsupported return type: ${method.returnType.name}")
                    hook(method).intercept { replacement }
                    deoptimizeSafely(method)
                }.onFailure { log(Log.WARN, TAG, "VIP getter unavailable: $name.$getter", it) }
            }
        }

        runCatching {
            val receiver = findClassIfExists(
                "com.nearme.themespace.trial.ThemeTrialExpireReceiver",
                loader
            ) ?: return@runCatching
            val method = receiver.getDeclaredMethod(
                "onReceive",
                Context::class.java,
                Intent::class.java
            )
            hook(method).intercept { chain ->
                val action = (chain.getArg(1) as? Intent)?.action
                if (action in expireActions) {
                    report("trial-expiry")
                    null
                } else {
                    chain.proceed()
                }
            }
            deoptimizeSafely(method)
        }.onFailure { log(Log.WARN, TAG, "Trial expiry adapter unavailable", it) }
    }

    // ---------------------------------------------------------------------
    // 去广告
    // ---------------------------------------------------------------------

    private fun installAdsHooks(loader: ClassLoader) {
        hookSplash(loader)
        hookHomepagePopup(loader)
        hookPromotionPopup(loader)
        hookAdGetters(loader)
        hookAdCards(loader)
    }

    private fun hookSplash(loader: ClassLoader) {
        runCatching {
            val manager = findClassIfExists("com.nearme.themespace.ad.l", loader)
                ?: return@runCatching
            val activity = Class.forName(
                "androidx.fragment.app.FragmentActivity",
                false,
                loader
            )
            val callback = Class.forName("com.nearme.themespace.ad.h", false, loader)
            val splash = Class.forName(
                "com.oppo.cdo.card.theme.dto.SplashDto",
                false,
                loader
            )
            val handlerField = manager.declaredFields
                .single { it.type == Handler::class.java }
                .apply { isAccessible = true }
            val callbackField = manager.declaredFields
                .single { it.type == callback }
                .apply { isAccessible = true }
            val legacyEmpty = manager.declaredMethods.singleOrNull {
                it.name == "J" && it.parameterTypes.contentEquals(arrayOf(splash))
            }
            val showEmpty = (legacyEmpty ?: manager.getDeclaredMethod("I", splash))
                .apply { isAccessible = true }
            // 复用应用自身的生命周期 Runnable 与消息分发，避免向宿主 Handler 注入模块回调。
            val lifecycleTask = manager.declaredClasses
                .filter { Runnable::class.java.isAssignableFrom(it) }
                .flatMap { it.declaredConstructors.toList() }
                .single { it.parameterTypes.contentEquals(arrayOf(manager, activity)) }
                .apply { isAccessible = true }
            val request = manager.getDeclaredMethod(
                "a",
                activity,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
            val show = manager.getDeclaredMethod(if (legacyEmpty == null) "H" else "I", splash)

            hook(show).intercept { chain ->
                val host = chain.getThisObject() ?: return@intercept chain.proceed()
                val handler = handlerField.get(host) as? Handler
                    ?: return@intercept chain.proceed()
                if (callbackField.get(host) == null) return@intercept chain.proceed()
                if (Looper.myLooper() == handler.looper) {
                    handler.removeMessages(4)
                    // null DTO 走原生无广告分支：结束统计并通知开屏持有者。
                    showEmpty.invoke(host, null)
                } else if (!handler.sendEmptyMessage(4)) {
                    return@intercept chain.proceed()
                }
                report("splash-display")
                null
            }

            hook(request).intercept { chain ->
                val host = chain.getThisObject() ?: return@intercept chain.proceed()
                val owner = chain.getArg(0) ?: return@intercept chain.proceed()
                val handler = handlerField.get(host) as? Handler
                    ?: return@intercept chain.proceed()
                if (callbackField.get(host) == null) return@intercept chain.proceed()
                val task = lifecycleTask.newInstance(host, owner) as Runnable
                if (!handler.post(task)) return@intercept chain.proceed()
                // 消息 4 原本用于展示 SDK 或缓存广告，这里立即转入无广告完成路径。
                if (!handler.sendEmptyMessage(4)) return@intercept chain.proceed()
                report("splash-request")
                null
            }

            deoptimizeClass(manager)
            findClassIfExists("com.nearme.themespace.ad.f", loader)?.let(::deoptimizeClass)
            findClassIfExists(
                "com.nearme.themespace.activities.ThemeActivity\$k",
                loader
            )?.let(::deoptimizeClass)
        }.onFailure { log(Log.WARN, TAG, "Splash ad hook unavailable", it) }
    }

    private fun hookHomepagePopup(loader: ClassLoader) {
        runCatching {
            val type = findClassIfExists("com.nearme.themespace.fragments.i7", loader)
                ?: return@runCatching
            val activity = Class.forName(
                "androidx.fragment.app.FragmentActivity",
                false,
                loader
            )
            val finish = type.getDeclaredMethod("A1", Activity::class.java)
                .apply { isAccessible = true }
            val start = type.getDeclaredMethod(
                "C1",
                activity,
                String::class.java,
                String::class.java,
            )
            hook(start).intercept { chain ->
                // 弹窗占用启动队列槽位，走原生空数据路径释放队列并继续首页任务。
                finish.invoke(null, chain.getArg(0))
                report("homepage-popup")
                null
            }
            deoptimizeClass(type)
            findClassIfExists("sn.b", loader)?.let(::deoptimizeClass)
        }.onFailure { log(Log.WARN, TAG, "Homepage popup hook unavailable", it) }
    }

    private fun hookPromotionPopup(loader: ClassLoader) {
        runCatching {
            val type = sequenceOf("lo.a\$c", "xq.a\$c")
                .mapNotNull { findClassIfExists(it, loader) }
                .firstOrNull() ?: return@runCatching
            val dto = Class.forName(
                "com.oppo.cdo.theme.domain.dto.response.PromotionPopupDto",
                false,
                loader
            )
            val method = type.getDeclaredMethod("b", dto)
            hook(method).intercept { chain ->
                val args = chain.args.toTypedArray()
                if (args[0] != null) {
                    // 置空后继续走原生无效数据分支，保留支付与普通弹窗逻辑。
                    args[0] = null
                    report("promotion-popup")
                }
                chain.proceed(args)
            }
            deoptimizeClass(type)
        }.onFailure { log(Log.WARN, TAG, "Promotion popup hook unavailable", it) }
    }

    private fun hookAdGetters(loader: ClassLoader) {
        fun getter(className: String, name: String, replacement: (Class<*>) -> Any?) {
            runCatching {
                val type = findClassIfExists(className, loader) ?: return@runCatching
                val method = type.getDeclaredMethod(name)
                val value = replacement(method.returnType)
                hook(method).intercept {
                    report("$className.$name")
                    value
                }
                deoptimizeSafely(method)
            }.onFailure {
                log(Log.WARN, TAG, "Ad getter unavailable: $className.$name", it)
            }
        }

        val splash = "com.oppo.cdo.card.theme.dto.SplashDto"
        getter(splash, "getAdData") {
            check(!it.isPrimitive)
            null
        }
        getter(splash, "getShowTime") {
            numericValue(it, 0) ?: error("Unsupported splash duration")
        }
        getter(splash, "getIsSkip") {
            check(it == Boolean::class.javaPrimitiveType || it == Boolean::class.javaObjectType)
            true
        }
        getter("com.oppo.cdo.card.theme.dto.page.HomePageDto", "getAdBanner") {
            check(!it.isPrimitive)
            null
        }
    }

    private fun hookAdCards(loader: ClassLoader) {
        adCardGetters.forEach { (name, getterName) ->
            runCatching {
                val type = findClassIfExists("com.oppo.cdo.card.theme.dto.$name", loader)
                    ?: return@runCatching
                val method = type.getMethod(getterName)
                check(List::class.java.isAssignableFrom(method.returnType))
                hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val source = result as? List<*> ?: return@intercept result
                    val next = source.filterNot { card ->
                        val cls = card?.javaClass ?: return@filterNot false
                        generateSequence<Class<*>>(cls) { it.superclass }
                            .any { it.name in adTypes }
                    }
                    if (next.size != source.size) report("cards:$name")
                    next
                }
                deoptimizeSafely(method)
            }.onFailure {
                log(Log.WARN, TAG, "Ad card adapter unavailable: $name", it)
            }
        }
    }

    // ---------------------------------------------------------------------
    // 深色模式资源
    // ---------------------------------------------------------------------

    private fun installNightHooks(loader: ClassLoader) {
        runCatching {
            val base = Class.forName("android.content.res.OplusBaseFile", false, loader)
            val ext = Class.forName("android.content.res.IResourcesImplExt", false, loader)
            val reject = base.getDeclaredMethod("rejectTheme", ext, String::class.java)
            val checkUpdate = Class.forName(
                "android.content.res.OplusThemeResources",
                false,
                loader
            ).getDeclaredMethod("checkUpdate")
            val defaultTheme = Class.forName(
                "com.oplus.theme.OplusThirdPartUtil",
                false,
                loader
            ).getDeclaredMethod("getDefaultTheme", Int::class.javaPrimitiveType)
            if (reject.returnType != Boolean::class.javaPrimitiveType ||
                defaultTheme.returnType != reject.returnType
            ) {
                return@runCatching
            }

            val userId = Process.myUid() / 100000
            hook(reject).intercept { chain ->
                val rejected = chain.proceed()
                if (rejected == true &&
                    runCatching { defaultTheme.invoke(null, userId) == false }
                        .getOrDefault(false)
                ) {
                    report("night-resources")
                    false
                } else {
                    rejected
                }
            }
            deoptimizeSafely(checkUpdate)
        }.onFailure {
            log(Log.WARN, TAG, "Night theme resource hook unavailable", it)
        }
    }

    // ---------------------------------------------------------------------
    // 工具
    // ---------------------------------------------------------------------

    private fun findClassIfExists(name: String, loader: ClassLoader): Class<*>? =
        runCatching { Class.forName(name, false, loader) }.getOrNull()

    private fun numericValue(type: Class<*>, value: Int): Any? = when (type) {
        Int::class.javaPrimitiveType, Int::class.javaObjectType -> value
        Long::class.javaPrimitiveType, Long::class.javaObjectType -> value.toLong()
        Float::class.javaPrimitiveType, Float::class.javaObjectType -> value.toFloat()
        Double::class.javaPrimitiveType, Double::class.javaObjectType -> value.toDouble()
        else -> null
    }

    private fun deoptimizeClass(type: Class<*>) {
        type.declaredMethods.forEach(::deoptimizeSafely)
    }

    private fun deoptimizeSafely(executable: Executable) {
        runCatching {
            if (!deoptimize(executable)) {
                log(Log.WARN, TAG, "Deoptimize returned false for $executable")
            }
        }.onFailure { log(Log.WARN, TAG, "Failed to deoptimize $executable", it) }
    }

    private fun report(stage: String) {
        if (reported.add(stage)) {
            log(Log.INFO, TAG, "Applied $stage")
        }
    }

    companion object {
        private const val TAG = "ThemeVip"
    }
}
