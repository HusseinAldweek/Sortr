package com.sortr.app.ui.viewer

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.sortr.app.R
import com.sortr.app.image.viewerImageRequest
import com.sortr.app.ui.common.formatBytes
import com.sortr.app.ui.common.formatDate
import com.sortr.domain.model.Photo

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Full screen photo with pinch to zoom, drag to pan and double tap to zoom in or out. Videos
 * play instead, with a tap to pause or resume.
 */
@Composable
fun FullScreenViewer(photo: Photo, onDismiss: () -> Unit) {
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
        val request = remember(photo.id, screen) { viewerImageRequest(platformContext, photo, screen) }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (photo.isVideo) {
                VideoPlayer(uri = photo.uri.toUri(), modifier = Modifier.fillMaxSize())
            } else {
                ZoomableImage(
                    model = request,
                    contentDescription = stringResource(R.string.swipe_card_description, formatDate(photo.dateTaken)),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.viewer_close), tint = Color.White)
            }
            Text(
                text = stringResource(
                    R.string.swipe_card_details,
                    formatDate(photo.dateTaken),
                    formatBytes(context, photo.sizeBytes),
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

/** Pinch to zoom, drag to pan, double tap to zoom in or out. */
@Composable
fun ZoomableImage(
    model: Any,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = SingletonImageLoader.get(LocalPlatformContext.current),
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Keeps the zoomed image covering the screen instead of sliding off one edge.
    fun clamp(value: Offset, atScale: Float): Offset {
        val maxX = size.width * (atScale - 1f) / 2f
        val maxY = size.height * (atScale - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
        offset = clamp(offset + panChange, newScale)
        scale = newScale
    }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
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
            .transformable(transformState)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

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
