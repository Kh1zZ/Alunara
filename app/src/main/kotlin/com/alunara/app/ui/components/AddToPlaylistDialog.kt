package com.alunara.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraTypography
import com.alunara.app.ui.theme.LocalAlunaraColors
import com.alunara.app.ui.theme.LocalAlunaraDisplayPrefs
import com.alunara.core.domain.model.AuthSessionState
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.Track

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AddToPlaylistDialog(
    visible: Boolean,
    targetTrack: Track?,
    authSession: AuthSessionState,
    playlists: List<PlaylistSummary>,
    onAddTrackToPlaylist: (PlaylistSummary, Track) -> Unit,
    onCreatePlaylist: (String, Track?) -> Unit,
    onOpenSignIn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var creatingMode by remember(visible, targetTrack) { mutableStateOf(targetTrack == null) }
    var newTitle by remember(visible, targetTrack) { mutableStateOf("") }
    var titleFocused by remember { mutableStateOf(false) }

    val primaryRequester = remember { FocusRequester() }
    val titleInputRequester = remember { FocusRequester() }

    LaunchedEffect(visible, creatingMode) {
        if (visible) {
            withFrameNanos { }
            withFrameNanos { }
            if (authSession is AuthSessionState.Authenticated && creatingMode) {
                titleInputRequester.safeRequestFocus()
            } else {
                primaryRequester.safeRequestFocus()
            }
        }
    }

    BackHandler(enabled = visible) {
        if (creatingMode && targetTrack != null) {
            creatingMode = false
        } else {
            onDismiss()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = if (prefs.reduceMotion) EnterTransition.None else fadeIn(tween(150)),
            exit = if (prefs.reduceMotion) ExitTransition.None else fadeOut(tween(120)),
            modifier = Modifier.fillMaxSize(),
        ) {
            val scrimInteraction = remember { MutableInteractionSource() }
            val cardShape = RoundedCornerShape(AlunaraDimens.RadiusCard)
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                val cardInteraction = remember { MutableInteractionSource() }
                Column(
                    modifier =
                        Modifier
                            .widthIn(min = 440.dp, max = 600.dp)
                            .clip(cardShape)
                            .background(colors.surface, cardShape)
                            .border(BorderStroke(2.dp, colors.focusRingStrong), cardShape)
                            .clickable(interactionSource = cardInteraction, indication = null, onClick = {})
                            .focusProperties { exit = { FocusRequester.Cancel } }
                            .focusGroup()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.focusProperties { canFocus = false },
                    ) {
                        Icon(
                            imageVector = if (targetTrack == null) Icons.Rounded.LibraryMusic else Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = null,
                            tint = colors.primaryAccent,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text =
                                    if (targetTrack == null || creatingMode) {
                                        stringResource(R.string.playlist_create_title)
                                    } else {
                                        stringResource(R.string.playlist_add_dialog_title)
                                    },
                                color = colors.primaryText,
                                fontSize = AlunaraTypography.sectionTitle(prefs.textScale),
                                fontWeight = FontWeight.Bold,
                            )
                            if (targetTrack != null) {
                                Text(
                                    text = "${targetTrack.title} · ${targetTrack.primaryArtistName}",
                                    color = colors.mutedText,
                                    fontSize = AlunaraTypography.caption(prefs.textScale),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    when {
                        authSession !is AuthSessionState.Authenticated -> {
                            Text(
                                text = stringResource(R.string.playlist_sync_guest_prompt),
                                color = colors.mutedText,
                                fontSize = AlunaraTypography.body(prefs.textScale),
                                lineHeight = AlunaraTypography.body(prefs.textScale) * 1.4f,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TvFocusableButton(
                                    text = stringResource(R.string.login_webview_button),
                                    icon = Icons.AutoMirrored.Rounded.Login,
                                    highlighted = true,
                                    onClick = {
                                        onDismiss()
                                        onOpenSignIn()
                                    },
                                    modifier = Modifier.focusRequester(primaryRequester),
                                )
                                TvFocusableButton(
                                    text = stringResource(R.string.cancel),
                                    onClick = onDismiss,
                                )
                            }
                        }

                        creatingMode -> {
                            val inputShape = RoundedCornerShape(AlunaraDimens.RadiusControl)
                            val inputBorder = if (titleFocused) colors.focusRingStrong else colors.secondarySurface
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = AlunaraDimens.ControlHeight)
                                        .background(colors.background, inputShape)
                                        .border(BorderStroke(colors.focusBorderWidth, inputBorder), inputShape)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (newTitle.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.playlist_create_hint),
                                        color = colors.dimmedLyrics,
                                        fontSize = AlunaraTypography.body(prefs.textScale),
                                    )
                                }
                                BasicTextField(
                                    value = newTitle,
                                    onValueChange = { newTitle = it },
                                    singleLine = true,
                                    textStyle =
                                        TextStyle(
                                            color = colors.primaryText,
                                            fontSize = AlunaraTypography.body(prefs.textScale),
                                        ),
                                    cursorBrush = SolidColor(colors.primaryAccent),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions =
                                        KeyboardActions(
                                            onDone = {
                                                keyboardController?.hide()
                                                val trimmed = newTitle.trim()
                                                if (trimmed.isNotEmpty()) {
                                                    onCreatePlaylist(trimmed, targetTrack)
                                                    onDismiss()
                                                }
                                            },
                                        ),
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .focusRequester(titleInputRequester)
                                            .onFocusChanged { titleFocused = it.isFocused },
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TvFocusableButton(
                                    text = stringResource(R.string.playlist_create_confirm),
                                    icon = Icons.Rounded.Add,
                                    highlighted = true,
                                    enabled = newTitle.isNotBlank(),
                                    onClick = {
                                        keyboardController?.hide()
                                        val trimmed = newTitle.trim()
                                        if (trimmed.isNotEmpty()) {
                                            onCreatePlaylist(trimmed, targetTrack)
                                            onDismiss()
                                        }
                                    },
                                )
                                TvFocusableButton(
                                    text = stringResource(R.string.cancel),
                                    onClick = {
                                        keyboardController?.hide()
                                        if (targetTrack != null) creatingMode = false else onDismiss()
                                    },
                                )
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp),
                            ) {
                                item(key = "create-new-playlist") {
                                    TvFocusableButton(
                                        text = stringResource(R.string.playlist_create_new_option),
                                        icon = Icons.Rounded.Add,
                                        highlighted = true,
                                        onClick = { creatingMode = true },
                                        modifier = Modifier.fillMaxWidth().focusRequester(primaryRequester),
                                    )
                                }
                                itemsIndexed(
                                    items = playlists,
                                    key = { _, pl -> "dlg-pl-${pl.id}" },
                                ) { _, playlist ->
                                    PlaylistChoiceRow(
                                        playlist = playlist,
                                        onClick = {
                                            if (targetTrack != null) {
                                                onAddTrackToPlaylist(playlist, targetTrack)
                                            }
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TvFocusableButton(
                                    text = stringResource(R.string.cancel),
                                    onClick = onDismiss,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistChoiceRow(
    playlist: PlaylistSummary,
    onClick: () -> Unit,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(AlunaraDimens.RadiusControl)
    val bgColor = if (focused) colors.focusedButtonBg else colors.secondarySurface
    val fgColor = if (focused) colors.focusedButtonContent else colors.primaryText
    val subColor = if (focused) colors.focusedButtonContent.copy(alpha = 0.78f) else colors.mutedText
    val borderColor = if (focused) colors.focusRingStrong else Color.Transparent

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bgColor, shape)
                .border(BorderStroke(colors.focusBorderWidth, borderColor), shape)
                .semantics { role = Role.Button }
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvArtwork(url = playlist.thumbnailUrl, contentDescription = null, size = 38.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                color = fgColor,
                fontSize = AlunaraTypography.body(prefs.textScale),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = playlist.trackCountText ?: playlist.author
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = subColor,
                    fontSize = AlunaraTypography.caption(prefs.textScale),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
