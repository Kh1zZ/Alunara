package com.alunara.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import com.alunara.app.ui.components.TvIconButton
import com.alunara.app.ui.components.TvSongRow
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.core.domain.model.QueueState

@Composable
fun LibraryScreen(
    queueState: QueueState,
    onPlayAt: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.library_headline),
                color = AlunaraPalette.PrimaryText,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (queueState.items.isNotEmpty()) {
                TvFocusableButton(
                    text = stringResource(R.string.clear_queue),
                    icon = Icons.Rounded.DeleteSweep,
                    onClick = onClearQueue,
                )
            }
        }

        Text(
            text = stringResource(R.string.library_guest_note),
            color = AlunaraPalette.MutedText,
            fontSize = 13.sp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(AlunaraPalette.Surface.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
        )

        if (queueState.items.isEmpty()) {
            Text(
                text = stringResource(R.string.queue_empty),
                color = AlunaraPalette.MutedText,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(queueState.items.size, key = { idx -> queueState.items[idx].queueItemId }) { index ->
                    val item = queueState.items[index]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TvSongRow(
                            track = item.track,
                            isActive = index == queueState.currentIndex,
                            onPlayClick = { onPlayAt(index) },
                            modifier = Modifier.weight(1f),
                        )
                        TvIconButton(
                            icon = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.move_up),
                            enabled = index > 0,
                            onClick = { onMoveItem(index, index - 1) },
                        )
                        TvIconButton(
                            icon = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.move_down),
                            enabled = index < queueState.items.lastIndex,
                            onClick = { onMoveItem(index, index + 1) },
                        )
                        TvIconButton(
                            icon = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.remove_item),
                            onClick = { onRemoveAt(index) },
                        )
                    }
                }
            }
        }
    }
}
