package com.sortr.app.image

import androidx.compose.ui.unit.IntSize
import androidx.core.net.toUri
import coil3.PlatformContext
import coil3.request.ImageRequest
import com.sortr.domain.model.Photo

/**
 * Request for a photo shown on a swipe card. The swipe screen preloads upcoming cards with
 * this exact request, so the explicit cache key guarantees the card finds the preloaded image.
 */
fun cardImageRequest(context: PlatformContext, photo: Photo, size: IntSize): ImageRequest =
    ImageRequest.Builder(context)
        // Videos show a still frame from the system's thumbnailer.
        .data(if (photo.isVideo) MediaThumbnail(photo.uri.toUri()) else photo.uri.toUri())
        .size(size.width, size.height)
        .memoryCacheKey("card:${photo.id}:${photo.sizeBytes}:${size.width}x${size.height}")
        .build()

/** Request for the full screen viewer: up to twice the screen size so zooming stays sharp. */
fun viewerImageRequest(context: PlatformContext, photo: Photo, screen: IntSize): ImageRequest {
    val width = (screen.width * 2).coerceAtMost(MAX_VIEWER_PX)
    val height = (screen.height * 2).coerceAtMost(MAX_VIEWER_PX)
    return ImageRequest.Builder(context)
        .data(photo.uri.toUri())
        .size(width, height)
        .build()
}

private const val MAX_VIEWER_PX = 4096
