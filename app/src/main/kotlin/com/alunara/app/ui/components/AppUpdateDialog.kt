package com.alunara.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraTypography
import com.alunara.app.ui.theme.LocalAlunaraColors
import com.alunara.app.ui.theme.LocalAlunaraDisplayPrefs
import com.alunara.app.update.AppUpdateInfo
import com.alunara.app.update.AppUpdateUiState
import com.alunara.app.update.formatByteSize

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppUpdateDialog(
    visible: Boolean,
    state: AppUpdateUiState,
    currentVersionName: String,
    onStartDownload: (AppUpdateInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onRetryCheck: () -> Unit,
    onRequestInstallPermission: () -> Unit,
    onRetriggerInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    launchError: String? = null,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    val primaryActionRequester = remember { FocusRequester() }
    val secondaryActionRequester = remember { FocusRequester() }

    val stateKey =
        when (state) {
            is AppUpdateUiState.UpdateAvailable -> "available"
            is AppUpdateUiState.Downloading -> "downloading"
            is AppUpdateUiState.PermissionRequired -> "permission"
            is AppUpdateUiState.ReadyToInstall -> "ready"
            is AppUpdateUiState.Error -> "error"
            AppUpdateUiState.Idle,
            AppUpdateUiState.Checking,
            AppUpdateUiState.UpToDate,
            -> "other"
        }

    LaunchedEffect(visible, stateKey) {
        if (visible && stateKey != "other") {
            withFrameNanos { }
            withFrameNanos { }
            primaryActionRequester.safeRequestFocus()
        }
    }

    BackHandler(enabled = visible) {
        if (state is AppUpdateUiState.Downloading) {
            onCancelDownload()
        } else {
            onDismiss()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter =
                if (prefs.reduceMotion) {
                    EnterTransition.None
                } else {
                    fadeIn(tween(durationMillis = 180))
                },
            exit =
                if (prefs.reduceMotion) {
                    ExitTransition.None
                } else {
                    fadeOut(tween(durationMillis = 150))
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
                            onClick = {
                                if (state !is AppUpdateUiState.Downloading) {
                                    onDismiss()
                                }
                            },
                        ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter =
                if (prefs.reduceMotion) {
                    EnterTransition.None
                } else {
                    fadeIn(tween(durationMillis = 180)) +
                        scaleIn(animationSpec = tween(durationMillis = 190), initialScale = 0.94f)
                },
            exit =
                if (prefs.reduceMotion) {
                    ExitTransition.None
                } else {
                    fadeOut(tween(durationMillis = 140)) +
                        scaleOut(animationSpec = tween(durationMillis = 140), targetScale = 0.96f)
                },
            modifier = Modifier.align(Alignment.Center),
        ) {
            val cardShape = RoundedCornerShape(AlunaraDimens.RadiusCard)
            val cardInteraction = remember { MutableInteractionSource() }
            Column(
                modifier =
                    Modifier
                        .widthIn(min = 440.dp, max = 600.dp)
                        .padding(24.dp)
                        .clip(cardShape)
                        .background(colors.surface, cardShape)
                        .border(BorderStroke(colors.focusBorderWidth, colors.focusRingStrong), cardShape)
                        .clickable(
                            interactionSource = cardInteraction,
                            indication = null,
                            onClick = {},
                        )
                        .focusGroup()
                        .focusProperties {
                            onExit = { cancelFocusChange() }
                        }
                        .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (launchError != null) {
                    Text(
                        text = launchError,
                        color = colors.errorText,
                        fontSize = AlunaraTypography.label(prefs.textScale),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                when (state) {
                    is AppUpdateUiState.UpdateAvailable -> {
                        val info = state.info
                        UpdateDialogHeader(
                            icon = Icons.Rounded.SystemUpdate,
                            title = stringResource(R.string.update_dialog_title),
                            tint = colors.primaryAccent,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.secondarySurface)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.update_dialog_version_transition, currentVersionName, info.versionName),
                                    color = colors.primaryAccent,
                                    fontSize = AlunaraTypography.label(prefs.textScale),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            val apkMeta =
                                if (info.asset.sizeBytes > 0L) {
                                    stringResource(
                                        R.string.update_dialog_apk_meta,
                                        info.asset.name,
                                        formatByteSize(info.asset.sizeBytes),
                                    )
                                } else {
                                    info.asset.name
                                }
                            Text(
                                text = apkMeta,
                                color = colors.mutedText,
                                fontSize = AlunaraTypography.caption(prefs.textScale),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 176.dp)
                                    .clip(RoundedCornerShape(AlunaraDimens.RadiusControl))
                                    .background(colors.secondarySurface)
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.update_dialog_whats_new),
                                color = colors.primaryAccent,
                                fontSize = AlunaraTypography.label(prefs.textScale),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = info.releaseNotes.ifBlank { stringResource(R.string.update_dialog_no_notes) },
                                color = colors.primaryText,
                                fontSize = AlunaraTypography.label(prefs.textScale),
                                lineHeight = AlunaraTypography.label(prefs.textScale) * 1.45f,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        ) {
                            TvFocusableButton(
                                text = stringResource(R.string.update_dialog_later),
                                onClick = onDismiss,
                                modifier =
                                    Modifier
                                        .focusRequester(secondaryActionRequester)
                                        .focusProperties {
                                            left = secondaryActionRequester
                                            up = secondaryActionRequester
                                            down = secondaryActionRequester
                                            right = primaryActionRequester
                                        },
                            )
                            TvFocusableButton(
                                text = stringResource(R.string.update_dialog_download_install),
                                icon = Icons.Rounded.Download,
                                highlighted = true,
                                onClick = { onStartDownload(info) },
                                modifier =
                                    Modifier
                                        .focusRequester(primaryActionRequester)
                                        .focusProperties {
                                            left = secondaryActionRequester
                                            up = primaryActionRequester
                                            down = primaryActionRequester
                                            right = primaryActionRequester
                                        },
                            )
                        }
                    }

                    is AppUpdateUiState.Downloading -> {
                        val info = state.info
                        UpdateDialogHeader(
                            icon = Icons.Rounded.Download,
                            title = stringResource(R.string.update_dialog_downloading_title),
                            tint = colors.primaryAccent,
                        )
                        Text(
                            text = "${info.asset.name} (${stringResource(R.string.update_dialog_version_transition, currentVersionName, info.versionName)})",
                            color = colors.mutedText,
                            fontSize = AlunaraTypography.label(prefs.textScale),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val fraction =
                            if (state.totalBytes > 0L) {
                                (state.downloadedBytes.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.secondarySurface),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(if (state.totalBytes > 0L) fraction else 0.35f)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.primaryAccent),
                            )
                        }

                        val progressLabel =
                            if (state.totalBytes > 0L) {
                                val percent = (fraction * 100f).toInt().coerceIn(0, 100)
                                stringResource(
                                    R.string.update_dialog_progress_known,
                                    percent,
                                    formatByteSize(state.downloadedBytes),
                                    formatByteSize(state.totalBytes),
                                )
                            } else {
                                stringResource(
                                    R.string.update_dialog_progress_unknown,
                                    formatByteSize(state.downloadedBytes),
                                )
                            }
                        Text(
                            text = progressLabel,
                            color = colors.primaryText,
                            fontSize = AlunaraTypography.body(prefs.textScale),
                            fontWeight = FontWeight.Medium,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TvFocusableButton(
                                text = stringResource(R.string.cancel),
                                onClick = onCancelDownload,
                                modifier =
                                    Modifier
                                        .focusRequester(primaryActionRequester)
                                        .focusProperties {
                                            left = primaryActionRequester
                                            right = primaryActionRequester
                                            up = primaryActionRequester
                                            down = primaryActionRequester
                                        },
                            )
                        }
                    }

                    is AppUpdateUiState.PermissionRequired -> {
                        UpdateDialogHeader(
                            icon = Icons.Rounded.Security,
                            title = stringResource(R.string.update_dialog_permission_title),
                            tint = colors.primaryAccent,
                        )
                        Text(
                            text = stringResource(R.string.update_dialog_permission_desc),
                            color = colors.primaryText,
                            fontSize = AlunaraTypography.body(prefs.textScale),
                            lineHeight = AlunaraTypography.body(prefs.textScale) * 1.45f,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        ) {
                            TvFocusableButton(
                                text = stringResource(R.string.cancel),
                                onClick = onDismiss,
                                modifier =
                                    Modifier
                                        .focusRequester(secondaryActionRequester)
                                        .focusProperties {
                                            left = secondaryActionRequester
                                            up = secondaryActionRequester
                                            down = secondaryActionRequester
                                            right = primaryActionRequester
                                        },
                            )
                            TvFocusableButton(
                                text = stringResource(R.string.update_dialog_allow_installs),
                                icon = Icons.Rounded.Security,
                                highlighted = true,
                                onClick = onRequestInstallPermission,
                                modifier =
                                    Modifier
                                        .focusRequester(primaryActionRequester)
                                        .focusProperties {
                                            left = secondaryActionRequester
                                            up = primaryActionRequester
                                            down = primaryActionRequester
                                            right = primaryActionRequester
                                        },
                            )
                        }
                    }

                    is AppUpdateUiState.ReadyToInstall -> {
                        UpdateDialogHeader(
                            icon = Icons.Rounded.InstallMobile,
                            title = stringResource(R.string.update_dialog_ready_title),
                            tint = colors.primaryAccent,
                        )
                        Text(
                            text = stringResource(R.string.update_dialog_ready_desc),
                            color = colors.primaryText,
                            fontSize = AlunaraTypography.body(prefs.textScale),
                            lineHeight = AlunaraTypography.body(prefs.textScale) * 1.45f,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        ) {
                            TvFocusableButton(
                                text = stringResource(R.string.update_dialog_close),
                                onClick = onDismiss,
                                modifier =
                                    Modifier
                                        .focusRequester(secondaryActionRequester)
                                        .focusProperties {
                                            left = secondaryActionRequester
                                            up = secondaryActionRequester
                                            down = secondaryActionRequester
                                            right = primaryActionRequester
                                        },
                            )
                            TvFocusableButton(
                                text = stringResource(R.string.update_dialog_install_now),
                                icon = Icons.Rounded.InstallMobile,
                                highlighted = true,
                                onClick = onRetriggerInstall,
                                modifier =
                                    Modifier
                                        .focusRequester(primaryActionRequester)
                                        .focusProperties {
                                            left = secondaryActionRequester
                                            up = primaryActionRequester
                                            down = primaryActionRequester
                                            right = primaryActionRequester
                                        },
                            )
                        }
                    }

                    is AppUpdateUiState.Error -> {
                        UpdateDialogHeader(
                            icon = Icons.Rounded.ErrorOutline,
                            title = stringResource(R.string.update_dialog_title),
                            tint = colors.errorText,
                        )
                        Text(
                            text = stringResource(R.string.update_status_error, state.message),
                            color = colors.errorText,
                            fontSize = AlunaraTypography.body(prefs.textScale),
                            lineHeight = AlunaraTypography.body(prefs.textScale) * 1.45f,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        ) {
                            TvFocusableButton(
                                text = stringResource(R.string.update_dialog_close),
                                onClick = onDismiss,
                                modifier =
                                    Modifier
                                        .focusRequester(secondaryActionRequester)
                                        .focusProperties {
                                            left = secondaryActionRequester
                                            up = secondaryActionRequester
                                            down = secondaryActionRequester
                                            right = primaryActionRequester
                                        },
                            )
                            TvFocusableButton(
                                text = stringResource(R.string.retry),
                                highlighted = true,
                                onClick = {
                                    val info = state.retryInfo
                                    if (info != null) {
                                        onStartDownload(info)
                                    } else {
                                        onRetryCheck()
                                    }
                                },
                                modifier =
                                    Modifier
                                        .focusRequester(primaryActionRequester)
                                        .focusProperties {
                                            left = secondaryActionRequester
                                            up = primaryActionRequester
                                            down = primaryActionRequester
                                            right = primaryActionRequester
                                        },
                            )
                        }
                    }

                    AppUpdateUiState.Idle,
                    AppUpdateUiState.Checking,
                    AppUpdateUiState.UpToDate,
                    -> Unit
                }
            }
        }
    }
}

@Composable
private fun UpdateDialogHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.focusProperties { canFocus = false },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = title,
            color = colors.primaryText,
            fontSize = AlunaraTypography.title(prefs.textScale),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
