package com.xixi.nocos.hook

import android.content.res.ColorStateList

/**
 * 与 ONextBox 的 KeyboardMonetColors 一致：保留原始状态的 alpha，只替换色相。
 */
internal object KeyboardMonetColors {

    fun recolor(original: ColorStateList, target: Int): ColorStateList = runCatching {
        @Suppress("UNCHECKED_CAST")
        val states = ColorStateList::class.java.getDeclaredMethod("getStates")
            .invoke(original) as Array<IntArray>
        val colors = ColorStateList::class.java.getDeclaredMethod("getColors")
            .invoke(original) as IntArray
        ColorStateList(
            states.map { it.clone() }.toTypedArray(),
            colors.map { tint(it, target) }.toIntArray()
        )
    }.getOrElse {
        val enabled = android.R.attr.state_enabled
        val states = arrayOf(
            intArrayOf(-enabled),
            intArrayOf(android.R.attr.state_pressed),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(android.R.attr.state_focused),
            intArrayOf()
        )
        val colors = states.mapIndexed { index, state ->
            val active = if (index == 0) intArrayOf() else intArrayOf(enabled, *state)
            tint(original.getColorForState(active, original.defaultColor), target)
        }.toIntArray()
        ColorStateList(states, colors)
    }

    private fun tint(original: Int, target: Int): Int =
        (target and 0xffffff) or (original and 0xff000000.toInt())
}
