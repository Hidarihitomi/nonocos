package com.xixi.nocos.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.nocos.R
import com.xixi.nocos.common.GameStartAnimationConfig
import com.xixi.nocos.common.KeyboardConfig
import com.xixi.nocos.common.LauncherMenuConfig
import com.xixi.nocos.common.ModuleId
import com.xixi.nocos.common.MultiAppConfig
import com.xixi.nocos.common.PermissionManagerConfig
import com.xixi.nocos.common.ScreenshotFixConfig
import com.xixi.nocos.common.ThemeVipConfig
import com.xixi.nocos.data.DeviceInfo
import com.xixi.nocos.ui.theme.AppColors
import com.xixi.nocos.ui.theme.ModuleRow
import com.xixi.nocos.ui.theme.ServiceStatus
import com.xixi.nocos.ui.theme.StatusPill
import com.xixi.nocos.ui.theme.rememberAppIcon
import com.xixi.nocos.ui.theme.rememberDrawableIcon
import com.xixi.nocos.ui.theme.rememberOwnIcon

@Composable
fun HomeScreen(
    colors: AppColors,
    serviceStatus: ServiceStatus,
    screenshotEnabled: Boolean?,
    launcherMenuEnabled: Boolean?,
    themeVipEnabled: Boolean?,
    gameStartAnimationEnabled: Boolean?,
    keyboardEnabled: Boolean?,
    permissionEnabled: Boolean?,
    multiAppEnabled: Boolean?,
    onOpenModule: (ModuleId) -> Unit
) {
    val scrollState = rememberScrollState()
    val ownIcon = rememberOwnIcon()
    val screenshotIcon = rememberDrawableIcon(R.drawable.ic_module_screenshot)
    val themeIcon = rememberDrawableIcon(R.drawable.ic_module_theme)
    val screenshotFallbackIcon = rememberAppIcon(ScreenshotFixConfig.targetPackageName)
    val themeFallbackIcon = rememberAppIcon(ThemeVipConfig.targetPackageName)
    val activated = serviceStatus == ServiceStatus.CONNECTED
    val contentLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    contentLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(contentLayer)
                }
                .verticalScroll(scrollState)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(statusBarHeight + 20.dp))

            DeviceInfoCard(
                colors = colors,
                activated = activated,
                title = stringResource(R.string.app_name),
                versionText = DeviceInfo.systemVersion,
                modelText = DeviceInfo.modelName
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.home_modules_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            ModuleRow(
                colors = colors,
                icon = screenshotIcon ?: screenshotFallbackIcon,
                fallbackIcon = ownIcon,
                title = stringResource(R.string.module_screenshot_title),
                enabled = screenshotEnabled,
                onClick = { onOpenModule(ModuleId.SCREENSHOT_FIX) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ModuleRow(
                colors = colors,
                icon = rememberAppIcon(LauncherMenuConfig.targetPackageName),
                fallbackIcon = ownIcon,
                title = stringResource(R.string.module_launcher_title),
                enabled = launcherMenuEnabled,
                onClick = { onOpenModule(ModuleId.LAUNCHER_MENU) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ModuleRow(
                colors = colors,
                icon = themeIcon ?: themeFallbackIcon,
                fallbackIcon = ownIcon,
                title = stringResource(R.string.module_theme_title),
                enabled = themeVipEnabled,
                onClick = { onOpenModule(ModuleId.THEME_VIP) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ModuleRow(
                colors = colors,
                icon = rememberAppIcon(GameStartAnimationConfig.targetPackageName),
                fallbackIcon = ownIcon,
                title = stringResource(R.string.module_game_start_animation_title),
                enabled = gameStartAnimationEnabled,
                onClick = { onOpenModule(ModuleId.GAME_START_ANIMATION) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ModuleRow(
                colors = colors,
                icon = rememberAppIcon(KeyboardConfig.targetPackageName),
                fallbackIcon = ownIcon,
                title = stringResource(R.string.module_keyboard_title),
                enabled = keyboardEnabled,
                onClick = { onOpenModule(ModuleId.KEYBOARD) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ModuleRow(
                colors = colors,
                icon = rememberAppIcon(PermissionManagerConfig.securityPermissionPackage),
                fallbackIcon = ownIcon,
                title = stringResource(R.string.module_permission_title),
                enabled = permissionEnabled,
                onClick = { onOpenModule(ModuleId.PERMISSION_MANAGER) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ModuleRow(
                colors = colors,
                icon = rememberAppIcon(MultiAppConfig.targetPackageName),
                fallbackIcon = ownIcon,
                title = stringResource(R.string.module_multi_app_title),
                enabled = multiAppEnabled,
                onClick = { onOpenModule(ModuleId.MULTI_APP) }
            )

            Spacer(modifier = Modifier.height(48.dp))
        }

        // 状态栏区域：把滚动内容模糊后垫在状态栏下方，形成毛玻璃效果。
        if (statusBarHeight > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .blur(24.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLayer(contentLayer)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .background(colors.background.copy(alpha = 0.32f))
            )
        }
    }
}

/**
 * 首页顶部信息卡：应用名 + 设备版本 + 机型，右侧显示激活状态。
 */
@Composable
private fun DeviceInfoCard(
    colors: AppColors,
    activated: Boolean,
    title: String,
    versionText: String,
    modelText: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF5C7A94))
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = versionText,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.82f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = modelText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        StatusPill(
            text = stringResource(
                if (activated) R.string.activation_state_active else R.string.activation_state_inactive
            ),
            textColor = if (activated) colors.success else colors.warning,
            containerColor = if (activated) colors.successSurface else colors.warningSurface
        )
    }
}

/**
 * 详情页顶栏：返回 + 模块名 + 目标包名
 */
@Composable
fun ModuleDetailHeader(
    colors: AppColors,
    title: String,
    packageName: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.module_back),
                tint = colors.textPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = packageName,
                fontSize = 12.sp,
                color = colors.textTertiary
            )
        }
    }
}
