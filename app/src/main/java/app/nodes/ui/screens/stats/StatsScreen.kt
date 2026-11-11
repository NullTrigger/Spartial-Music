/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.nodes.R
import app.nodes.core.data.LibraryRepository
import app.nodes.core.model.ArtistStat
import app.nodes.core.model.ListeningStats
import app.nodes.core.model.TrackStat
import app.nodes.ui.components.Artwork
import app.nodes.ui.components.EmptyState
import app.nodes.ui.components.GeneratedArt
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.glass
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesGreyDim
import app.nodes.ui.theme.NodesAccent
import app.nodes.ui.theme.NodesWhite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/**
 * The windows the stats screen can look back over. "This month" anchors on the
 * first day of the month in the device's zone, Replay style; the month name is
 * exposed separately for the headline.
 */
enum class StatsPeriod(val labelRes: Int) {
    THIS_MONTH(R.string.stats_period_this_month),
    LAST_30_DAYS(R.string.stats_period_last_30),
    ALL_TIME(R.string.stats_period_all_time),
    ;

    /** Inclusive start of the window, in epoch millis. */
    fun since(now: Long): Long = when (this) {
        THIS_MONTH -> Instant.ofEpochMilli(now)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        LAST_30_DAYS -> now - THIRTY_DAYS_MS
        ALL_TIME -> 0L
    }

    /**
     * The month name for the headline ("… min in May"), or null for windows
     * that are not bound to one calendar month.
     */
    fun monthLabel(now: Long, locale: Locale = Locale.getDefault()): String? {
        if (this != THIS_MONTH) return null
        return Instant.ofEpochMilli(now)
            .atZone(ZoneId.systemDefault())
            .month
            .getDisplayName(TextStyle.FULL, locale)
    }

    private companion object {
        const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }
}

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.THIS_MONTH,
    val stats: ListeningStats = ListeningStats(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    library: LibraryRepository,
) : ViewModel() {

    private val _period = MutableStateFlow(StatsPeriod.THIS_MONTH)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    /**
     * Re-queries when the chosen period changes and keeps emitting while the
     * screen is open, so the numbers tick up live as plays are recorded.
     */
    val state: StateFlow<StatsUiState> = _period
        .flatMapLatest { period ->
            library.listeningStats(period.since(System.currentTimeMillis()))
                .map { StatsUiState(period = period, stats = it, loading = false) }
                .onStart { emit(StatsUiState(period = period, loading = true)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(),
        )

    fun setPeriod(value: StatsPeriod) {
        _period.value = value
    }
}

/**
 * Listening stats, a Replay-style readout in the Nodes glass language.
 *
 * The headline card carries the big estimated minutes count, a horizontal rail
 * ranks artists on generated covers with oversized rank numbers, and a bare
 * numbered list ranks songs. Nothing here touches the network: every number is
 * a Room aggregation over the play history on this device.
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats = state.stats

    Column(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        PageHeader(
            title = stringResource(R.string.stats_title),
            subtitle = stringResource(R.string.stats_subtitle),
            back = onBack,
        )

        PeriodChips(
            selected = state.period,
            onSelect = viewModel::setPeriod,
        )

        if (!state.loading && stats.plays == 0) {
            EmptyState(
                title = stringResource(R.string.stats_title),
                description = stringResource(R.string.stats_empty),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp),
            ) {
                item(key = "headline") { HeadlineCard(state) }

                if (stats.topArtists.isNotEmpty()) {
                    item(key = "artists-head") {
                        SectionHeader(stringResource(R.string.stats_top_artists))
                    }
                    item(key = "artists-rail") {
                        ArtistRail(stats.topArtists)
                    }
                }

                if (stats.topTracks.isNotEmpty()) {
                    item(key = "tracks-head") {
                        SectionHeader(stringResource(R.string.stats_top_tracks))
                    }
                    itemsIndexed(items = stats.topTracks, key = { _, stat -> stat.track.id }) { index, stat ->
                        TrackStatRow(
                            index = index,
                            stat = stat,
                        )
                    }
                }

                item(key = "note") {
                    Text(
                        text = stringResource(R.string.stats_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = NodesGreyDim,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

/** Same pill treatment as the library tabs; selection is the brighter film. */
@Composable
private fun PeriodChips(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsPeriod.entries.forEach { period ->
            val isChosen = period == selected
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(
                        if (isChosen) Color.White.copy(alpha = 0.16f)
                        else Color.White.copy(alpha = 0.05f),
                    )
                    .clickable { onSelect(period) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(period.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isChosen) NodesWhite else NodesGrey,
                )
            }
        }
    }
}

