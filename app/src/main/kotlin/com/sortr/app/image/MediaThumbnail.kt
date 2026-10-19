package com.sortr.app.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import java.io.IOException
import kotlin.math.max

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
        val bitmap = try {
            resolver.loadThumbnail(data.uri, Size(width, height), null)
        } catch (e: Exception) {
            // Files outside the gallery (e.g. WhatsApp's sent media) have no system thumbnail.
            decodeSmall(data.uri, max(width, height)) ?: throw IOException("No thumbnail for ${data.uri}", e)
        }
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    /** A small frame for videos, or a downscaled decode for photos. */
    private fun decodeSmall(uri: Uri, maxSide: Int): Bitmap? = runCatching {
        if (resolver.getType(uri)?.startsWith("video/") == true) {
            MediaMetadataRetriever().use { retriever ->
                resolver.openFileDescriptor(uri, "r")?.use { fd ->
                    retriever.setDataSource(fd.fileDescriptor)
                    retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSide, maxSide)
                }
            }
        } else {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > maxSide) {
                    val scale = maxSide.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        }
    }.getOrNull()

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
