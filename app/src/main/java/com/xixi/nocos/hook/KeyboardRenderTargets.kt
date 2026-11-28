package com.xixi.nocos.hook

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 键盘绘制目标定位。
 *
 * ONextBox 使用 DexKit 动态定位；nonocos 不引入 DexKit，改为按小布输入法
 * 1.9.25.17 的真实类名/签名定位，并在签名变化时返回 null 由调用方降级。
 */
internal object KeyboardRenderTargets {

    const val RENDERER = "com.oplus.keyboard.input.view.body.t"
    const val KEY = "com.oplus.keyboard.base.entity.f"
    const val EDGE = "com.oplus.keyboard.base.util.s"
    const val AMBIENT = "com.oplus.keyboard.base.util.r"

    fun load(loader: ClassLoader, name: String): Class<*>? =
        runCatching { Class.forName(name, false, loader) }.getOrNull()

    fun background(loader: ClassLoader): Method? {
        val renderer = load(loader, RENDERER) ?: return null
        val key = load(loader, KEY) ?: return null
        return runCatching {
            renderer.declaredMethods.firstOrNull {
                it.returnType == Drawable::class.java &&
                    it.parameterTypes.contentEquals(arrayOf(key, Boolean::class.javaPrimitiveType))
            }
        }.getOrNull()?.apply { isAccessible = true }
    }

    fun draws(loader: ClassLoader): List<Method> {
        val renderer = load(loader, RENDERER) ?: return emptyList()
        val key = load(loader, KEY) ?: return emptyList()
        return renderer.declaredMethods.filter {
            it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(Canvas::class.java, key))
        }.onEach { it.isAccessible = true }
    }

    /** 收集渲染器中静态的 mark -> 本地化文字 映射（若当前版本存在）。 */
    fun labelMaps(loader: ClassLoader): List<Map<*, *>> {
        val renderer = load(loader, RENDERER) ?: return emptyList()
        val fields = ArrayList<Field>()
        var type: Class<*>? = renderer
        while (type != null && type != View::class.java && type != Any::class.java) {
            fields += type.declaredFields
            type = type.superclass
        }
        return fields.filter {
            Modifier.isStatic(it.modifiers) && Map::class.java.isAssignableFrom(it.type)
        }.mapNotNull { field ->
            runCatching {
                field.isAccessible = true
                field.get(null) as? Map<*, *>
            }.getOrNull()
        }
    }

    fun effects(loader: ClassLoader): List<Method> {
        val methods = listOf(
            load(loader, EDGE)?.let { type ->
                type.declaredMethods.firstOrNull {
                    it.returnType == Void.TYPE &&
                        it.parameterTypes.contentEquals(
                            arrayOf(Canvas::class.java, List::class.java, Boolean::class.javaPrimitiveType)
                        )
                }
            },
            load(loader, AMBIENT)?.let { type ->
                type.declaredMethods.firstOrNull {
                    it.returnType == Void.TYPE &&
                        it.parameterTypes.contentEquals(
                            arrayOf(Canvas::class.java, List::class.java, Boolean::class.javaPrimitiveType)
                        )
                }
            }
        ).filterNotNull()
        return methods.onEach { it.isAccessible = true }
    }

    fun edgeEffect(loader: ClassLoader): Method? =
        effects(loader).firstOrNull(::isEdge)

    fun ambientEffect(loader: ClassLoader): Method? =
        effects(loader).firstOrNull { !isEdge(it) }

    fun isEdge(method: Method): Boolean = method.declaringClass.declaredFields.any {
        Modifier.isStatic(it.modifiers) && Drawable::class.java.isAssignableFrom(it.type) &&
            it.type.methods.any { candidate -> candidate.name == "setEdgeEnable" }
    }

    fun effectField(method: Method): Field? = method.declaringClass.declaredFields.firstOrNull {
        Modifier.isStatic(it.modifiers) && Drawable::class.java.isAssignableFrom(it.type) &&
            it.type.methods.any { candidate -> candidate.name == "setEdgeEnable" }
    }?.apply { isAccessible = true }
}
