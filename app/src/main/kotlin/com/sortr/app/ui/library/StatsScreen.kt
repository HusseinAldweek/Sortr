package com.sortr.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.ui.common.formatBytes
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.common.formatYear
import com.sortr.app.ui.design.GroupedCard
import com.sortr.app.ui.design.GroupedSection
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.ListRow
import com.sortr.app.ui.design.ScreenPadding
import com.sortr.app.ui.theme.palette
import com.sortr.domain.model.LibraryStats
import com.sortr.domain.model.LibrarySummary
import com.sortr.domain.model.QuickCounts
import com.sortr.domain.repository.LibraryRepository
import com.sortr.domain.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.roundToInt

data class YearCount(val year: Int, val count: Int)

data class StatsUiState(
    val stats: LibraryStats = LibraryStats(),
    val summary: LibrarySummary = LibrarySummary(),
    val counts: QuickCounts = QuickCounts(),
    val years: List<YearCount> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(library: LibraryRepository, photos: PhotoRepository) : ViewModel() {
    val state: StateFlow<StatsUiState> = combine(
        library.observeStats(),
        photos.observeSummary(),
        library.observeQuickCounts(),
        library.observeDateBuckets(),
    ) { stats, summary, counts, buckets ->
        StatsUiState(
            stats = stats,
            summary = summary,
            counts = counts,
            years = buckets.groupBy { it.year }.map { (year, months) -> YearCount(year, months.sumOf { it.count }) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}

@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val palette = MaterialTheme.palette
    val stats = state.stats
    val summary = state.summary

    LargeTitleScaffold(title = stringResource(R.string.stats_title), onBack = onBack) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item { OverviewCard(state) }
            item {
                GroupedSection(header = stringResource(R.string.stats_section_decisions)) {
                    row { StatRow(stringResource(R.string.stats_kept), stats.kept, Icons.Rounded.Check, palette.green) }
                    row { StatRow(stringResource(R.string.stats_trashed), stats.movedToTrash, Icons.Rounded.Delete, palette.red) }
                    row { StatRow(stringResource(R.string.stats_deleted), stats.deletedFromPhone, Icons.Rounded.DeleteForever, palette.orange) }
                    row { StatRow(stringResource(R.string.stats_vaulted), stats.movedToVault, Icons.Rounded.Lock, palette.indigo) }
                    row { StatRow(stringResource(R.string.stats_skipped), stats.skipped, Icons.Rounded.ArrowDownward, palette.gray) }
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.stats_section_library)) {
                    row { StatRow(stringResource(R.string.stats_photos), summary.photos, Icons.Rounded.Photo, palette.blue) }
                    row { StatRow(stringResource(R.string.stats_videos), summary.videos, Icons.Rounded.PlayCircle, palette.pink) }
                    row { StatRow(stringResource(R.string.stats_albums), state.counts.albums, Icons.Rounded.Collections, palette.teal) }
                    row { StatRow(stringResource(R.string.stats_left), summary.toReview, Icons.Rounded.HourglassEmpty, palette.yellow) }
                    row {
                        ListRow(
                            title = stringResource(R.string.stats_in_trash),
                            icon = Icons.Rounded.Delete,
                            iconColor = palette.red,
                            value = stringResource(
                                R.string.count_with_review,
                                formatCount(summary.trashedCount),
                                formatBytes(context, summary.trashedBytes),
                            ),
                        )
                    }
                    row { StatRow(stringResource(R.string.stats_in_vault), stats.inVault, Icons.Rounded.Lock, palette.indigo) }
                }
            }
            if (state.years.isNotEmpty()) {
                item {
                    val most = state.years.maxOf { it.count }.coerceAtLeast(1)
                    GroupedSection(header = stringResource(R.string.stats_section_years), separatorIndent = 16.dp) {
                        state.years.forEach { year -> row { YearBar(year, most) } }
                    }
                }
            }
        }
    }
}

/** Big headline numbers: how much is sorted and how much space was freed. */
@Composable
private fun OverviewCard(state: StatsUiState) {
    val context = LocalContext.current
    val summary = state.summary
    val sortedFraction = summary.sortedFraction
    GroupedCard {
        Column(Modifier.padding(20.dp)) {
            Row {
                BigNumber(formatCount(state.stats.reviewed), stringResource(R.string.stats_reviewed), Modifier.weight(1f))
                BigNumber(formatBytes(context, state.stats.freedBytes), stringResource(R.string.stats_freed), Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                progress = { sortedFraction },
                color = MaterialTheme.palette.green,
                trackColor = MaterialTheme.palette.fill,
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.stats_sorted_percent, formatCount((sortedFraction * 100).roundToInt())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BigNumber(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1, softWrap = false)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatRow(title: String, value: Int, icon: ImageVector, color: Color) {
    ListRow(title = title, icon = icon, iconColor = color, value = formatCount(value))
}

@Composable
private fun YearBar(year: YearCount, most: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(formatYear(year.year), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(56.dp))
        Box(
            Modifier
                .weight(1f)
                .height(10.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(year.count.toFloat() / most)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(formatCount(year.count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
