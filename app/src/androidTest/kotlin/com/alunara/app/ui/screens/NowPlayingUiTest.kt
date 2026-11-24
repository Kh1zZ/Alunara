package com.alunara.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.alunara.app.R
import com.alunara.app.playback.PlaybackSessionUiState
import com.alunara.app.ui.theme.AlunaraDisplayPrefs
import com.alunara.app.ui.theme.AlunaraTheme
import com.alunara.core.domain.model.QueueItem
import com.alunara.core.domain.model.QueueState
import com.alunara.core.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NowPlayingUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactLayoutKeepsTransportVisibleAndSeekAccessible() {
        var seekTarget = -1L
        showPlayer(onSeekTo = { seekTarget = it })

        composeRule.onNodeWithContentDescription(string(R.string.action_prev)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.action_play)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.action_next)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.action_favorite)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.action_start_radio)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.seek_bar_label))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(8_000f) }
        composeRule.runOnIdle { assertEquals(8_000L, seekTarget) }
    }

    @Test
    fun backClosesClearQueueConfirmationAndRestoresFocus() {
        showPlayer()
        composeRule.onNodeWithText(string(R.string.now_playing_tab_queue, 2)).performClick()
        val clear = composeRule.onNodeWithContentDescription(string(R.string.clear_queue))
        clear.performClick()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        clear.assertIsFocused()
    }

    private fun showPlayer(onSeekTo: (Long) -> Unit = {}) {
        val queue = QueueState(
            items = listOf(
                QueueItem("first", Track(id = "track-first", title = "First track")),
                QueueItem("second", Track(id = "track-second", title = "Second track")),
            ),
            currentIndex = 0,
        )
        composeRule.setContent {
            AlunaraTheme(displayPrefs = AlunaraDisplayPrefs(largeText = true, reduceMotion = true)) {
                Box(Modifier.size(width = 640.dp, height = 360.dp)) {
                    NowPlayingScreen(
                        sessionState = PlaybackSessionUiState(queueState = queue, durationMs = 60_000L),
                        onTogglePlayPause = {},
                        onSkipPrevious = {},
                        onSkipNext = {},
                        onSeekBy = {},
                        onSeekTo = onSeekTo,
                        onToggleFavorite = {},
                        onStartRadio = {},
                        onSwitchLyricsProvider = {},
                    )
                }
            }
        }
    }

    private fun string(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)
}
