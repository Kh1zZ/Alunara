package com.alunara.app.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MotionPhotosOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.alunara.app.BuildConfig
import com.alunara.app.R
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.components.TvIconButton
import com.alunara.app.ui.components.safeRequestFocus
import com.alunara.app.ui.i18n.AppLanguage
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraDisplayPrefs
import com.alunara.app.ui.theme.AlunaraTypography
import com.alunara.app.ui.theme.LocalAlunaraColors
import com.alunara.app.ui.theme.LocalAlunaraDisplayPrefs
import com.alunara.app.update.AppUpdateUiState
import com.alunara.core.domain.model.AuthSessionState
import com.alunara.core.domain.policy.AudioQualityPreset

@OptIn(ExperimentalComposeUiApi::class)
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
    currentVersionName: String = BuildConfig.VERSION_NAME,
    autoUpdateEnabled: Boolean = true,
    updateUiState: AppUpdateUiState = AppUpdateUiState.Idle,
    onToggleAutoUpdate: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onOpenUpdateDialog: () -> Unit = {},
) {
    val colors = LocalAlunaraColors.current
    val activePrefs = LocalAlunaraDisplayPrefs.current
    var confirmingLogout by remember { mutableStateOf(false) }
    val cancelLogoutRequester = remember { FocusRequester() }
    var isPickingLanguage by remember { mutableStateOf(false) }
    var languageSlideDir by remember { mutableIntStateOf(1) }
    var hadOpenedLanguagePicker by remember { mutableStateOf(false) }
    val languageTriggerRequester = remember { FocusRequester() }
    val languagePickerRequester = remember { FocusRequester() }

    LaunchedEffect(confirmingLogout) {
        if (confirmingLogout) {
            withFrameNanos { }
            cancelLogoutRequester.safeRequestFocus()
        }
    }

    LaunchedEffect(isPickingLanguage) {
        if (isPickingLanguage) {
            hadOpenedLanguagePicker = true
            withFrameNanos { }
            withFrameNanos { }
            languagePickerRequester.safeRequestFocus()
        } else if (hadOpenedLanguagePicker) {
            withFrameNanos { }
            withFrameNanos { }
            languageTriggerRequester.safeRequestFocus()
        }
    }

    BackHandler(enabled = isPickingLanguage) {
        isPickingLanguage = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = !isPickingLanguage },
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
                val languageDescription = languageDescriptionText(appLanguage)
                Text(
                    text = "$languageDescription · ${stringResource(R.string.language_subtitle)}",
                    color = colors.mutedText,
                    fontSize = AlunaraTypography.label(activePrefs.textScale),
                    lineHeight = AlunaraTypography.label(activePrefs.textScale) * 1.4f,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
                LanguagePickerTriggerRow(
                    activeLanguage = appLanguage,
                    hint = stringResource(R.string.language_picker_action_hint),
                    onClick = { isPickingLanguage = true },
                    modifier = Modifier.focusRequester(languageTriggerRequester),
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusProperties { canFocus = false },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = colors.primaryAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.update_section_title),
                            color = colors.primaryAccent,
                            fontSize = AlunaraTypography.body(activePrefs.textScale),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.secondarySurface)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.update_current_version, currentVersionName),
                            color = colors.primaryText,
                            fontSize = AlunaraTypography.caption(activePrefs.textScale),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                val updateStatusText =
                    when (updateUiState) {
                        AppUpdateUiState.Idle -> stringResource(R.string.update_auto_subtitle)
                        AppUpdateUiState.Checking -> stringResource(R.string.update_status_checking)
                        AppUpdateUiState.UpToDate ->
                            stringResource(R.string.update_status_up_to_date, currentVersionName)
                        is AppUpdateUiState.UpdateAvailable ->
                            stringResource(R.string.update_status_available, updateUiState.info.versionName)
                        is AppUpdateUiState.Downloading -> {
                            val pct =
                                if (updateUiState.totalBytes > 0L) {
                                    ((updateUiState.downloadedBytes.toFloat() / updateUiState.totalBytes.toFloat()) * 100f)
                                        .toInt()
                                        .coerceIn(0, 100)
                                } else {
                                    0
                                }
                            stringResource(R.string.update_status_downloading, pct)
                        }
                        is AppUpdateUiState.PermissionRequired ->
                            stringResource(R.string.update_dialog_permission_title)
                        is AppUpdateUiState.ReadyToInstall ->
                            stringResource(R.string.update_dialog_ready_title)
                        is AppUpdateUiState.Error ->
                            stringResource(R.string.update_status_error, updateUiState.message)
                    }
                val isErrorStatus = updateUiState is AppUpdateUiState.Error
                Text(
                    text = updateStatusText,
                    color = if (isErrorStatus) colors.errorText else colors.mutedText,
                    fontSize = AlunaraTypography.label(activePrefs.textScale),
                    lineHeight = AlunaraTypography.label(activePrefs.textScale) * 1.4f,
                    modifier = Modifier.focusProperties { canFocus = false },
                )

                SettingsInlineToggle(
                    label = stringResource(R.string.update_auto_label),
                    actionLabel =
                        if (autoUpdateEnabled) {
                            stringResource(R.string.state_on)
                        } else {
                            stringResource(R.string.state_off)
                        },
                    icon = Icons.Rounded.SystemUpdate,
                    highlighted = autoUpdateEnabled,
                    onClick = onToggleAutoUpdate,
                )

                val hasPendingActionDialog =
                    updateUiState is AppUpdateUiState.UpdateAvailable ||
                        updateUiState is AppUpdateUiState.Downloading ||
                        updateUiState is AppUpdateUiState.PermissionRequired ||
                        updateUiState is AppUpdateUiState.ReadyToInstall
                val actionButtonText =
                    when (updateUiState) {
                        is AppUpdateUiState.UpdateAvailable ->
                            stringResource(R.string.update_open_button, updateUiState.info.versionName)
                        is AppUpdateUiState.Downloading -> {
                            val pct =
                                if (updateUiState.totalBytes > 0L) {
                                    ((updateUiState.downloadedBytes.toFloat() / updateUiState.totalBytes.toFloat()) * 100f)
                                        .toInt()
                                        .coerceIn(0, 100)
                                } else {
                                    0
                                }
                            stringResource(R.string.update_status_downloading, pct)
                        }
                        is AppUpdateUiState.PermissionRequired,
                        is AppUpdateUiState.ReadyToInstall,
                        -> stringResource(R.string.update_dialog_install_now)
                        else -> stringResource(R.string.update_check_button)
                    }
                TvFocusableButton(
                    text = actionButtonText,
                    icon = Icons.Rounded.Refresh,
                    highlighted = hasPendingActionDialog,
                    enabled = updateUiState !is AppUpdateUiState.Checking,
                    onClick = if (hasPendingActionDialog) onOpenUpdateDialog else onCheckForUpdates,
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

        AnimatedVisibility(
            visible = isPickingLanguage,
            enter =
                if (activePrefs.reduceMotion) {
                    EnterTransition.None
                } else {
                    fadeIn(animationSpec = tween(durationMillis = 180))
                },
            exit =
                if (activePrefs.reduceMotion) {
                    ExitTransition.None
                } else {
                    fadeOut(animationSpec = tween(durationMillis = 150))
                },
            modifier = Modifier.fillMaxSize(),
        ) {
            val scrimInteraction = remember { MutableInteractionSource() }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .clickable(
                            interactionSource = scrimInteraction,
                            indication = null,
                            onClick = { isPickingLanguage = false },
                        ),
            )
        }

        AnimatedVisibility(
            visible = isPickingLanguage,
            enter =
                if (activePrefs.reduceMotion) {
                    EnterTransition.None
                } else {
                    fadeIn(tween(durationMillis = 180)) + scaleIn(initialScale = 0.94f, animationSpec = tween(durationMillis = 190))
                },
            exit =
                if (activePrefs.reduceMotion) {
                    ExitTransition.None
                } else {
                    fadeOut(tween(durationMillis = 140)) + scaleOut(targetScale = 0.96f, animationSpec = tween(durationMillis = 140))
                },
            modifier = Modifier.align(Alignment.Center),
        ) {
            FocusLockedLanguagePickerDialog(
                activeLanguage = appLanguage,
                slideDirection = languageSlideDir,
                reduceMotion = activePrefs.reduceMotion,
                pickerFocusRequester = languagePickerRequester,
                onCycleLanguage = { delta ->
                    languageSlideDir = if (delta >= 0) 1 else -1
                    onSelectAppLanguage(appLanguage.cycle(delta))
                },
                onConfirmClose = { isPickingLanguage = false },
            )
        }
    }
}

