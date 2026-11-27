package com.xixi.nocos.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.nocos.R
import com.xixi.nocos.common.MultiAppConfig
import com.xixi.nocos.ui.theme.AppColors
import com.xixi.nocos.ui.theme.ServiceStatus
import com.xixi.nocos.ui.theme.SettingSwitchRow
import com.xixi.nocos.ui.theme.SolidCard

@Composable
fun MultiAppScreen(
    colors: AppColors,
    serviceStatus: ServiceStatus,
    unlockApps: Boolean,
    extendedSlots: Boolean,
    isSaving: Boolean,
    onToggleUnlockApps: (Boolean) -> Unit,
    onToggleExtendedSlots: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val isServiceConnected = serviceStatus == ServiceStatus.CONNECTED
    val enabled = isServiceConnected && !isSaving

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        ModuleDetailHeader(
            colors = colors,
            title = stringResource(R.string.module_multi_app_title),
            packageName = MultiAppConfig.targetPackageName,
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!isServiceConnected) {
            Text(
                text = stringResource(R.string.multi_app_unavailable_tip),
                fontSize = 13.sp,
                color = colors.error
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        SolidCard(colors) {
            Column(modifier = Modifier.padding(20.dp)) {
                SettingSwitchRow(
                    colors = colors,
                    title = stringResource(R.string.multi_app_unlock_title),
                    description = stringResource(R.string.multi_app_unlock_desc),
                    checked = unlockApps,
                    enabled = enabled,
                    onCheckedChange = onToggleUnlockApps
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SolidCard(colors) {
            Column(modifier = Modifier.padding(20.dp)) {
                SettingSwitchRow(
                    colors = colors,
                    title = stringResource(R.string.multi_app_slots_title),
                    description = stringResource(R.string.multi_app_slots_desc),
                    checked = extendedSlots,
                    enabled = enabled,
                    onCheckedChange = onToggleExtendedSlots
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}
