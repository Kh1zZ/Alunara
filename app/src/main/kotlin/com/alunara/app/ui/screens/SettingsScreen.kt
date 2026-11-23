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
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MotionPhotosOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.components.safeRequestFocus
import com.alunara.app.ui.i18n.AppLanguage
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraDisplayPrefs
import com.alunara.app.ui.theme.AlunaraTypography
import com.alunara.app.ui.theme.LocalAlunaraColors
import com.alunara.app.ui.theme.LocalAlunaraDisplayPrefs
import com.alunara.core.domain.model.AuthSessionState
import com.alunara.core.domain.policy.AudioQualityPreset

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
    audioQuality: AudioQualityPreset = AudioQualityPreset.AUTO,
    onSelectAudioQuality: (AudioQualityPreset) -> Unit = {},
    appLanguage: AppLanguage = AppLanguage.ENGLISH,
    onSelectAppLanguage: (AppLanguage) -> Unit = {},
    displayPrefs: AlunaraDisplayPrefs = AlunaraDisplayPrefs(),
    onUpdateDisplayPrefs: (AlunaraDisplayPrefs) -> Unit = {},
) {
    val colors = LocalAlunaraColors.current
    val activePrefs = LocalAlunaraDisplayPrefs.current
    var confirmingLogout by remember { mutableStateOf(false) }
    val cancelLogoutRequester = remember { FocusRequester() }

    LaunchedEffect(confirmingLogout) {
        if (confirmingLogout) {
            withFrameNanos { }
            cancelLogoutRequester.safeRequestFocus()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_headline),
                color = colors.primaryText,
                fontSize = AlunaraTypography.headline(activePrefs.textScale),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.focusProperties { canFocus = false },
            )
        }

        item {
            SettingsToggleRow(
                label = stringResource(R.string.autoplay_label),
                actionLabel = if (autoplayEnabled) stringResource(R.string.state_on) else stringResource(R.string.state_off),
                icon = if (autoplayEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome,
                highlighted = autoplayEnabled,
                onClick = onToggleAutoplay,
            )
        }

        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(AlunaraDimens.RadiusControl))
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.focusProperties { canFocus = false },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = colors.primaryAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.audio_quality_section_title),
                        color = colors.primaryAccent,
                        fontSize = AlunaraTypography.body(activePrefs.textScale),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                val qualityDescription =
                    when (audioQuality) {
                        AudioQualityPreset.AUTO -> stringResource(R.string.audio_quality_desc_auto)
                        AudioQualityPreset.LOW -> stringResource(R.string.audio_quality_desc_low)
                        AudioQualityPreset.MEDIUM -> stringResource(R.string.audio_quality_desc_medium)
                        AudioQualityPreset.HIGH -> stringResource(R.string.audio_quality_desc_high)
                    }
                Text(
                    text = "$qualityDescription · ${stringResource(R.string.audio_quality_subtitle)}",
                    color = colors.mutedText,
                    fontSize = AlunaraTypography.label(activePrefs.textScale),
                    lineHeight = AlunaraTypography.label(activePrefs.textScale) * 1.4f,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val options =
                        listOf(
                            AudioQualityPreset.AUTO to stringResource(R.string.audio_quality_auto),
                            AudioQualityPreset.LOW to stringResource(R.string.audio_quality_low),
                            AudioQualityPreset.MEDIUM to stringResource(R.string.audio_quality_medium),
                            AudioQualityPreset.HIGH to stringResource(R.string.audio_quality_high),
                        )
                    options.forEach { (preset, label) ->
                        TvFocusableButton(
                            text = label,
                            highlighted = audioQuality == preset,
                            onClick = { onSelectAudioQuality(preset) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(AlunaraDimens.RadiusControl))
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.focusProperties { canFocus = false },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = colors.primaryAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.language_section_title),
                        color = colors.primaryAccent,
                        fontSize = AlunaraTypography.body(activePrefs.textScale),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                val languageDescription =
                    when (appLanguage) {
                        AppLanguage.INDONESIAN -> stringResource(R.string.language_desc_indonesian)
                        AppLanguage.ENGLISH -> stringResource(R.string.language_desc_english)
                    }
                Text(
                    text = "$languageDescription · ${stringResource(R.string.language_subtitle)}",
                    color = colors.mutedText,
                    fontSize = AlunaraTypography.label(activePrefs.textScale),
                    lineHeight = AlunaraTypography.label(activePrefs.textScale) * 1.4f,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val languageOptions =
                        listOf(
                            AppLanguage.INDONESIAN to stringResource(R.string.language_option_indonesian),
                            AppLanguage.ENGLISH to stringResource(R.string.language_option_english),
                        )
                    languageOptions.forEach { (language, label) ->
                        TvFocusableButton(
                            text = label,
                            highlighted = appLanguage == language,
                            onClick = { onSelectAppLanguage(language) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(AlunaraDimens.RadiusControl))
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.display_section_title),
                    color = colors.primaryAccent,
                    fontSize = AlunaraTypography.body(activePrefs.textScale),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
                SettingsInlineToggle(
                    label = stringResource(R.string.text_size_label),
                    actionLabel =
                        if (displayPrefs.largeText) {
                            stringResource(R.string.text_size_large)
                        } else {
                            stringResource(R.string.text_size_normal)
                        },
                    icon = Icons.Rounded.FormatSize,
                    highlighted = displayPrefs.largeText,
                    onClick = { onUpdateDisplayPrefs(displayPrefs.copy(largeText = !displayPrefs.largeText)) },
                )
                SettingsInlineToggle(
                    label = stringResource(R.string.high_contrast_label),
                    actionLabel = if (displayPrefs.highContrast) stringResource(R.string.state_on) else stringResource(R.string.state_off),
                    icon = Icons.Rounded.Contrast,
                    highlighted = displayPrefs.highContrast,
                    onClick = { onUpdateDisplayPrefs(displayPrefs.copy(highContrast = !displayPrefs.highContrast)) },
                )
                SettingsInlineToggle(
                    label = stringResource(R.string.reduce_motion_label),
                    actionLabel = if (displayPrefs.reduceMotion) stringResource(R.string.state_on) else stringResource(R.string.state_off),
                    icon = Icons.Rounded.MotionPhotosOff,
                    highlighted = displayPrefs.reduceMotion,
                    onClick = { onUpdateDisplayPrefs(displayPrefs.copy(reduceMotion = !displayPrefs.reduceMotion)) },
                )
            }
        }

        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(AlunaraDimens.RadiusControl))
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
                        tint = colors.primaryAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.account_section_title),
                        color = colors.primaryAccent,
                        fontSize = AlunaraTypography.body(activePrefs.textScale),
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
                    color = colors.primaryText,
                    fontSize = AlunaraTypography.rowTitle(activePrefs.textScale),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.account_keystore_status),
                    color = colors.primaryAccent,
                    fontSize = AlunaraTypography.label(activePrefs.textScale),
                )
                Text(
                    text =
                        if (authSession is AuthSessionState.Authenticated) {
                            stringResource(R.string.account_synced_note)
                        } else {
                            stringResource(R.string.account_fg04_note)
                        },
                    color = colors.mutedText,
                    fontSize = AlunaraTypography.label(activePrefs.textScale),
                    lineHeight = AlunaraTypography.label(activePrefs.textScale) * 1.45f,
                )

                if (authLoading) {
                    Text(
                        text = stringResource(R.string.login_verifying),
                        color = colors.primaryAccent,
                        fontSize = AlunaraTypography.label(activePrefs.textScale),
                        fontWeight = FontWeight.Medium,
                    )
                }

                if (!authErrorMessage.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.login_error_prefix, authErrorMessage),
                        color = colors.errorText,
                        fontSize = AlunaraTypography.label(activePrefs.textScale),
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
                                color = colors.errorText,
                                fontSize = AlunaraTypography.label(activePrefs.textScale),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TvFocusableButton(
                                    text = stringResource(R.string.cancel),
                                    onClick = { confirmingLogout = false },
                                    modifier = Modifier.focusRequester(cancelLogoutRequester),
                                )
                                TvFocusableButton(
                                    text = stringResource(R.string.logout_confirm_action),
                                    icon = Icons.AutoMirrored.Rounded.Logout,
                                    highlighted = true,
                                    onClick = {
                                        confirmingLogout = false
                                        onLogout()
                                    },
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
                        .background(colors.surface, RoundedCornerShape(AlunaraDimens.RadiusControl))
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
                        tint = colors.primaryAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.compliance_summary),
                        color = colors.primaryAccent,
                        fontSize = AlunaraTypography.label(activePrefs.textScale),
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    text = stringResource(R.string.attribution_text),
                    color = colors.mutedText,
                    fontSize = AlunaraTypography.body(activePrefs.textScale),
                    lineHeight = AlunaraTypography.body(activePrefs.textScale) * 1.45f,
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    actionLabel: String,
    icon: ImageVector,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAlunaraColors.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(AlunaraDimens.RadiusControl))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SettingsInlineToggle(
            label = label,
            actionLabel = actionLabel,
            icon = icon,
            highlighted = highlighted,
            onClick = onClick,
        )
    }
}

@Composable
private fun SettingsInlineToggle(
    label: String,
    actionLabel: String,
    icon: ImageVector,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAlunaraColors.current
    val activePrefs = LocalAlunaraDisplayPrefs.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = colors.primaryText,
            fontSize = AlunaraTypography.rowTitle(activePrefs.textScale),
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
                    .focusProperties { canFocus = false },
        )
        TvFocusableButton(
            text = actionLabel,
            icon = icon,
            highlighted = highlighted,
            onClick = onClick,
        )
    }
}
