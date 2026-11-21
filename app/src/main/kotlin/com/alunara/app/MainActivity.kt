package com.alunara.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.app.ui.theme.AlunaraTheme

private enum class TvDestination {
    HOME,
    SEARCH,
    LIBRARY,
    SETTINGS,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlunaraTheme {
                AlunaraTvShell()
            }
        }
    }
}

@Composable
private fun AlunaraTvShell() {
    var selectedDestination by rememberSaveable { mutableStateOf(TvDestination.HOME) }
    var railFocused by remember { mutableStateOf(false) }
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        initialFocusRequester.requestFocus()
    }

    // ~5% TV overscan safe margin (UI/UX Spec §2.2)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AlunaraPalette.Background)
                .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            CollapsibleNavigationRail(
                selected = selectedDestination,
                expanded = railFocused,
                firstItemRequester = initialFocusRequester,
                onRailFocusChanged = { railFocused = it },
                onSelect = { selectedDestination = it },
            )

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(AlunaraPalette.Surface, RoundedCornerShape(16.dp))
                        .padding(32.dp),
            ) {
                DestinationContent(destination = selectedDestination)
            }
        }
    }
}

@Composable
private fun CollapsibleNavigationRail(
    selected: TvDestination,
    expanded: Boolean,
    firstItemRequester: FocusRequester,
    onRailFocusChanged: (Boolean) -> Unit,
    onSelect: (TvDestination) -> Unit,
) {
    val railWidth = if (expanded) 220.dp else 76.dp
    Column(
        modifier =
            Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(AlunaraPalette.Surface, RoundedCornerShape(16.dp))
                .padding(12.dp)
                .onFocusChanged { onRailFocusChanged(it.hasFocus) },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvDestination.entries.forEachIndexed { index, dest ->
            RailItem(
                shortTag =
                    when (dest) {
                        TvDestination.HOME -> "H"
                        TvDestination.SEARCH -> "S"
                        TvDestination.LIBRARY -> "L"
                        TvDestination.SETTINGS -> "G"
                    },
                label =
                    stringResource(
                        when (dest) {
                            TvDestination.HOME -> R.string.nav_home
                            TvDestination.SEARCH -> R.string.nav_search
                            TvDestination.LIBRARY -> R.string.nav_library
                            TvDestination.SETTINGS -> R.string.nav_settings
                        },
                    ),
                selected = selected == dest,
                expanded = expanded,
                modifier = if (index == 0) Modifier.focusRequester(firstItemRequester) else Modifier,
                onClick = { onSelect(dest) },
            )
        }
    }
}

@Composable
private fun RailItem(
    shortTag: String,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val bgColor =
        when {
            selected -> AlunaraPalette.SecondarySurface
            else -> Color.Transparent
        }
    val borderStroke =
        if (focused) {
            BorderStroke(2.dp, AlunaraPalette.FocusOutline)
        } else {
            BorderStroke(1.dp, Color.Transparent)
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(bgColor, shape)
                .border(borderStroke, shape)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = shortTag,
            color = if (selected || focused) AlunaraPalette.PrimaryAccent else AlunaraPalette.MutedText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        if (expanded) {
            Text(
                text = label,
                color = AlunaraPalette.PrimaryText,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DestinationContent(destination: TvDestination) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.status_badge),
            color = AlunaraPalette.PrimaryAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )

        val (headlineRes, bodyRes) =
            when (destination) {
                TvDestination.HOME -> R.string.home_headline to R.string.home_body
                TvDestination.SEARCH -> R.string.search_headline to R.string.search_body
                TvDestination.LIBRARY -> R.string.library_headline to R.string.library_body
                TvDestination.SETTINGS -> R.string.settings_headline to R.string.attribution_text
            }

        Text(
            text = stringResource(headlineRes),
            color = AlunaraPalette.PrimaryText,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = stringResource(bodyRes),
            color = AlunaraPalette.MutedText,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.compliance_summary),
            color = AlunaraPalette.PrimaryText,
            fontSize = 14.sp,
        )
    }
}