@Composable
private fun languageDescriptionText(language: AppLanguage): String =
    when (language) {
        AppLanguage.INDONESIAN -> stringResource(R.string.language_desc_indonesian)
        AppLanguage.ENGLISH -> stringResource(R.string.language_desc_english)
        AppLanguage.JAPANESE -> stringResource(R.string.language_desc_japanese)
    }

@Composable
private fun LanguagePickerTriggerRow(
    activeLanguage: AppLanguage,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(AlunaraDimens.RadiusControl)
    val bgColor = if (focused) colors.focusedButtonBg else colors.secondarySurface
    val primaryColor = if (focused) colors.focusedButtonContent else colors.primaryText
    val secondaryColor = if (focused) colors.focusedButtonContent.copy(alpha = 0.78f) else colors.mutedText
    val chevronColor = if (focused) colors.focusedButtonContent else colors.primaryAccent
    val borderColor = if (focused) colors.focusRingStrong else colors.primaryAccent.copy(alpha = 0.55f)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bgColor, shape)
                .border(BorderStroke(colors.focusBorderWidth, borderColor), shape)
                .semantics { role = Role.Button }
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = activeLanguage.nativeDisplayName,
                color = primaryColor,
                fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = hint,
                color = secondaryColor,
                fontSize = AlunaraTypography.caption(prefs.textScale),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = null,
                tint = chevronColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "·",
                color = secondaryColor,
                fontSize = AlunaraTypography.body(prefs.textScale),
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = chevronColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FocusLockedLanguagePickerDialog(
    activeLanguage: AppLanguage,
    slideDirection: Int,
    reduceMotion: Boolean,
    pickerFocusRequester: FocusRequester,
    onCycleLanguage: (Int) -> Unit,
    onConfirmClose: () -> Unit,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    val cardShape = RoundedCornerShape(AlunaraDimens.RadiusCard)
    val cardInteraction = remember { MutableInteractionSource() }

    Column(
        modifier =
            Modifier
                .widthIn(min = 420.dp, max = 560.dp)
                .clip(cardShape)
                .background(colors.surface, cardShape)
                .border(BorderStroke(colors.focusBorderWidth, colors.focusRingStrong), cardShape)
                .clickable(
                    interactionSource = cardInteraction,
                    indication = null,
                    onClick = {},
                )
                .focusRequester(pickerFocusRequester)
                .focusProperties {
                    exit = { FocusRequester.Cancel }
                }
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    val code = keyEvent.nativeKeyEvent.keyCode
                    when (keyEvent.type) {
                        KeyEventType.KeyDown ->
                            when (code) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    onCycleLanguage(-1)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    onCycleLanguage(1)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                -> true
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_NUMPAD_ENTER,
                                KeyEvent.KEYCODE_BACK,
                                -> {
                                    onConfirmClose()
                                    true
                                }
                                else -> false
                            }
                        KeyEventType.KeyUp ->
                            when (code) {
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_NUMPAD_ENTER,
                                KeyEvent.KEYCODE_BACK,
                                -> true
                                else -> false
                            }
                        else -> false
                    }
                }
                .padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.language_section_title),
                color = colors.primaryAccent,
                fontSize = AlunaraTypography.sectionTitle(prefs.textScale),
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvIconButton(
                icon = Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.language_picker_prev_cd),
                highlighted = true,
                onClick = { onCycleLanguage(-1) },
                modifier = Modifier.focusProperties { canFocus = false },
            )

            AnimatedContent(
                targetState = activeLanguage,
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val enterSlide =
                            slideInHorizontally(animationSpec = tween(durationMillis = 180)) { width ->
                                if (slideDirection >= 0) width / 3 else -width / 3
                            }
                        val exitSlide =
                            slideOutHorizontally(animationSpec = tween(durationMillis = 150)) { width ->
                                if (slideDirection >= 0) -width / 3 else width / 3
                            }
                        (enterSlide + fadeIn(tween(durationMillis = 160))) togetherWith
                            (exitSlide + fadeOut(tween(durationMillis = 130)))
                    }
                },
                label = "language_picker_slide",
                modifier = Modifier.weight(1f),
            ) { selectedLanguage ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AlunaraDimens.RadiusControl))
                            .background(colors.secondarySurface)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = selectedLanguage.nativeDisplayName,
                        color = colors.primaryText,
                        fontSize = AlunaraTypography.headline(prefs.textScale),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = languageDescriptionText(selectedLanguage),
                        color = colors.mutedText,
                        fontSize = AlunaraTypography.label(prefs.textScale),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            TvIconButton(
                icon = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.language_picker_next_cd),
                highlighted = true,
                onClick = { onCycleLanguage(1) },
                modifier = Modifier.focusProperties { canFocus = false },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.focusProperties { canFocus = false },
        ) {
            AppLanguage.entries.forEach { entry ->
                val isSelected = entry == activeLanguage
                val indicatorWidth by
                    animateDpAsState(
                        targetValue = if (isSelected) 20.dp else 6.dp,
                        animationSpec =
                            if (reduceMotion) {
                                androidx.compose.animation.core.snap()
                            } else {
                                tween(durationMillis = 160)
                            },
                        label = "lang_dot_width",
                    )
                Box(
                    modifier =
                        Modifier
                            .width(indicatorWidth)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    colors.primaryAccent
                                } else {
                                    colors.mutedText.copy(alpha = 0.35f)
                                },
                            ),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.language_picker_focus_hint),
                color = colors.mutedText,
                fontSize = AlunaraTypography.caption(prefs.textScale),
                lineHeight = AlunaraTypography.caption(prefs.textScale) * 1.35f,
                modifier =
                    Modifier
                        .weight(1f)
                        .focusProperties { canFocus = false },
            )
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(AlunaraDimens.RadiusControl))
                        .background(colors.focusedButtonBg)
                        .focusProperties { canFocus = false }
                        .clickable(onClick = onConfirmClose)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.language_picker_done),
                    color = colors.focusedButtonContent,
                    fontSize = AlunaraTypography.label(prefs.textScale),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
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
