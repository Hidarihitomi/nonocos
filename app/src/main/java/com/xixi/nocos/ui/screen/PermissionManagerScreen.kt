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
import com.xixi.nocos.common.PermissionManagerConfig
import com.xixi.nocos.ui.theme.AppColors
import com.xixi.nocos.ui.theme.ServiceStatus
import com.xixi.nocos.ui.theme.SettingSwitchRow
import com.xixi.nocos.ui.theme.SolidCard

@Composable
fun PermissionManagerScreen(
    colors: AppColors,
    serviceStatus: ServiceStatus,
    oldStartDialog: Boolean,
    alwaysAllow: Boolean,
    unlockRestricted: Boolean,
    disableMalicious: Boolean,
    exportPages: Boolean,
    isSaving: Boolean,
    onToggleOldStartDialog: (Boolean) -> Unit,
    onToggleAlwaysAllow: (Boolean) -> Unit,
    onToggleUnlockRestricted: (Boolean) -> Unit,
    onToggleDisableMalicious: (Boolean) -> Unit,
    onToggleExportPages: (Boolean) -> Unit,
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
            title = stringResource(R.string.module_permission_title),
            packageName = PermissionManagerConfig.securityPermissionPackage,
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!isServiceConnected) {
            Text(
                text = stringResource(R.string.permission_unavailable_tip),
                fontSize = 13.sp,
                color = colors.error
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        SolidCard(colors) {
            Column(modifier = Modifier.padding(20.dp)) {
                SettingSwitchRow(
                    colors = colors,
                    title = stringResource(R.string.permission_old_dialog_title),
                    description = stringResource(R.string.permission_old_dialog_desc),
                    checked = oldStartDialog,
                    enabled = enabled,
                    onCheckedChange = onToggleOldStartDialog
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SolidCard(colors) {
            Column(modifier = Modifier.padding(20.dp)) {
                SettingSwitchRow(
                    colors = colors,
                    title = stringResource(R.string.permission_always_allow_title),
                    description = stringResource(
                        if (oldStartDialog) {
                            R.string.permission_always_allow_old_desc
                        } else {
                            R.string.permission_always_allow_desc
                        }
                    ),
                    checked = alwaysAllow,
                    enabled = enabled && !oldStartDialog,
                    onCheckedChange = onToggleAlwaysAllow
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SolidCard(colors) {
            Column(modifier = Modifier.padding(20.dp)) {
                SettingSwitchRow(
                    colors = colors,
                    title = stringResource(R.string.permission_unlock_restricted_title),
                    description = stringResource(R.string.permission_unlock_restricted_desc),
                    checked = unlockRestricted,
                    enabled = enabled,
                    onCheckedChange = onToggleUnlockRestricted
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SolidCard(colors) {
            Column(modifier = Modifier.padding(20.dp)) {
                SettingSwitchRow(
                    colors = colors,
                    title = stringResource(R.string.permission_disable_malicious_title),
                    description = stringResource(R.string.permission_disable_malicious_desc),
                    checked = disableMalicious,
                    enabled = enabled,
                    onCheckedChange = onToggleDisableMalicious
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SolidCard(colors) {
            Column(modifier = Modifier.padding(20.dp)) {
                SettingSwitchRow(
                    colors = colors,
                    title = stringResource(R.string.permission_export_pages_title),
                    description = stringResource(R.string.permission_export_pages_desc),
                    checked = exportPages,
                    enabled = enabled,
                    onCheckedChange = onToggleExportPages
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}