/** The big minutes card: period kicker, Replay headline, play count under it. */
@Composable
private fun HeadlineCard(state: StatsUiState) {
    val minutes = state.stats.minutes

    Column(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(state.period.labelRes).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = NodesAccent,
        )
        val headline = if (minutes > 0) {
            val formatted = NumberFormat.getIntegerInstance().format(minutes)
            val month = state.period.monthLabel(System.currentTimeMillis())
            if (month != null) {
                stringResource(R.string.stats_headline_month, formatted, month)
            } else {
                stringResource(R.string.stats_headline, formatted)
            }
        } else {
            stringResource(R.string.stats_headline_zero)
        }
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 34.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.8).sp,
                fontWeight = FontWeight.Bold,
            ),
            color = NodesWhite,
        )
        Text(
            text = pluralStringResource(R.plurals.stats_plays, state.stats.plays, state.stats.plays),
            style = MaterialTheme.typography.bodySmall,
            color = NodesGrey,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = NodesGreyDim,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 12.dp),
    )
}

@Composable
private fun ArtistRail(stats: List<ArtistStat>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items = stats, key = { _, stat -> stat.artist }) { index, stat ->
            ArtistCard(rank = index + 1, stat = stat)
        }
    }
}

/**
 * A ranked artist card: seeded gradient cover, oversized rank number over a
 * left-side scrim, name and play count underneath. No real artwork anywhere.
 */
@Composable
private fun ArtistCard(rank: Int, stat: ArtistStat) {
    Box(
        Modifier
            .width(142.dp)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(118.dp),
            ) {
                GeneratedArt(
                    title = stat.artist,
                    modifier = Modifier.matchParentSize(),
                    showInitial = false,
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(NodesBlack.copy(alpha = 0.55f), Color.Transparent),
                            ),
                        ),
                )
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 46.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.Black,
                    ),
                    color = if (rank <= 3) NodesAccent else NodesWhite.copy(alpha = 0.92f),
                    modifier = Modifier.padding(start = 12.dp, top = 6.dp),
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 2.dp),
            ) {
                Text(
                    text = stat.artist,
                    style = MaterialTheme.typography.titleSmall,
                    color = NodesWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.stats_plays, stat.plays, stat.plays),
                    style = MaterialTheme.typography.bodySmall,
                    color = NodesGrey,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A ranked song row: big rank digits (teal for the podium), artwork, title and
 * artist, the play count on the right. Deliberately not a card - the rows
 * breathe on the dark backdrop like the rest of the app.
 */
@Composable
private fun TrackStatRow(
    index: Int,
    stat: TrackStat,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 20.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (index + 1 <= 3) NodesAccent else NodesGreyDim,
            )
        }
        Spacer(Modifier.width(10.dp))
        Artwork(
            artworkUri = stat.track.artworkUri,
            title = stat.track.title,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            targetPx = 200,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stat.track.title,
                style = MaterialTheme.typography.titleSmall,
                color = NodesWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stat.track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = NodesGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = pluralStringResource(R.plurals.stats_plays, stat.plays, stat.plays),
            style = MaterialTheme.typography.labelMedium,
            color = NodesAccent,
        )
    }
}
