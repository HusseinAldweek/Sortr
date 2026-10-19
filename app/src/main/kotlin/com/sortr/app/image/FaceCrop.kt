package com.sortr.app.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.ImageRequest
import coil3.request.Options
import com.sortr.data.faces.FaceCropLoader
import com.sortr.domain.model.FaceRef
import java.io.IOException

/** Request for a square face thumbnail, loaded by [FaceCropFetcher]. */
fun faceImageRequest(context: PlatformContext, face: FaceRef): ImageRequest =
    ImageRequest.Builder(context)
        .data(face)
        .memoryCacheKey("face:${face.faceId}")
        .build()

/** Loads a face thumbnail through [FaceCropLoader], which decodes only the area around the face. */
class FaceCropFetcher(private val face: FaceRef, private val loader: FaceCropLoader) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bitmap = loader.load(face) ?: throw IOException("Couldn't load face ${face.faceId}")
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val loader: FaceCropLoader) : Fetcher.Factory<FaceRef> {
        override fun create(data: FaceRef, options: Options, imageLoader: ImageLoader): Fetcher =
            FaceCropFetcher(data, loader)
    }
}

class FaceCropKeyer : Keyer<FaceRef> {
    override fun key(data: FaceRef, options: Options): String = "face:${data.faceId}"
}
