package com.sortr.app.ui.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.sortr.app.R
import com.sortr.app.image.MediaThumbnail
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.design.GroupedSection
import com.sortr.app.ui.design.ListRow
import com.sortr.app.ui.theme.palette
import com.sortr.domain.model.Album
import com.sortr.domain.model.AlbumPaths

/**
 * Sheet for picking where items go: "New album…" first, then [albums]. Albums Android won't
 * let apps move items into are shown but can't be picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPickerSheet(
    title: String,
    albums: List<Album>,
    onPick: (Album) -> Unit,
    onNewAlbum: () -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                if (body != null) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                }
            }
            item {
                GroupedSection(modifier = Modifier.padding(vertical = 8.dp)) {
                    row {
                        ListRow(
                            title = stringResource(R.string.merge_new_album),
                            icon = Icons.Rounded.Add,
                            iconColor = MaterialTheme.palette.blue,
                            titleColor = MaterialTheme.colorScheme.primary,
                            showChevron = false,
                            onClick = onNewAlbum,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
            item {
                GroupedSection {
                    albums.forEach { album ->
                        row {
                            val enabled = album.canReceivePhotos
                            ListRow(
                                title = album.name,
                                subtitle = if (enabled) {
                                    pluralStringResource(R.plurals.count_items, album.count, formatCount(album.count))
                                } else {
                                    stringResource(R.string.album_cant_receive)
                                },
                                leading = { AlbumThumb(album, Modifier.size(44.dp)) },
                                showChevron = false,
                                onClick = if (enabled) ({ onPick(album) }) else null,
                                modifier = Modifier.alpha(if (enabled) 1f else 0.45f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Asks for an album name. [initial] pre-fills it, e.g. for a rename. */
@Composable
fun AlbumNameDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    body: String? = null,
    confirmLabel: String = stringResource(R.string.action_create),
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val cleaned = AlbumPaths.cleanName(name)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (body != null) {
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_INPUT) },
                    label = { Text(stringResource(R.string.album_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { cleaned?.let(onConfirm) }, enabled = cleaned != null) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Rounded square cover of an album. */
@Composable
fun AlbumThumb(album: Album, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val cover = album.coverUri
        if (cover != null) {
            AsyncImage(
                model = MediaThumbnail(cover.toUri()),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private const val MAX_INPUT = 80
