package com.xixi.nocos.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.nocos.R
import com.xixi.nocos.common.SpoofConfig
import com.xixi.nocos.ui.theme.AppColors
import com.xixi.nocos.ui.theme.ServiceStatus
import com.xixi.nocos.ui.theme.SolidCard

@Composable
fun CosaSpoofScreen(
    colors: AppColors,
    serviceStatus: ServiceStatus,
    currentValue: String,
    inputValue: String,
    onInputValueChange: (String) -> Unit,
    onSaveClicked: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val isServiceConnected = serviceStatus == ServiceStatus.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
            .systemBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        ModuleDetailHeader(
            colors = colors,
            title = stringResource(R.string.module_cosa_title),
            packageName = SpoofConfig.targetPackageName,
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSection(
            colors = colors,
            isServiceConnected = isServiceConnected,
            currentValue = currentValue,
            inputValue = inputValue,
            onInputValueChange = onInputValueChange,
            onSaveClicked = onSaveClicked
        )

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun SettingsSection(
    colors: AppColors,
    isServiceConnected: Boolean,
    currentValue: String,
    inputValue: String,
    onInputValueChange: (String) -> Unit,
    onSaveClicked: () -> Unit
) {
    SolidCard(colors) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.spoof_prjname_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.spoof_prjname_effective, currentValue),
                fontSize = 13.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(colors.surfaceVariant, RoundedCornerShape(12.dp))
                    .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputValue.isEmpty()) {
                    Text(
                        text = stringResource(R.string.spoof_prjname_title),
                        color = colors.textTertiary,
                        fontSize = 16.sp
                    )
                }
                BasicTextField(
                    value = inputValue,
                    onValueChange = onInputValueChange,
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = isServiceConnected,
                    modifier = Modifier.fillMaxWidth(),
                    cursorBrush = SolidColor(colors.accent)
                )
            }

            AnimatedVisibility(visible = !isServiceConnected) {
                Text(
                    text = stringResource(R.string.service_unavailable_tip),
                    color = colors.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isServiceConnected) colors.accent else colors.disabled)
                    .clickable(
                        enabled = isServiceConnected,
                        onClick = onSaveClicked
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.spoof_prjname_save_and_apply),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.status_tip),
                fontSize = 12.sp,
                color = colors.textTertiary,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
