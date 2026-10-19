package com.sortr.app.ui.whatsapp

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.ui.albums.AlbumThumb
import com.sortr.app.ui.common.formatBytes
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.design.GroupedCard
import com.sortr.app.ui.design.GroupedSection
import com.sortr.app.ui.design.IconBadge
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.ListRow
import com.sortr.app.ui.design.PillButton
import com.sortr.app.ui.design.ScreenPadding
import com.sortr.app.ui.design.SectionTitle
import com.sortr.app.ui.design.SmartTile
import com.sortr.app.ui.design.TileGrid
import com.sortr.app.ui.library.color
import com.sortr.app.ui.library.countSummary
import com.sortr.app.ui.library.icon
import com.sortr.app.ui.theme.palette
import com.sortr.domain.model.Album
import com.sortr.domain.model.MediaSource
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class WhatsAppHubState(
    val loaded: Boolean = false,
    val total: Int = 0,
    val toReview: Int = 0,
    val sizeBytes: Long = 0,
    /** Items still to sort in each part: photos, videos, sent, private. */
    val toReviewByPart: Map<MediaSource, Int> = emptyMap(),
    val albums: List<Album> = emptyList(),
) {
    val sortedFraction: Float get() = if (total == 0) 0f else 1f - toReview.toFloat() / total
}

@HiltViewModel
class WhatsAppViewModel @Inject constructor(library: LibraryRepository) : ViewModel() {
    val state: StateFlow<WhatsAppHubState> = combine(library.observeSources(), library.observeAlbums()) { sources, albums ->
        val whatsApp = albums.filter { MediaSource.WHATSAPP.matches(it.relativePath) }
        WhatsAppHubState(
            loaded = true,
            total = whatsApp.sumOf { it.count },
            toReview = whatsApp.sumOf { it.toReview },
            sizeBytes = whatsApp.sumOf { it.sizeBytes },
            toReviewByPart = sources.filter { it.source.partOf == MediaSource.WHATSAPP }.associate { it.source to it.toReview },
            albums = whatsApp.sortedByDescending { it.count },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhatsAppHubState())
}

/** WhatsApp's own page: what's left to sort, its parts, its folders, and access to hidden media. */
@Composable
fun WhatsAppScreen(
    onBack: () -> Unit,
    onStartSession: (filterKey: String) -> Unit,
    onOpenAlbum: (relativePath: String) -> Unit,
    viewModel: WhatsAppViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LargeTitleScaffold(
        title = stringResource(R.string.whatsapp_name),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!state.loaded) {
            CircularProgressIndicator(Modifier.padding(padding).padding(32.dp))
            return@LargeTitleScaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item { HubHero(state, onStart = { onStartSession(SessionFilter.Source(MediaSource.WHATSAPP).key) }) }
            item { WhatsAppPromptCard(snackbarHostState = snackbarHostState, alwaysOffer = true) }
            item { SectionTitle(stringResource(R.string.home_section_browse)) }
            item {
                val parts = listOf(MediaSource.WHATSAPP_PHOTOS, MediaSource.WHATSAPP_VIDEOS, MediaSource.WHATSAPP_SENT, MediaSource.WHATSAPP_PRIVATE)
                TileGrid(
                    parts.map<MediaSource, @Composable (Modifier) -> Unit> { part ->
                        { modifier ->
                            SmartTile(
                                title = stringResource(part.tileLabelRes),
                                icon = part.icon,
                                color = part.color,
                                count = formatCount(state.toReviewByPart[part] ?: 0),
                                onClick = { onStartSession(SessionFilter.Source(part).key) },
                                modifier = modifier,
                            )
                        }
                    },
                )
            }
            if (state.albums.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.whatsapp_hub_folders)) }
                item {
                    GroupedSection(separatorIndent = 16.dp + 44.dp + 14.dp) {
                        state.albums.forEach { album ->
                            row {
                                ListRow(
                                    title = album.name,
                                    subtitle = countSummary(album.count, album.toReview),
                                    leading = { AlbumThumb(album, Modifier.size(44.dp)) },
                                    onClick = { onOpenAlbum(album.relativePath) },
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
private fun HubHero(state: WhatsAppHubState, onStart: () -> Unit) {
    val context = LocalContext.current
    GroupedCard {
        Column(Modifier.padding(20.dp)) {
            if (state.total == 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.AutoMirrored.Rounded.Chat, MaterialTheme.palette.green, size = 44.dp, shape = CircleShape)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.whatsapp_hub_empty_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.whatsapp_hub_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                return@Column
            }
            Text(formatCount(state.toReview), style = MaterialTheme.typography.displaySmall, maxLines = 1, softWrap = false)
            Text(
                text = pluralStringResource(R.plurals.whatsapp_hub_caption, state.toReview),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { state.sortedFraction },
                color = MaterialTheme.palette.green,
                trackColor = MaterialTheme.palette.fill,
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.whatsapp_hub_of_total, formatCount(state.total), formatBytes(context, state.sizeBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.toReview > 0) {
                Spacer(Modifier.height(18.dp))
                PillButton(
                    text = stringResource(R.string.home_hero_start),
                    onClick = onStart,
                    icon = Icons.Rounded.Style,
                    color = MaterialTheme.palette.green,
                )
            }
        }
    }
}

private val MediaSource.tileLabelRes: Int
    get() = when (this) {
        MediaSource.WHATSAPP_PHOTOS -> R.string.custom_type_photos
        MediaSource.WHATSAPP_VIDEOS -> R.string.tile_videos
        MediaSource.WHATSAPP_SENT -> R.string.whatsapp_tile_sent
        MediaSource.WHATSAPP_PRIVATE -> R.string.whatsapp_tile_private
        else -> error("Not a WhatsApp part: $this")
    }
