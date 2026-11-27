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
import com.xixi.nocos.common.KeyboardConfig
import com.xixi.nocos.ui.theme.AppColors
import com.xixi.nocos.ui.theme.ServiceStatus
import com.xixi.nocos.ui.theme.SettingSwitchRow
import com.xixi.nocos.ui.theme.SolidCard

@Composable
fun KeyboardScreen(
    colors: AppColors,
    serviceStatus: ServiceStatus,
    booleans: Map<String, Boolean>,
    isSaving: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val connected = serviceStatus == ServiceStatus.CONNECTED

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
            title = stringResource(R.string.module_keyboard_title),
            packageName = KeyboardConfig.targetPackageName,
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!connected) {
            Text(
                text = stringResource(R.string.keyboard_unavailable_tip),
                fontSize = 13.sp,
                color = colors.error
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        SolidCard(colors) {
            Column(modifier = Modifier.padding(20.dp)) {
                SettingSwitchRow(
                    colors = colors,
                    title = stringResource(R.string.keyboard_monet_title),
                    description = stringResource(R.string.keyboard_monet_desc),
                    checked = booleans[KeyboardConfig.prefsKeyMonet] == true,
                    enabled = connected && !isSaving,
                    onCheckedChange = { onToggle(KeyboardConfig.prefsKeyMonet, it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SolidCard(colors) {
            Column(modifier = Modifier.padding(20.dp)) {
                SettingSwitchRow(
                    colors = colors,
                    title = stringResource(R.string.keyboard_material_unlock_title),
                    description = stringResource(R.string.keyboard_material_unlock_desc),
                    checked = booleans[KeyboardConfig.prefsKeyMaterialUnlock] == true,
                    enabled = connected && !isSaving,
                    onCheckedChange = { onToggle(KeyboardConfig.prefsKeyMaterialUnlock, it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.keyboard_style_tip),
            fontSize = 12.sp,
            color = colors.textTertiary,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))
    }
}
