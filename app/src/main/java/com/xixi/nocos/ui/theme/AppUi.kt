package com.xixi.nocos.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 纯色、无渐变、无模糊的界面基础组件。
 */
@Composable
fun AppTheme(colors: AppColors, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        content()
    }
}

enum class ServiceStatus {
    WAITING, CONNECTED, ERROR
}

@Composable
fun SolidCard(colors: AppColors, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.divider, RoundedCornerShape(16.dp))
    ) {
        content()
    }
}

@Composable
fun Divider(colors: AppColors) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
}

@Composable
fun StatusPill(text: String, textColor: Color, containerColor: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(textColor)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
fun ModuleRow(
    colors: AppColors,
    icon: ImageBitmap?,
    fallbackIcon: ImageBitmap?,
    title: String,
    enabled: Boolean?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.divider, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconBadge(icon = icon ?: fallbackIcon, colors = colors, active = enabled == true)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun AppIconBadge(icon: ImageBitmap?, colors: AppColors, active: Boolean = true) {
    val grayscale = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceVariant)
            .border(1.dp, colors.divider, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            androidx.compose.foundation.Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
                colorFilter = if (active) null else grayscale
            )
        } else {
            Text(
                text = "?",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textTertiary
            )
        }
    }
}

@Composable
fun SettingSwitchRow(
    colors: AppColors,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) colors.textPrimary else colors.textTertiary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = colors.textSecondary,
                lineHeight = 19.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.surface,
                uncheckedTrackColor = colors.disabled,
                uncheckedBorderColor = colors.divider
            )
        )
    }
}

@Composable
fun SettingSliderRow(
    colors: AppColors,
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) colors.textPrimary else colors.textTertiary
            )
            Text(
                text = valueText,
                fontSize = 13.sp,
                color = if (enabled) colors.accent else colors.textTertiary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.divider
            )
        )
    }
}

@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val size = 144
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}

@Composable
fun rememberDrawableIcon(resId: Int): ImageBitmap? {
    val context = LocalContext.current
    return remember(resId) {
        runCatching {
            val drawable = context.getDrawable(resId) ?: return@runCatching null
            val size = 192
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}

@Composable
fun rememberOwnIcon(): ImageBitmap? {
    val context = LocalContext.current
    return remember {
        runCatching {
            val drawable = context.applicationInfo.loadIcon(context.packageManager)
            val size = 144
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}

// Flat Solid Color Palette - No Glass, No Transparent UI Elements
class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val accent: Color,
    val accentSurface: Color,
    val success: Color,
    val successSurface: Color,
    val warning: Color,
    val warningSurface: Color,
    val error: Color,
    val disabled: Color
) {
    companion object {
        val Light = AppColors(
            background = Color(0xFFF2F2F7),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF9F9F9),
            textPrimary = Color(0xFF000000),
            textSecondary = Color(0xFF666666),
            textTertiary = Color(0xFF999999),
            divider = Color(0xFFE5E5EA),
            accent = Color(0xFF007AFF),
            accentSurface = Color(0xFFE5F1FF),
            success = Color(0xFF1E9E4F),
            successSurface = Color(0xFFE6F6EA),
            warning = Color(0xFFC2681A),
            warningSurface = Color(0xFFFCEBD8),
            error = Color(0xFFFF3B30),
            disabled = Color(0xFFD1D1D6)
        )

        val Dark = AppColors(
            background = Color(0xFF000000),
            surface = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFF2C2C2E),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFEBEBF5),
            textTertiary = Color(0xFF8E8E93),
            divider = Color(0xFF38383A),
            accent = Color(0xFF0A84FF),
            accentSurface = Color(0xFF002E66),
            success = Color(0xFF32D74B),
            successSurface = Color(0xFF14301D),
            warning = Color(0xFFFFB74D),
            warningSurface = Color(0xFF3A2A15),
            error = Color(0xFFFF453A),
            disabled = Color(0xFF3A3A3C)
        )
    }
}
