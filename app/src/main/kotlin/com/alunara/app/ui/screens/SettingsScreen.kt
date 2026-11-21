package com.alunara.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.theme.AlunaraPalette

@Composable
fun SettingsScreen(
    autoplayEnabled: Boolean,
    onToggleAutoplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_headline),
            color = AlunaraPalette.PrimaryText,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(AlunaraPalette.Surface.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.autoplay_label),
                color = AlunaraPalette.PrimaryText,
                fontSize = 15.sp,
            )
            TvFocusableButton(
                text = if (autoplayEnabled) stringResource(R.string.state_on) else stringResource(R.string.state_off),
                icon = if (autoplayEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome,
                highlighted = autoplayEnabled,
                onClick = onToggleAutoplay,
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(AlunaraPalette.Surface.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = AlunaraPalette.PrimaryAccent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.compliance_summary),
                    color = AlunaraPalette.PrimaryAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = stringResource(R.string.attribution_text),
                color = AlunaraPalette.MutedText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}
