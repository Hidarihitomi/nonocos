package com.xixi.nocos.hook

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable

/**
 * 与 ONextBox 的 KeyboardStyleLayers 一致。
 *
 * TransitionDrawable 只作为包装载体，第二层是透明占位；通过 layer id 记录原始资源名，
 * 这样即使系统克隆 ConstantState，也能恢复出正确的绘制规则。
 */
internal class KeyboardStyleLayers {
    private val styles = ArrayList<String>()
    private val ids = HashMap<String, Int>()

    @Synchronized
    fun wrap(original: Drawable, name: String, icon: Boolean): TransitionDrawable {
        val key = (if (icon) "i" else "b") + name
        if (original is TransitionDrawable && owns(original) && style(original) == key) return original
        val id = ids.getOrPut(key) {
            styles.add(key)
            styles.size
        }
        return TransitionDrawable(
            arrayOf(original, GradientDrawable().apply { setColor(Color.TRANSPARENT) })
        ).apply {
            setId(0, id)
            setId(1, MARKER)
        }
    }

    fun owns(value: Drawable): Boolean =
        value is TransitionDrawable && value.numberOfLayers == 2 && value.getId(1) == MARKER

    @Synchronized
    fun style(value: TransitionDrawable): String? = styles.getOrNull(value.getId(0) - 1)

    @Synchronized
    fun capture(): Array<String> = styles.toTypedArray()

    @Synchronized
    fun restore(saved: Array<*>) {
        styles.clear()
        ids.clear()
        saved.filterIsInstanceTo(styles, String::class.java)
        styles.forEachIndexed { index, key -> ids[key] = index + 1 }
    }

    private companion object {
        const val MARKER = -0x4f4e4258
    }
}
