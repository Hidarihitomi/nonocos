package com.xixi.nocos.hook

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.TransitionDrawable
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import com.xixi.nocos.common.KeyboardConfig
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.IdentityHashMap
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * ColorOS 17 键盘美化，完整对齐 ONextBox 的 KeyboardAppearanceHooker：
 *   - 主题取色（整数 / ColorStateList / Drawable）
 *   - 模糊半径、透明度、圆角
 *   - 工具栏图标透明度、背景透明度、描边、去阴影
 *   - 按键背景、圆角、透明度、描边、阴影、文字缩放
 *   - T9 / QWERTY 角标隐藏与居中
 *
 * ONextBox 使用 DexKit 动态定位；这里按小布输入法 1.9.25.17 的真实类名定位，
 * 每个 Hook 都有存在性保护，版本变化时只记录日志，不影响键盘本身。
 */
internal class KeyboardAppearance(
    private val module: XposedModule,
    private val prefs: SharedPreferences,
    private val loader: ClassLoader
) {

    private val tag = "NocosKeyboardStyle"
    private val frames = ThreadLocal<Frame?>()
    private val edgeCorners = ThreadLocal<Iterator<Float>?>()
    private val drawables = IdentityHashMap<Drawable, MutableMap<String, Drawable>>()
    private val paletteIds = ConcurrentHashMap<String, Int>()
    private val renderedViews = WeakHashMap<View, Long>()
    private val layers = KeyboardStyleLayers()
    private val renderers = WeakHashMap<TransitionDrawable, StyleDrawable>()
    private val blurOwners = WeakHashMap<Any, Boolean>()
    private val hits = ConcurrentHashMap.newKeySet<String>()

    private val toolbarControls = arrayOf("toolbar_bg_alpha", "toolbar_outline", "toolbar_no_shadow")
    private val keyControls = arrayOf("monet", "key_corner", "key_alpha", "key_no_outline", "key_no_shadow")
    private val edgeControls = arrayOf("monet", "key_corner", "key_no_outline", "key_no_shadow")
    private val textControls = arrayOf("key_font_scale", "hide_t9_marks", "hide_qwerty_marks", "center_marks")

    @Volatile
    private var booleans: Map<String, Boolean> = emptyMap()

    @Volatile
    private var numbers: Map<String, Int> = emptyMap()

    @Volatile
    private var signature: Long = 0L

    @Volatile
    private var appContext: Context? = null

    init {
        reload()
    }

    fun install() {
        runCatching {
            prefs.registerOnSharedPreferenceChangeListener { _, _ -> reload() }
        }
        installStyleLayer()
        installTheme()
        installBlur()
        installToolbar()
        installKeys()
        installInputRefresh()
        deoptimizeConsumers()
    }

    // ------------------------------------------------------------------
    // 配置
    // ------------------------------------------------------------------

    fun reload() {
        // 只保留「莫奈配色」；其余美化项不再生效，避免旧配置残留。
        booleans = CONTROLS.associateWith { it == "monet" && prefs.getBoolean(it, false) }
        numbers = KeyboardConfig.numberOptions.associate {
            it.key to prefs.getInt("${it.key}_value", it.default)
        }
        var hash = 17L
        CONTROLS.forEach { name ->
            hash = hash * 31 + (if (booleans[name] == true) 1 else 0)
            hash = hash * 31 + (numbers[name] ?: 0)
        }
        signature = hash
    }

    private fun enabled(name: String): Boolean = booleans[name] == true

    private fun any(vararg names: String): Boolean = names.any(::enabled)

    private fun number(name: String): Int =
        numbers[name] ?: KeyboardConfig.numberOptions.firstOrNull { it.key == name }?.default ?: 0

    private fun changed(): Long = signature

    private fun hit(name: String) {
        if (hits.add(name)) module.log(Log.INFO, tag, "Applied $name")
    }

    private fun install(name: String, block: () -> Unit) {
        runCatching(block).onFailure { module.log(Log.WARN, tag, "$name unavailable", it) }
    }

    private fun context(): Context? = appContext ?: runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        (activityThread.getDeclaredMethod("currentApplication").invoke(null) as? Context)
            ?.also { appContext = it }
    }.getOrNull()

    private fun styled(original: Drawable, name: String, icon: Boolean): Drawable =
        synchronized(drawables) {
            if (drawables.size > 256) drawables.clear()
            drawables.getOrPut(original) { mutableMapOf() }
                .getOrPut("$name:$icon") { layers.wrap(original, name, icon) }
        }

    // ------------------------------------------------------------------
    // 样式层：TransitionDrawable 只作为包装载体
    // ------------------------------------------------------------------

    private fun installStyleLayer() {
        install("style-layer") {
            val draw = TransitionDrawable::class.java.getDeclaredMethod("draw", Canvas::class.java)
            module.hook(draw).intercept { chain ->
                val wrapper = chain.thisObject as TransitionDrawable
                if (!layers.owns(wrapper)) return@intercept chain.proceed()
                val renderer = synchronized(renderers) {
                    renderers[wrapper] ?: layers.style(wrapper)?.let { key ->
                        StyleDrawable(wrapper.getDrawable(0), key.substring(1), key.startsWith('i'))
                            .also { renderers[wrapper] = it }
                    }
                } ?: return@intercept chain.proceed()
                renderer.bounds = wrapper.bounds
                renderer.state = wrapper.state
                renderer.alpha = wrapper.getDrawable(1).alpha
                renderer.colorFilter = wrapper.getDrawable(0).colorFilter
                renderer.draw(chain.getArg(0) as Canvas)
                null
            }
        }
    }

    // ------------------------------------------------------------------
    // 主题取色
    // ------------------------------------------------------------------

    private fun installTheme() {
        install("theme") {
            val themes = THEME_CLASSES.mapNotNull { load(it) }
            themes.forEach { theme ->
                theme.declaredMethods.filter {
                    it.returnType in setOf(Integer::class.java, Int::class.javaPrimitiveType) &&
                        String::class.java in it.parameterTypes
                }.forEach { method ->
                    runCatching {
                        module.hook(method).intercept { chain ->
                            val original = chain.proceed()
                            val name = firstString(chain.getArgs()) ?: return@intercept original
                            runCatching {
                                var color = (original as? Int) ?: return@runCatching original
                                val monet = if (enabled("monet")) {
                                    KeyboardAppearanceRules.monetRole(name)?.let(::systemColor)
                                } else null
                                if (monet != null) color = monet
                                if (name == "bgKeyboardBlur" && enabled("blur_opacity") && !enabled("monet")) {
                                    color = (color and 0xffffff) or ((number("blur_opacity") * 255 / 100) shl 24)
                                }
                                if (color != original) hit("theme:$name")
                                color
                            }.getOrDefault(original)
                        }
                    }.onFailure { module.log(Log.WARN, tag, "theme color ${method.name} unavailable", it) }
                }
                theme.declaredMethods.filter {
                    it.returnType == ColorStateList::class.java && String::class.java in it.parameterTypes
                }.forEach { method ->
                    runCatching {
                        module.hook(method).intercept { chain ->
                            val original = chain.proceed() as? ColorStateList
                                ?: return@intercept null
                            if (!enabled("monet")) return@intercept original
                            runCatching {
                                val name = firstString(chain.getArgs())
                                    ?: return@runCatching original
                                val target = KeyboardAppearanceRules.monetRole(name)?.let(::systemColor)
                                    ?: return@runCatching original
                                hit("color-list:$name")
                                KeyboardMonetColors.recolor(original, target)
                            }.onFailure {
                                if (hits.add("color-list-failure")) {
                                    module.log(Log.WARN, tag, "State color correction failed", it)
                                }
                            }.getOrDefault(original)
                        }
                    }.onFailure {
                        module.log(Log.WARN, tag, "theme color-list ${method.name} unavailable", it)
                    }
                }
                theme.declaredMethods.filter {
                    Drawable::class.java.isAssignableFrom(it.returnType) &&
                        String::class.java in it.parameterTypes
                }.forEach { method ->
                    runCatching {
                        module.hook(method).intercept { chain ->
                            val original = chain.proceed() as? Drawable ?: return@intercept null
                            val name = firstString(chain.getArgs()) ?: return@intercept original
                            when {
                                layers.owns(original) -> original
                                enabled("monet") && (name.startsWith("bg") || name.startsWith("icon")) ->
                                    styled(
                                        original, name,
                                        name.startsWith("icon") && !name.startsWith("iconHeaderToolItem")
                                    )
                                else -> original
                            }
                        }
                    }.onFailure {
                        module.log(Log.WARN, tag, "theme drawable ${method.name} unavailable", it)
                    }
                }
            }
            module.log(Log.INFO, tag, "Theme backends prepared: ${themes.size}")
        }
    }

    private fun firstString(args: List<Any?>): String? =
        args.firstOrNull { it is String } as? String

    private fun systemColor(role: String): Int? = runCatching {
        val res = context()?.resources ?: return@runCatching null
        val night = res.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val name = "system_${role}_${if (night) "dark" else "light"}"
        val id = paletteIds.getOrPut(name) { res.getIdentifier(name, "color", "android") }
        if (id == 0) null else res.getColor(id, null)
    }.getOrNull()

    // ------------------------------------------------------------------
    // 模糊材质
    // ------------------------------------------------------------------

    private fun installBlur() {
        install("blur") {
            val type = load("com.oplus.keyboard.input.view.blur.b") ?: return@install
            val viewField = type.getDeclaredField("a").apply { isAccessible = true }
            val managerField = type.getDeclaredField("b").apply { isAccessible = true }
            type.declaredMethods.filter {
                (it.name == "b" && it.parameterCount == 0) ||
                    (it.name == "a" && it.parameterTypes.contentEquals(arrayOf(View::class.java)))
            }.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching {
                        synchronized(blurOwners) { blurOwners[chain.thisObject!!] = true }
                        val target = managerField.get(chain.thisObject) ?: return@runCatching
                        val owner = viewField.get(chain.thisObject) as? View ?: return@runCatching
                        if (enabled("blur_radius")) {
                            target.javaClass.getMethod(
                                "setBlurRadius", Int::class.javaPrimitiveType
                            ).invoke(target, number("blur_radius"))
                            hit("blur-radius:${number("blur_radius")}")
                        }
                        if (enabled("blur_corner")) {
                            val radius = min(owner.width, owner.height) * number("blur_corner") / 200f
                            target.javaClass.getMethod(
                                "setCornerRadius",
                                Float::class.javaPrimitiveType,
                                Float::class.javaPrimitiveType,
                                Float::class.javaPrimitiveType,
                                Float::class.javaPrimitiveType
                            ).invoke(target, radius, radius, radius, radius)
                            hit("blur-corner:${number("blur_corner")}")
                        }
                    }.onFailure {
                        module.log(Log.DEBUG, tag, "Material update skipped: ${it.javaClass.simpleName}")
                    }
                    result
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 工具栏
    // ------------------------------------------------------------------

    private fun installToolbar() {
        install("toolbar") {
            val type = load("com.oplus.keyboard.input.view.head.ToolItemView") ?: return@install
            val icon = type.getDeclaredField("a").apply { isAccessible = true }
            val background = type.getDeclaredField("c").apply { isAccessible = true }
            val interactiveBackground = type.getDeclaredField("l").apply { isAccessible = true }
            val persistentBackground = type.getDeclaredField("m").apply { isAccessible = true }
            val backgroundSize = type.getDeclaredField("d").apply { isAccessible = true }
            val monetBackground = StyleDrawable(
                ColorDrawable(Color.TRANSPARENT), "bgHeaderItem", false
            )
            module.hook(type.getDeclaredMethod("onDraw", Canvas::class.java)).intercept { chain ->
                val owner = chain.thisObject as View
                val oldIcon = icon.get(chain.thisObject) as? Drawable
                val oldBackground = background.get(chain.thisObject) as? StateListDrawable
                val oldInteractive = interactiveBackground.getBoolean(owner)
                val oldPersistent = persistentBackground.getBoolean(owner)
                try {
                    if (oldIcon != null && !layers.owns(oldIcon) &&
                        (enabled("toolbar_icon_alpha") || enabled("monet"))
                    ) {
                        icon.set(chain.thisObject, styled(oldIcon, "iconHeaderItem", true))
                    }
                    if (enabled("monet")) {
                        val width = owner.width - owner.paddingLeft - owner.paddingRight
                        val height = owner.height - owner.paddingTop - owner.paddingBottom
                        val requested = backgroundSize.getInt(owner)
                        val size = if (requested > 0) requested else min(width, height)
                        val x = owner.paddingLeft + (width - size) / 2
                        val y = owner.paddingTop + (height - size) / 2
                        if (size > 0) {
                            monetBackground.setBounds(x, y, x + size, y + size)
                            monetBackground.state = owner.drawableState
                            monetBackground.draw(chain.getArg(0) as Canvas)
                        }
                        interactiveBackground.setBoolean(owner, false)
                        persistentBackground.setBoolean(owner, false)
                        hit("consistent-monet-toolbar")
                    } else if (oldBackground != null && toolbarControls.any(::enabled)) {
                        val state = StateListDrawable()
                        state.addState(intArrayOf(), styled(oldBackground, "bgHeaderItem", false))
                        background.set(chain.thisObject, state)
                    }
                    chain.proceed()
                } finally {
                    icon.set(chain.thisObject, oldIcon)
                    if (oldBackground != null) background.set(chain.thisObject, oldBackground)
                    interactiveBackground.setBoolean(owner, oldInteractive)
                    persistentBackground.setBoolean(owner, oldPersistent)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 按键
    // ------------------------------------------------------------------

    private fun installKeys() {
        install("keys") {
            val background = KeyboardRenderTargets.background(loader) ?: return@install
            val renderer = background.declaringClass
            val keyType = background.parameterTypes[0]
            val draws = KeyboardRenderTargets.draws(loader)
            if (draws.isEmpty()) return@install

            fun field(name: String): Field = keyType.getDeclaredField(name).apply { isAccessible = true }

            val labelMaps by lazy { KeyboardRenderTargets.labelMaps(loader) }
            val mark by lazy { field("t") }
            val left by lazy { field("b") }
            val right by lazy { field("c") }
            val code by lazy { field("a") }
            val top by lazy { field("d") }
            val bottom by lazy { field("e") }
            val eligible by lazy { field("E") }
            val codes = runCatching {
                val type = load("com.oplus.keyboard.base.enums.KeyCode") ?: return@runCatching emptyMap()
                val value = type.getDeclaredMethod("getValue")
                type.enumConstants.orEmpty().associate {
                    value.invoke(it) as Int to (it as Enum<*>).name
                }
            }.getOrDefault(emptyMap())
            val keyboardType by lazy {
                renderer.declaredFields.firstOrNull {
                    it.type.name == "com.oplus.keyboard.base.enums.KeyboardType"
                }?.apply { isAccessible = true }
            }

            install("material-key-geometry") {
                check(listOf(left, right, top, bottom, code).all { it.type == Int::class.javaPrimitiveType })
                check(eligible.type == Boolean::class.javaPrimitiveType)
                val edges = KeyboardRenderTargets.edgeEffect(loader) ?: error("edge effect missing")
                val effectField = KeyboardRenderTargets.effectField(edges) ?: error("edge drawable missing")
                val effectClass = effectField.type
                val setCorner = effectClass.getMethod("setCornerRadius", Float::class.javaPrimitiveType)
                val setEdge = effectClass.getMethod("setEdgeEnable", Boolean::class.javaPrimitiveType)
                val setShadow = effectClass.getMethod("setShadowEnable", Boolean::class.javaPrimitiveType)
                fun nativeRadius() = 8f * (context()?.resources?.displayMetrics?.density ?: 1f)

                module.hook(edges).intercept { chain ->
                    if (edgeControls.none(::enabled)) return@intercept chain.proceed()
                    val keys = chain.getArg(1) as? List<*> ?: return@intercept chain.proceed()
                    val nativeRadius = nativeRadius()
                    val radii = keys.filterNotNull().mapNotNull { key ->
                        val width = right.getInt(key) - left.getInt(key)
                        val height = bottom.getInt(key) - top.getInt(key)
                        if (!eligible.getBoolean(key) || width <= 0 || height <= 0) {
                            null
                        } else {
                            val name = codes[code.getInt(key)].orEmpty()
                            val ratio = KeyboardAppearanceRules.keyCornerRatio(
                                name.contains("ENTER"), enabled("monet"),
                                if (enabled("key_corner")) number("key_corner") else null
                            )
                            if (!enabled("monet") && !enabled("key_corner")) {
                                nativeRadius
                            } else {
                                min(width, height) * ratio / 2f
                            }
                        }
                    }
                    val previous = edgeCorners.get()
                    edgeCorners.set(radii.iterator())
                    try {
                        chain.proceed()
                    } finally {
                        edgeCorners.set(previous)
                    }
                }

                module.hook(effectClass.getDeclaredMethod("draw", Canvas::class.java)).intercept { chain ->
                    val iterator = edgeCorners.get()
                    if (iterator == null || !iterator.hasNext()) return@intercept chain.proceed()
                    val effect = effectField.get(null) as Drawable
                    if (chain.thisObject !== effect) return@intercept chain.proceed()
                    val nativeRadius = nativeRadius()
                    val radius = iterator.next()
                    synchronized(effect) {
                        try {
                            setCorner.invoke(effect, radius)
                            setEdge.invoke(effect, !enabled("key_no_outline"))
                            setShadow.invoke(effect, !enabled("key_no_shadow"))
                            hit("material-key-outline")
                            chain.proceed()
                        } finally {
                            setCorner.invoke(effect, nativeRadius)
                            setEdge.invoke(effect, true)
                            setShadow.invoke(effect, true)
                        }
                    }
                }

                val ambient = KeyboardRenderTargets.ambientEffect(loader) ?: error("ambient effect missing")
                module.hook(ambient).intercept { chain ->
                    if (enabled("key_no_shadow") || enabled("monet")) null else chain.proceed()
                }
            }

            install("key-text") {
                check(mark.type == String::class.java)
                check(left.type == Int::class.javaPrimitiveType && right.type == Int::class.javaPrimitiveType)
                keyboardType
                labelMaps
                draws.forEach { draw ->
                    module.hook(draw).intercept { chain ->
                        if (textControls.none(::enabled)) return@intercept chain.proceed()
                        val previous = frames.get()
                        val key = chain.getArg(1)
                        val rawMark = mark.get(key)?.toString().orEmpty()
                        val marks = (listOf(rawMark) + labelMaps.mapNotNull { it[rawMark] as? String })
                            .filter { it.isNotEmpty() }.toSet()
                        frames.set(
                            Frame(
                                marks,
                                left.getInt(key).toFloat(),
                                right.getInt(key).toFloat(),
                                keyboardType?.get(chain.thisObject)?.toString().orEmpty()
                            )
                        )
                        try {
                            chain.proceed()
                        } finally {
                            frames.set(previous)
                        }
                    }
                }
            }

            install("key-background") {
                check(code.type == Int::class.javaPrimitiveType)
                module.hook(background).intercept { chain ->
                    val original = chain.proceed() as? Drawable ?: return@intercept null
                    if (keyControls.none(::enabled)) {
                        original
                    } else {
                        val name = codes[code.getInt(chain.getArg(0))].orEmpty()
                        val kind = when {
                            name.contains("ENTER") -> "keyEnter"
                            name in FUNCTION_KEYS -> "keyFunction"
                            else -> "key"
                        }
                        hit("key-background")
                        styled(original, kind + if (chain.getArg(1) == true) "Pressed" else "", false)
                    }
                }
            }

            install("key-text-paint") {
                val text = Canvas::class.java.getDeclaredMethod(
                    "drawText",
                    String::class.java,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Paint::class.java
                )
                module.hook(text).intercept { chain ->
                    val frame = frames.get() ?: return@intercept chain.proceed()
                    val word = chain.getArg(0) as String
                    val secondary = word in frame.marks
                    if (secondary && KeyboardAppearanceRules.hideMark(
                            frame.type, enabled("hide_t9_marks"), enabled("hide_qwerty_marks")
                        )
                    ) {
                        hit("hide-secondary:${frame.type}")
                        return@intercept null
                    }
                    val paint = chain.getArg(3) as Paint
                    val size = paint.textSize
                    try {
                        if (enabled("key_font_scale")) {
                            val scale = number("key_font_scale")
                            paint.textSize = size * scale / 100f
                            hit("key-font-scale:$scale")
                        }
                        if (secondary && enabled("center_marks")) {
                            val args = chain.getArgs().toMutableList()
                            args[1] = (frame.left + frame.right - paint.measureText(word)) / 2f
                            hit("center-secondary")
                            chain.proceed(args.toTypedArray())
                        } else {
                            chain.proceed()
                        }
                    } finally {
                        paint.textSize = size
                    }
                }
            }

            install("key-refresh") {
                val refresh = renderer.getDeclaredMethod(
                    "setDrawRefresh", Boolean::class.javaPrimitiveType
                ).apply { isAccessible = true }
                module.hook(renderer.getDeclaredMethod("onDraw", Canvas::class.java)).intercept { chain ->
                    val owner = chain.thisObject as View
                    val token = changed()
                    synchronized(renderedViews) {
                        if (renderedViews.put(owner, token) != token) refresh.invoke(owner, true)
                    }
                    chain.proceed()
                }
            }

            module.log(Log.INFO, tag, "Key appearance installed: ${draws.size} native draw entries")
        }
    }

    // ------------------------------------------------------------------
    // 输入时刷新
    // ------------------------------------------------------------------

    private fun installInputRefresh() {
        install("input-refresh") {
            val service = load("com.oplus.keyboard.service.ImeService")
            val start = runCatching {
                service?.getDeclaredMethod(
                    "onStartInputView", EditorInfo::class.java, Boolean::class.javaPrimitiveType
                )
            }.getOrNull() ?: runCatching {
                android.inputmethodservice.InputMethodService::class.java.getDeclaredMethod(
                    "onStartInputView", EditorInfo::class.java, Boolean::class.javaPrimitiveType
                )
            }.getOrNull() ?: return@install
            module.hook(start).intercept { chain ->
                val result = chain.proceed()
                reload()
                synchronized(renderedViews) { renderedViews.keys.toList() }.forEach { it.invalidate() }
                synchronized(blurOwners) {
                    blurOwners.keys.toList().forEach { owner ->
                        runCatching { owner.javaClass.getDeclaredMethod("b").invoke(owner) }
                    }
                }
                result
            }
        }
    }

    // ------------------------------------------------------------------
    // 反优化：避免主题/绘制方法被内联后绕过 Hook
    // ------------------------------------------------------------------

    private fun deoptimizeConsumers() {
        install("deoptimize") {
            var count = 0
            var failed = 0
            KeyboardThemeConsumers.entries.forEach { entry ->
                runCatching {
                    val (className, methodName, descriptor) = entry.split('#', limit = 3)
                    val type = load(className) ?: error("class missing")
                    val method = type.getDeclaredMethod(methodName, *parseDescriptor(loader, descriptor))
                    if (module.deoptimize(method)) count++ else failed++
                }.onFailure { failed++ }
            }
            module.log(Log.INFO, tag, "Deoptimized $count theme consumers ($failed skipped)")
        }
    }

    /** 把 smali 方法描述符转换为反射参数类型。 */
    private fun parseDescriptor(loader: ClassLoader, descriptor: String): Array<Class<*>> {
        val inner = descriptor.substring(1, descriptor.indexOf(')'))
        val types = ArrayList<Class<*>>()
        var index = 0
        while (index < inner.length) {
            var dimensions = 0
            while (inner[index] == '[') { dimensions++; index++ }
            val primitive = when (inner[index]) {
                'V' -> Void.TYPE
                'Z' -> Boolean::class.javaPrimitiveType
                'B' -> Byte::class.javaPrimitiveType
                'C' -> Char::class.javaPrimitiveType
                'S' -> Short::class.javaPrimitiveType
                'I' -> Int::class.javaPrimitiveType
                'J' -> Long::class.javaPrimitiveType
                'F' -> Float::class.javaPrimitiveType
                'D' -> Double::class.javaPrimitiveType
                'L' -> {
                    val end = inner.indexOf(';', index)
                    val name = inner.substring(index + 1, end).replace('/', '.')
                    index = end
                    Class.forName(name, false, loader)
                }
                else -> error("bad descriptor: $descriptor")
            }!!
            index++
            var type = primitive
            repeat(dimensions) { type = java.lang.reflect.Array.newInstance(type, 0).javaClass }
            types += type
        }
        return types.toTypedArray()
    }

    private fun load(name: String): Class<*>? =
        runCatching { Class.forName(name, false, loader) }.getOrNull()

    private data class Frame(
        val marks: Set<String>,
        val left: Float,
        val right: Float,
        val type: String
    )

    /** 以保存的 Drawable 为底，叠加透明度 / 莫奈取色 / 描边 / 阴影。 */
    private inner class StyleDrawable(
        private val original: Drawable,
        private val name: String,
        private val icon: Boolean
    ) : Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var extraAlpha = 255
        private var filter: ColorFilter? = null

        private val sampled by lazy {
            runCatching {
                val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
                val previous = Rect(original.bounds)
                try {
                    original.setBounds(0, 0, 24, 24)
                    original.draw(Canvas(bitmap))
                    bitmap.getPixel(12, 12)
                } finally {
                    original.bounds = previous
                    bitmap.recycle()
                }
            }.getOrDefault(Color.TRANSPARENT)
        }

        override fun draw(canvas: Canvas) {
            val monet = enabled("monet")
            if (monet || icon && enabled("toolbar_icon_alpha") ||
                name.startsWith("bgHeader") && toolbarControls.any(::enabled)
            ) {
                hit("drawable:$name")
            }
            val key = name.startsWith("key")
            val toolbar = name == "bgHeaderItem"
            val themedSurface = !icon && !key && !toolbar
            val active = monet || when {
                icon -> enabled("toolbar_icon_alpha")
                key -> keyControls.any(::enabled)
                toolbar -> toolbarControls.any(::enabled)
                else -> false
            }
            if (!active) {
                val previous = Rect(original.bounds)
                try {
                    original.bounds = bounds
                    original.draw(canvas)
                } finally {
                    original.bounds = previous
                }
                return
            }
            val percent = when {
                icon && name == "iconHeaderItem" && enabled("toolbar_icon_alpha") ->
                    number("toolbar_icon_alpha")
                toolbar && enabled("toolbar_bg_alpha") -> number("toolbar_bg_alpha")
                key && enabled("key_alpha") -> number("key_alpha")
                else -> 100
            }
            val flat = !icon && (
                monet && (!themedSurface || KeyboardAppearanceRules.rectangularKeyboardBackground(name)) ||
                    key && (enabled("key_corner") || enabled("key_no_outline") || enabled("key_no_shadow")) ||
                    toolbar && enabled("toolbar_no_shadow")
                )
            // 原生层已经把宿主 alpha 传给原 Drawable。
            val alpha = percent * (if (flat) extraAlpha else 255) / 100
            val saved = if (alpha == 255) {
                canvas.save()
            } else {
                canvas.saveLayerAlpha(
                    bounds.left.toFloat(), bounds.top.toFloat(),
                    bounds.right.toFloat(), bounds.bottom.toFloat(), alpha
                )
            }
            try {
                if (flat) {
                    val selected = state.any {
                        it == android.R.attr.state_pressed || it == android.R.attr.state_selected ||
                            it == android.R.attr.state_checked
                    }
                    val role = when {
                        name.contains("Enter") || name.contains("Pressed") || toolbar && selected ->
                            "primary_container"
                        name.contains("Function") -> "secondary_container"
                        toolbar -> "secondary_container"
                        key -> "surface_container_highest"
                        else -> "surface_container_low"
                    }
                    paint.color = if (monet) systemColor(role) ?: sampled else sampled
                    paint.colorFilter = filter
                    val ratio = if (themedSurface) 0f else if (toolbar) 1f else {
                        KeyboardAppearanceRules.keyCornerRatio(
                            name.contains("Enter"), monet,
                            if (key && enabled("key_corner")) number("key_corner") else null
                        )
                    }
                    val radius = min(bounds.width(), bounds.height()) * ratio / 2f
                    val shape = RectF(bounds)
                    val density = context()?.resources?.displayMetrics?.density ?: 1f
                    if (key && !monet && !enabled("key_no_shadow")) {
                        shape.inset(density, density)
                        paint.setShadowLayer(density * .75f, 0f, density * .5f, 0x26000000)
                    }
                    canvas.drawRoundRect(shape, radius, radius, paint)
                    paint.clearShadowLayer()
                    if (key && !monet && !enabled("key_no_outline")) {
                        paint.color = systemColor("outline_variant") ?: 0x40808080
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = density * .33f
                        canvas.drawRoundRect(shape, radius, radius, paint)
                        paint.style = Paint.Style.FILL
                    }
                } else {
                    val previous = Rect(original.bounds)
                    val oldFilter = original.colorFilter
                    try {
                        original.bounds = bounds
                        original.colorFilter = filter ?: if (monet) {
                            KeyboardAppearanceRules.monetRole(name)?.let(::systemColor)?.let {
                                PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN)
                            } ?: oldFilter
                        } else {
                            oldFilter
                        }
                        original.draw(canvas)
                    } finally {
                        original.bounds = previous
                        original.colorFilter = oldFilter
                    }
                }
                if (toolbar && enabled("toolbar_outline")) {
                    paint.color = systemColor("outline_variant") ?: Color.GRAY
                    if (!flat) paint.alpha = paint.alpha * extraAlpha / 255
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = context()?.resources?.displayMetrics?.density ?: 1f
                    val radius = min(bounds.width(), bounds.height()) * .5f
                    canvas.drawRoundRect(
                        RectF(bounds).apply { inset(paint.strokeWidth, paint.strokeWidth) },
                        radius, radius, paint
                    )
                    paint.style = Paint.Style.FILL
                }
            } finally {
                canvas.restoreToCount(saved)
            }
        }

        override fun setAlpha(alpha: Int) {
            extraAlpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            filter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun getIntrinsicWidth(): Int = original.intrinsicWidth
        override fun getIntrinsicHeight(): Int = original.intrinsicHeight
        override fun getPadding(padding: Rect): Boolean = original.getPadding(padding)
        override fun onStateChange(state: IntArray): Boolean = original.setState(state)
        override fun isStateful(): Boolean = original.isStateful
    }

    private companion object {
        val CONTROLS = arrayOf(
            "monet", "blur_radius", "blur_opacity", "blur_corner",
            "toolbar_icon_alpha", "toolbar_bg_alpha", "toolbar_outline", "toolbar_no_shadow",
            "key_corner", "key_alpha", "key_font_scale", "key_no_outline", "key_no_shadow",
            "hide_t9_marks", "hide_qwerty_marks", "center_marks"
        )

        val THEME_CLASSES = listOf(
            "com.oplus.keyboard.base.theme.a",
            "com.oplus.keyboard.base.theme.b",
            "com.oplus.keyboard.base.theme.work.e",
            "com.oplus.keyboard.base.theme.work.pack.d",
            "com.oplus.keyboard.base.theme.work.marker.b"
        )

        val FUNCTION_KEYS = setOf("BACK", "SHIFT", "SPACE", "SYMBOL", "LEFTSYMBOL", "CLEAR")
    }
}
