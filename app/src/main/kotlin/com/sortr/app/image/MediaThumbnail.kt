package com.sortr.app.image

import android.content.ContentResolver
import android.net.Uri
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse

/**
 * Coil model for a thumbnail of a photo or video. Loaded through [ContentResolver.loadThumbnail],
 * which reuses the system's thumbnail cache, is much faster than decoding the full photo and
 * also gives a still frame for videos.
 */
data class MediaThumbnail(val uri: Uri)

class MediaThumbnailFetcher(
    private val data: MediaThumbnail,
    private val options: Options,
    private val resolver: ContentResolver,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val width = options.size.width.pxOrElse { DEFAULT_SIZE_PX }
        val height = options.size.height.pxOrElse { DEFAULT_SIZE_PX }
        val bitmap = resolver.loadThumbnail(data.uri, android.util.Size(width, height), null)
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val resolver: ContentResolver) : Fetcher.Factory<MediaThumbnail> {
        override fun create(data: MediaThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbnailFetcher(data, options, resolver)
    }

    private companion object {
        const val DEFAULT_SIZE_PX = 256
    }
}

class MediaThumbnailKeyer : Keyer<MediaThumbnail> {
    // Includes the size: the same item is shown as a small grid tile and as a large video card.
    override fun key(data: MediaThumbnail, options: Options): String =
        "thumb:${data.uri}:${options.size.width}x${options.size.height}"
}
