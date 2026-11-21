package com.alunara.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.core.domain.model.AuthSessionState

@Composable
fun SettingsScreen(
    autoplayEnabled: Boolean,
    authSession: AuthSessionState,
    authLoading: Boolean,
    authErrorMessage: String?,
    onToggleAutoplay: () -> Unit,
    onOpenWebViewLogin: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingLogout by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_headline),
                color = AlunaraPalette.PrimaryText,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(AlunaraPalette.Surface.copy(alpha = 0.72f), RoundedCornerShape(AlunaraDimens.RadiusControl))
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
        }

        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(AlunaraPalette.Surface.copy(alpha = 0.72f), RoundedCornerShape(AlunaraDimens.RadiusControl))
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = AlunaraPalette.PrimaryAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.account_section_title),
                        color = AlunaraPalette.PrimaryAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                val scopeText =
                    when (authSession) {
                        AuthSessionState.Guest -> stringResource(R.string.account_scope_guest)
                        is AuthSessionState.Authenticated ->
                            stringResource(R.string.account_scope_authenticated, authSession.profile.name)
                        is AuthSessionState.Expired -> stringResource(R.string.account_scope_expired)
                    }
                Text(
                    text = scopeText,
                    color = AlunaraPalette.PrimaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.account_keystore_status),
                    color = AlunaraPalette.PrimaryAccent,
                    fontSize = 13.sp,
                )
                Text(
                    text = stringResource(R.string.account_fg04_note),
                    color = AlunaraPalette.MutedText,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )

                if (authLoading) {
                    Text(
                        text = stringResource(R.string.login_verifying),
                        color = AlunaraPalette.PrimaryAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                if (!authErrorMessage.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.login_error_prefix, authErrorMessage),
                        color = AlunaraPalette.ErrorText,
                        fontSize = 13.sp,
                    )
                }

                if (authSession !is AuthSessionState.Authenticated) {
                    TvFocusableButton(
                        text = stringResource(R.string.login_webview_button),
                        icon = Icons.AutoMirrored.Rounded.Login,
                        highlighted = true,
                        enabled = !authLoading,
                        onClick = onOpenWebViewLogin,
                    )
                }

                if (authSession !is AuthSessionState.Guest) {
                    if (!confirmingLogout) {
                        TvFocusableButton(
                            text = stringResource(R.string.logout_button),
                            icon = Icons.AutoMirrored.Rounded.Logout,
                            onClick = { confirmingLogout = true },
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.logout_confirm_prompt),
                                color = AlunaraPalette.ErrorText,
                                fontSize = 13.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TvFocusableButton(
                                    text = stringResource(R.string.logout_confirm_action),
                                    icon = Icons.AutoMirrored.Rounded.Logout,
                                    highlighted = true,
                                    onClick = {
                                        confirmingLogout = false
                                        onLogout()
                                    },
                                )
                                TvFocusableButton(
                                    text = stringResource(R.string.cancel),
                                    onClick = { confirmingLogout = false },
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(AlunaraPalette.Surface.copy(alpha = 0.72f), RoundedCornerShape(AlunaraDimens.RadiusControl))
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
}
