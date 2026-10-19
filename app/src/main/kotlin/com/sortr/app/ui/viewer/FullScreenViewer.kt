package com.sortr.app.ui.viewer

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import com.sortr.app.R
import com.sortr.app.image.MediaThumbnail
import com.sortr.app.image.viewerImageRequest
import com.sortr.app.media.ShareItem
import com.sortr.app.media.Sharing
import com.sortr.app.ui.common.formatBytes
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.common.formatDate
import com.sortr.domain.model.Photo
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Full screen photo with pinch to zoom, drag to pan and double tap to zoom in or out. Videos
 * play instead, with a tap to pause or resume. Drag down to close.
 */
@Composable
fun FullScreenViewer(photo: Photo, onDismiss: () -> Unit) = FullScreenViewer(listOf(photo), 0, onDismiss)

/** Several photos and videos: swipe sideways between them. Share sends the one on screen. */
@Composable
fun FullScreenViewer(photos: List<Photo>, initialIndex: Int, onDismiss: () -> Unit) {
    if (photos.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val platformContext = LocalPlatformContext.current
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val screen = with(LocalDensity.current) {
            IntSize(configuration.screenWidthDp.dp.roundToPx(), configuration.screenHeightDp.dp.roundToPx())
        }
        val pager = rememberPagerState(initialPage = initialIndex.coerceIn(0, photos.lastIndex)) { photos.size }
        // Fades out while the photo is dragged down to close.
        var dismissProgress by remember { mutableFloatStateOf(0f) }
        val current = photos[pager.currentPage.coerceIn(0, photos.lastIndex)]

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 1f - dismissProgress)),
        ) {
            HorizontalPager(
                state = pager,
                beyondViewportPageCount = 1,
                key = { photos[it].id },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val photo = photos[page]
                if (photo.isVideo) {
                    // Only the video on screen plays; neighbours show a still frame.
                    if (page == pager.settledPage) {
                        VideoPlayer(uri = photo.uri.toUri(), modifier = Modifier.fillMaxSize())
                    } else {
                        AsyncImage(
                            model = MediaThumbnail(photo.uri.toUri()),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    val request = remember(photo.id, screen) { viewerImageRequest(platformContext, photo, screen) }
                    ZoomableImage(
                        model = request,
                        contentDescription = stringResource(R.string.swipe_card_description, formatDate(photo.dateTaken)),
                        modifier = Modifier.fillMaxSize(),
                        onDismiss = onDismiss,
                        onDismissProgress = { dismissProgress = it },
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(8.dp),
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.viewer_close), tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (photos.size > 1) {
                    Text(
                        text = stringResource(R.string.swipe_progress, formatCount(pager.currentPage + 1), formatCount(photos.size)),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.weight(1f))
                }
                IconButton(
                    onClick = { Sharing.share(context, listOf(ShareItem(current.uri.toUri(), current.mimeType))) },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.action_share), tint = Color.White)
                }
            }
            Text(
                text = stringResource(
                    R.string.swipe_card_details,
                    formatDate(current.dateTaken),
                    formatBytes(context, current.sizeBytes),
                ),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .safeDrawingPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 16.dp),
            )
        }
    }
}

/**
 * Pinch to zoom, drag to pan, double tap to zoom in or out. With [onDismiss], dragging the
 * unzoomed photo down closes it, reporting how far along the drag is through [onDismissProgress].
 */
