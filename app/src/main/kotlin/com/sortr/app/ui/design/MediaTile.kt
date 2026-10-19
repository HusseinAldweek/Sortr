package com.sortr.app.ui.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.sortr.app.R
import com.sortr.app.image.MediaThumbnail
import com.sortr.app.ui.common.formatDate
import com.sortr.app.ui.common.formatDuration
import com.sortr.domain.model.Photo

/**
 * Square grid tile for a photo or video: thumbnail, video length, and a selection mark while
 * selecting. Long-press starts selecting, like the Photos app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaTile(
    photo: Photo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RectangleShape,
    contentDescription: String = stringResource(R.string.album_photo_description, formatDate(photo.dateTaken)),
) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { if (selected != null) this.selected = selected }
            .combinedClickable(role = Role.Image, onLongClick = onLongClick, onClick = onClick),
    ) {
        AsyncImage(
            model = MediaThumbnail(photo.uri.toUri()),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (photo.isVideo) VideoBadge(photo.durationMs, Modifier.align(Alignment.BottomStart))
        if (selected == true) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.25f)),
            )
        }
        if (selected != null) {
            SelectionMark(
                selected = selected,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            )
        }
    }
}

/** Play symbol and length along the bottom of a video thumbnail. */
@Composable
fun VideoBadge(durationMs: Long?, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        if (durationMs != null) {
            Spacer(Modifier.width(2.dp))
            Text(formatDuration(durationMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}
