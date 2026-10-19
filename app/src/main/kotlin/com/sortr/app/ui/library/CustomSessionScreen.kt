package com.sortr.app.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.ui.common.formatBytes
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.common.formatMonth
import com.sortr.app.ui.common.formatYear
import com.sortr.app.ui.design.Capsule
import com.sortr.app.ui.design.CheckRow
import com.sortr.app.ui.design.GroupedCard
import com.sortr.app.ui.design.GroupedSection
import com.sortr.app.ui.design.ListRow
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.PillButton
import com.sortr.app.ui.design.ScreenPadding
import com.sortr.app.ui.design.SegmentedControl
import com.sortr.domain.model.Album
import com.sortr.domain.model.DateBucket
import com.sortr.domain.model.MediaTypeFilter
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomSessionViewModel @Inject constructor(private val library: LibraryRepository) : ViewModel() {

    val filter = MutableStateFlow(SessionFilter.Custom())

    val albums: StateFlow<List<Album>> = library.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val buckets: StateFlow<List<DateBucket>> = library.observeDateBuckets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** How many items the session would have; null while counting. */
    val count: StateFlow<Int?> = filter.debounce(COUNT_DEBOUNCE_MS)
        .mapLatest { library.countToReview(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(change: (SessionFilter.Custom) -> SessionFilter.Custom) {
        filter.value = change(filter.value)
    }

    private companion object {
        const val COUNT_DEBOUNCE_MS = 150L
    }
}

@Composable
fun CustomSessionScreen(
    onBack: () -> Unit,
    onStartSession: (filterKey: String) -> Unit,
    viewModel: CustomSessionViewModel = hiltViewModel(),
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val buckets by viewModel.buckets.collectAsStateWithLifecycle()
    val count by viewModel.count.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAllAlbums by rememberSaveable { mutableStateOf(false) }
    val years = buckets.map { it.year }.distinct()
    val months = filter.year?.let { year -> buckets.filter { it.year == year }.map { it.month }.sorted() }.orEmpty()

    LargeTitleScaffold(
        title = stringResource(R.string.custom_title),
        onBack = onBack,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                val total = count
                PillButton(
                    text = when {
                        total == null -> stringResource(R.string.action_start)
                        total == 0 -> stringResource(R.string.custom_nothing)
                        else -> pluralStringResource(R.plurals.custom_start, total, formatCount(total))
                    },
                    enabled = (total ?: 0) > 0,
                    onClick = { onStartSession(filter.key) },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(16.dp),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item {
                Section(stringResource(R.string.custom_type)) {
                    SegmentedControl(
                        options = MediaTypeFilter.entries,
                        selected = filter.mediaType,
                        label = { stringResource(it.labelRes) },
                        onSelect = { type -> viewModel.update { it.copy(mediaType = type) } },
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            item {
                Section(stringResource(R.string.custom_date)) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        CapsuleRow {
                            Capsule(
                                text = stringResource(R.string.custom_any_year),
                                selected = filter.year == null,
                                onClick = { viewModel.update { it.copy(year = null, month = null) } },
                            )
                            years.forEach { year ->
                                Capsule(
                                    text = formatYear(year),
                                    selected = filter.year == year,
                                    onClick = { viewModel.update { it.copy(year = year, month = null) } },
                                )
                            }
                        }
                        if (months.isNotEmpty()) {
                            CapsuleRow(Modifier.padding(top = 10.dp)) {
                                Capsule(
                                    text = stringResource(R.string.custom_any_month),
                                    selected = filter.month == null,
                                    onClick = { viewModel.update { it.copy(month = null) } },
                                )
                                months.forEach { month ->
                                    Capsule(
                                        text = formatMonth(month),
                                        selected = filter.month == month,
                                        onClick = { viewModel.update { it.copy(month = month) } },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                val shown = if (showAllAlbums) albums else albums.sortedByDescending { it.count }.take(ALBUMS_PREVIEW)
                GroupedSection(header = stringResource(R.string.custom_albums), separatorIndent = 16.dp) {
                    row {
                        CheckRow(
                            title = stringResource(R.string.custom_any_album),
                            checked = filter.albums.isEmpty(),
                            onClick = { viewModel.update { it.copy(albums = emptySet()) } },
                        )
                    }
                    shown.forEach { album ->
                        row {
                            CheckRow(
                                title = album.name,
                                subtitle = formatCount(album.count),
                                checked = album.relativePath in filter.albums,
                                onClick = {
                                    viewModel.update {
                                        val path = album.relativePath
                                        it.copy(albums = if (path in it.albums) it.albums - path else it.albums + path)
                                    }
                                },
                            )
                        }
                    }
                    if (!showAllAlbums && albums.size > ALBUMS_PREVIEW) {
                        row {
                            ListRow(
                                title = stringResource(R.string.custom_show_all_albums),
                                titleColor = MaterialTheme.colorScheme.primary,
                                showChevron = false,
                                onClick = { showAllAlbums = true },
                            )
                        }
                    }
                }
            }
            item {
                Section(stringResource(R.string.custom_size)) {
                    CapsuleRow(Modifier.padding(vertical = 12.dp)) {
                        Capsule(
                            text = stringResource(R.string.custom_size_any),
                            selected = filter.minSizeBytes == null,
                            onClick = { viewModel.update { it.copy(minSizeBytes = null) } },
                        )
                        SIZE_STEPS.forEach { bytes ->
                            Capsule(
                                text = stringResource(R.string.custom_size_over, formatBytes(context, bytes)),
                                selected = filter.minSizeBytes == bytes,
                                onClick = { viewModel.update { it.copy(minSizeBytes = bytes) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Small grey heading with a card underneath, matching [GroupedSection]. */
@Composable
private fun Section(header: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = header.uppercase(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        )
        GroupedCard { content() }
    }
}

@Composable
private fun CapsuleRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) { content() }
}

private val MediaTypeFilter.labelRes: Int
    get() = when (this) {
        MediaTypeFilter.ALL -> R.string.custom_type_all
        MediaTypeFilter.PHOTOS -> R.string.custom_type_photos
        MediaTypeFilter.VIDEOS -> R.string.custom_type_videos
    }

private const val ALBUMS_PREVIEW = 6
// Round decimal sizes, so they read as "5 MB", "20 MB" and "100 MB".
private val SIZE_STEPS = listOf(5_000_000L, 20_000_000L, 100_000_000L)
