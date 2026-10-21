package com.sortr.app.ui.library

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.sortr.app.R
import com.sortr.app.ui.common.formatMonthYear
import com.sortr.app.ui.common.formatYear
import com.sortr.app.ui.theme.palette
import com.sortr.domain.model.AlbumPaths
import com.sortr.domain.model.MediaSource
import com.sortr.domain.model.SessionFilter

@get:StringRes
val MediaSource.labelRes: Int
    get() = when (this) {
        MediaSource.CAMERA -> R.string.source_camera
        MediaSource.SCREENSHOTS -> R.string.source_screenshots
        MediaSource.SCREEN_RECORDINGS -> R.string.source_screen_recordings
        MediaSource.WHATSAPP -> R.string.source_whatsapp
        MediaSource.SNAPCHAT -> R.string.source_snapchat
        MediaSource.TELEGRAM -> R.string.source_telegram
        MediaSource.INSTAGRAM -> R.string.source_instagram
        MediaSource.MESSENGER -> R.string.source_messenger
        MediaSource.TIKTOK -> R.string.source_tiktok
        MediaSource.DOWNLOADS -> R.string.source_downloads
        MediaSource.WHATSAPP_PHOTOS -> R.string.source_whatsapp_photos
        MediaSource.WHATSAPP_VIDEOS -> R.string.source_whatsapp_videos
        MediaSource.WHATSAPP_SENT -> R.string.source_whatsapp_sent
        MediaSource.WHATSAPP_PRIVATE -> R.string.source_whatsapp_private
    }

val MediaSource.icon: ImageVector
    get() = when (this) {
        MediaSource.CAMERA -> Icons.Rounded.PhotoCamera
        MediaSource.SCREENSHOTS -> Icons.Rounded.Screenshot
        MediaSource.SCREEN_RECORDINGS -> Icons.Rounded.Videocam
        MediaSource.WHATSAPP -> Icons.AutoMirrored.Rounded.Chat
        MediaSource.SNAPCHAT -> Icons.Rounded.ChatBubble
        MediaSource.TELEGRAM -> Icons.AutoMirrored.Rounded.Send
        MediaSource.INSTAGRAM -> Icons.Rounded.CameraAlt
        MediaSource.MESSENGER -> Icons.Rounded.Forum
        MediaSource.TIKTOK -> Icons.Rounded.MusicNote
        MediaSource.DOWNLOADS -> Icons.Rounded.Download
        MediaSource.WHATSAPP_PHOTOS -> Icons.Rounded.Photo
        MediaSource.WHATSAPP_VIDEOS -> Icons.Rounded.PlayCircle
        MediaSource.WHATSAPP_SENT -> Icons.AutoMirrored.Rounded.Send
        MediaSource.WHATSAPP_PRIVATE -> Icons.Rounded.VisibilityOff
    }

val MediaSource.color: Color
    @Composable @ReadOnlyComposable get() = with(MaterialTheme.palette) {
        when (this@color) {
            MediaSource.CAMERA -> gray
            MediaSource.SCREENSHOTS -> blue
            MediaSource.SCREEN_RECORDINGS -> red
            MediaSource.WHATSAPP -> green
            MediaSource.SNAPCHAT -> yellow
            MediaSource.TELEGRAM -> teal
            MediaSource.INSTAGRAM -> pink
            MediaSource.MESSENGER -> indigo
            MediaSource.TIKTOK -> Color(0xFF1C1C1E)
            MediaSource.DOWNLOADS -> orange
            MediaSource.WHATSAPP_PHOTOS -> blue
            MediaSource.WHATSAPP_VIDEOS -> pink
            MediaSource.WHATSAPP_SENT -> teal
            MediaSource.WHATSAPP_PRIVATE -> indigo
        }
    }

/** Title for a swipe session; a person session uses [personName] when there is one. */
@Composable
fun sessionTitle(filter: SessionFilter, personName: String? = null): String = when (filter) {
    SessionFilter.AllPhotos -> stringResource(R.string.session_all_photos)
    is SessionFilter.Person -> personName ?: stringResource(R.string.person_unnamed)
    SessionFilter.UnsortedFaces -> stringResource(R.string.unsorted_faces_title)
    is SessionFilter.Year -> formatYear(filter.year)
    is SessionFilter.Month -> formatMonthYear(filter.year, filter.month)
    is SessionFilter.Album -> AlbumPaths.displayName(filter.relativePath)
    is SessionFilter.Source -> stringResource(filter.source.labelRes)
    SessionFilter.Videos -> stringResource(R.string.session_videos)
    SessionFilter.Blurry -> stringResource(R.string.session_blurry)
    SessionFilter.Duplicates -> stringResource(R.string.session_duplicates)
    SessionFilter.Largest -> stringResource(R.string.session_largest)
    is SessionFilter.Custom -> stringResource(R.string.session_custom)
    is SessionFilter.Screenshots ->
        stringResource(if (filter.olderThanDays == null) R.string.session_screenshots else R.string.session_old_screenshots)
    SessionFilter.OnThisDay -> stringResource(R.string.session_on_this_day)
}