@Composable
fun ZoomableImage(
    model: Any,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = SingletonImageLoader.get(LocalPlatformContext.current),
    onDismiss: (() -> Unit)? = null,
    onDismissProgress: (Float) -> Unit = {},
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember { mutableStateOf(Size.Unspecified) }
    var dismissDrag by remember { mutableFloatStateOf(0f) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnProgress by rememberUpdatedState(onDismissProgress)

    fun dragProgress() = if (size.height == 0) 0f else (dismissDrag / (size.height * DISMISS_FRACTION)).coerceIn(0f, 1f)

    // Keeps the zoomed photo covering the screen: it can only move as far as it's larger than the
    // view, so the black border around a photo with another shape never slides into view.
    fun clamp(value: Offset, atScale: Float): Offset {
        val shown = fittedSize(imageSize, size)
        val maxX = max(0f, (shown.width * atScale - size.width) / 2f)
        val maxY = max(0f, (shown.height * atScale - size.height) / 2f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val dismissing = onDismiss != null && scale == 1f && zoomChange == 1f &&
            (dismissDrag > 0f || (panChange.y > 0f && abs(panChange.y) > abs(panChange.x)))
        if (dismissing) {
            dismissDrag = (dismissDrag + panChange.y).coerceAtLeast(0f)
            currentOnProgress(dragProgress())
        } else {
            val newScale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
            offset = clamp(offset + panChange, newScale)
            scale = newScale
        }
    }

    // When the finger lifts: close if dragged far enough, otherwise spring back.
    LaunchedEffect(transformState) {
        snapshotFlow { transformState.isTransformInProgress }.collect { inProgress ->
            if (inProgress || dismissDrag == 0f) return@collect
            if (dragProgress() >= 1f) {
                currentOnDismiss?.invoke()
            } else {
                animate(dismissDrag, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                    dismissDrag = value
                    currentOnProgress(dragProgress())
                }
            }
        }
    }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
        onState = { state ->
            if (state is AsyncImagePainter.State.Success) imageSize = state.painter.intrinsicSize
        },
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            // Zoom in around the tapped point.
                            val center = Offset(size.width / 2f, size.height / 2f)
                            scale = DOUBLE_TAP_ZOOM
                            offset = clamp((center - tap) * (DOUBLE_TAP_ZOOM - 1f), DOUBLE_TAP_ZOOM)
                        }
                    },
                )
            }
            // Sideways drags on an unzoomed photo go to the pager; downward ones close the viewer.
            .transformable(
                state = transformState,
                canPan = { pan -> scale > 1f || (onDismiss != null && abs(pan.y) > abs(pan.x)) },
            )
            .graphicsLayer {
                // Shrinks a little while being dragged away, like the Photos app.
                val shrink = 1f - DISMISS_SHRINK * dragProgress()
                scaleX = scale * shrink
                scaleY = scale * shrink
                translationX = offset.x
                translationY = offset.y + dismissDrag
            },
    )
}

/** Size of an image of [image] size drawn with ContentScale.Fit inside [view]. */
private fun fittedSize(image: Size, view: IntSize): Size {
    if (image == Size.Unspecified || image.width <= 0f || image.height <= 0f || view == IntSize.Zero) {
        return Size(view.width.toFloat(), view.height.toFloat())
    }
    val fit = min(view.width / image.width, view.height / image.height)
    return Size(image.width * fit, image.height * fit)
}

/** Dragging down this share of the screen height closes the viewer. */
private const val DISMISS_FRACTION = 0.25f
private const val DISMISS_SHRINK = 0.2f

/** Plays a video from MediaStore. Tap to pause or resume; a thin bar shows how far along it is. */
@Composable
private fun VideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf<VideoView?>(null) }
    var playing by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(view) {
        val player = view ?: return@LaunchedEffect
        while (true) {
            if (player.duration > 0) progress = player.currentPosition.toFloat() / player.duration
            delay(PROGRESS_TICK_MS)
        }
    }

    Box(
        modifier.pointerInput(Unit) {
            detectTapGestures {
                val player = view ?: return@detectTapGestures
                if (player.isPlaying) player.pause() else player.start()
                playing = player.isPlaying
            }
        },
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setOnPreparedListener { start() }
                    setOnCompletionListener { playing = false }
                    setOnErrorListener { _, _, _ ->
                        failed = true
                        true
                    }
                    setVideoURI(uri)
                    view = this
                }
            },
            onRelease = { it.stopPlayback() },
            modifier = Modifier.align(Alignment.Center),
        )
        if (failed) {
            Text(
                text = stringResource(R.string.viewer_video_error),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        } else if (!playing) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(12.dp),
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.25f),
            drawStopIndicator = {},
            gapSize = 0.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 72.dp)
                .height(3.dp),
        )
    }
}

private const val PROGRESS_TICK_MS = 250L
