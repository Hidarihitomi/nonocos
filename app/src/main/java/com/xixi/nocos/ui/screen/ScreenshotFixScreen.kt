package com.xixi.nocos.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.nocos.R
import com.xixi.nocos.common.ScreenshotFixConfig
import com.xixi.nocos.ui.theme.AppColors
import com.xixi.nocos.ui.theme.ServiceStatus
import com.xixi.nocos.ui.theme.SettingSwitchRow
import com.xixi.nocos.ui.theme.SolidCard

@Composable
fun ScreenshotFixScreen(
    colors: AppColors,
    serviceStatus: ServiceStatus,
    enabled: Boolean,
    isSaving: Boolean,
    onToggle: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val isServiceConnected = serviceStatus == ServiceStatus.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        ModuleDetailHeader(
            colors = colors,
            title = stringResource(R.string.module_screenshot_title),
            packageName = ScreenshotFixConfig.targetPackageName,
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(24.dp))

        SolidCard(colors) {
            Column(modifier = Modifier.padding(20.dp)) {
                SettingSwitchRow(
                    colors = colors,
                    title = stringResource(R.string.screenshot_fix_enable_title),
                    description = stringResource(R.string.screenshot_fix_enable_desc),
                    checked = enabled,
                    enabled = isServiceConnected && !isSaving,
                    onCheckedChange = onToggle
                )

                if (!isServiceConnected) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.screenshot_fix_unavailable_tip),
                        fontSize = 13.sp,
                        color = colors.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}
