package com.sortr.app.ui.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import com.sortr.app.R
import com.sortr.app.image.cardImageRequest
import com.sortr.app.ui.common.formatBytes
import com.sortr.app.ui.common.formatDate
import com.sortr.domain.model.Photo
import com.sortr.domain.model.SwipeAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val CardShape = RoundedCornerShape(24.dp)
private val CardBackground = Color(0xFF111111)
private val FlingVelocity = 1200.dp // per second
private const val MAX_ROTATION_DEGREES = 12f

/** Position of the top card. One instance per card, so a new card always starts centred. */
@Stable
internal class SwipeCardState {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)

    /** True once the card is flying off screen. Further input is ignored. */
    var isCommitting by mutableStateOf(false)
        private set

    suspend fun dragTo(position: Offset) {
        if (!isCommitting) offset.snapTo(position)
    }

    suspend fun settle() {
        if (!isCommitting) {
            offset.animateTo(
                targetValue = Offset.Zero,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            )
        }
    }

    /** Animates the card off screen. Returns false if it was already leaving. */
    suspend fun flyOut(action: SwipeAction, cardSize: IntSize): Boolean {
        if (isCommitting) return false
        isCommitting = true
        val start = offset.value
        val farX = cardSize.width * 1.5f
        val farY = cardSize.height * 1.5f
        val target = when (action) {
            SwipeAction.KEEP -> Offset(farX, start.y)
            SwipeAction.DELETE -> Offset(-farX, start.y)
            SwipeAction.PRIVATE -> Offset(start.x, -farY)
            SwipeAction.SKIP -> Offset(start.x, farY)
        }
        offset.animateTo(target, tween(durationMillis = 220, easing = FastOutLinearInEasing))
        return true
    }
}

/**
 * The top card plus the next one peeking from behind it. Also preloads the [upcoming] photos
 * at card size so swiping never waits on decoding.
 */
@Composable
internal fun SwipeStack(
    current: Photo,
    upcoming: List<Photo>,
    cardState: SwipeCardState,
    cardSize: IntSize,
    enabledActions: Set<SwipeAction>,
    canCommit: (SwipeAction) -> Boolean,
    onBlocked: (SwipeAction) -> Unit,
    onCommit: (Photo, SwipeAction) -> Unit,
    onOpen: (Photo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    LaunchedEffect(upcoming.map { it.id }, cardSize) {
        if (cardSize == IntSize.Zero) return@LaunchedEffect
        val loader = SingletonImageLoader.get(context)
        upcoming.forEach { loader.enqueue(cardImageRequest(context, it, cardSize)) }
    }

    Box(modifier) {
        if (cardSize == IntSize.Zero) return@Box

        upcoming.firstOrNull()?.let { next ->
            key(next.id) {
                PhotoCardContent(
                    photo = next,
                    cardSize = cardSize,
                    modifier = Modifier.graphicsLayer {
                        val o = cardState.offset.value
                        val progress = max(
                            abs(o.x) / (cardSize.width * HORIZONTAL_COMMIT_FRACTION),
                            abs(o.y) / (cardSize.height * VERTICAL_COMMIT_FRACTION),
                        ).coerceIn(0f, 1f)
                        val scale = 0.92f + 0.08f * progress
                        scaleX = scale
                        scaleY = scale
                    },
                )
            }
        }
        key(current.id) {
            SwipeCard(
                photo = current,
                state = cardState,
                cardSize = cardSize,
                enabledActions = enabledActions,
                canCommit = canCommit,
                onBlocked = onBlocked,
                onCommit = onCommit,
                onOpen = { onOpen(current) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SwipeCard(
    photo: Photo,
    state: SwipeCardState,
    cardSize: IntSize,
    enabledActions: Set<SwipeAction>,
    canCommit: (SwipeAction) -> Boolean,
    onBlocked: (SwipeAction) -> Unit,
    onCommit: (Photo, SwipeAction) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val flingVelocity = with(LocalDensity.current) { FlingVelocity.toPx() }
    val currentOnCommit by rememberUpdatedState(onCommit)
    val currentCanCommit by rememberUpdatedState(canCommit)
    val currentOnBlocked by rememberUpdatedState(onBlocked)
    val width = cardSize.width.toFloat()
    val height = cardSize.height.toFloat()

    val offset = state.offset.value
    val direction = dragDirection(offset.x, offset.y)?.takeIf { it in enabledActions }
    val progress = if (direction != null) dragProgress(offset.x, offset.y, width, height) else 0f

    val commit: (SwipeAction) -> Unit = { action ->
        if (currentCanCommit(action)) {
            scope.launch {
                if (state.flyOut(action, cardSize)) currentOnCommit(photo, action)
            }
        } else {
            scope.launch { state.settle() }
            currentOnBlocked(action)
        }
    }
    val accessibilityActions = enabledActions.map { action ->
        CustomAccessibilityAction(stringResource(action.labelRes)) {
            commit(action)
            true
        }
    }

    Box(
        modifier
            // Before graphicsLayer, so drag positions aren't affected by the card's own movement.
            .pointerInput(photo.id, cardSize, enabledActions) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    // Initial pass: the card sees touches before the tap handler inside it, so a
                    // drag claims them and can never also count as a tap.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val origin = state.offset.value
                    var dragging = false
                    var moved = Offset.Zero
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            // Measured from the press rather than summed from deltas: when the phone
                            // is busy, Android can fold the last moves into the release event.
                            moved = change.position - down.position
                            change.historical.forEach { tracker.addPosition(it.uptimeMillis, it.position) }
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!dragging && moved.getDistance() > touchSlop) dragging = true
                            if (dragging) {
                                change.consume()
                                val target = origin + moved
                                scope.launch { state.dragTo(target) }
                            }
                            if (!change.pressed) break
                        }
                    } catch (e: CancellationException) {
                        scope.launch { state.settle() }
                        throw e
                    }
                    // No drag means a tap, which the card's click handler takes care of.
                    if (!dragging) return@awaitEachGesture

                    val velocity = tracker.calculateVelocity()
                    val end = origin + moved
                    val action = resolveSwipe(
                        dx = end.x,
                        dy = end.y,
                        velocityX = velocity.x,
                        velocityY = velocity.y,
                        width = width,
                        height = height,
                        flingVelocity = flingVelocity,
                        enabled = enabledActions,
                    )
                    if (action != null) commit(action) else scope.launch { state.settle() }
                }
            }
            .graphicsLayer {
                translationX = offset.x
                translationY = offset.y
                rotationZ = if (width > 0f) offset.x / width * MAX_ROTATION_DEGREES else 0f
            }
            .semantics { customActions = accessibilityActions },
    ) {
        PhotoCardContent(photo = photo, cardSize = cardSize, onClick = onOpen)
        if (direction != null && progress > 0f) {
            SwipeOverlay(direction, progress)
        }
    }
}

@Composable
private fun BoxScope.SwipeOverlay(action: SwipeAction, progress: Float) {
    Box(
        Modifier
            .matchParentSize()
            .clip(CardShape)
            .background(action.color.copy(alpha = 0.5f * progress)),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.Center)
            .graphicsLayer {
                alpha = progress
                val scale = 0.8f + 0.2f * progress
                scaleX = scale
                scaleY = scale
            }
            .border(3.dp, Color.White, RoundedCornerShape(12.dp))
            .background(action.color, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Icon(action.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(action.labelRes).uppercase(Locale.getDefault()),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The photo itself with its date and size. Used for both the top card and the one behind it. */
@Composable
internal fun PhotoCardContent(
    photo: Photo,
    cardSize: IntSize,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalPlatformContext.current
    val request = remember(photo.id, cardSize) { cardImageRequest(context, photo, cardSize) }
    var loadState by remember(photo.id) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClickLabel = stringResource(R.string.swipe_open_full_screen), onClick = onClick)
    } else {
        Modifier
    }

    Card(
        shape = CardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(CardBackground)
                .then(clickModifier),
        ) {
            AsyncImage(
                model = request,
                contentDescription = stringResource(R.string.swipe_card_description, formatDate(photo.dateTaken)),
                contentScale = ContentScale.Fit,
                onState = { loadState = it },
                modifier = Modifier.fillMaxSize(),
            )
            when (loadState) {
                is AsyncImagePainter.State.Loading -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                is AsyncImagePainter.State.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Icon(Icons.Rounded.BrokenImage, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.swipe_image_error), color = Color.White)
                }
                else -> Unit
            }
            CardDetails(photo, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun CardDetails(photo: Photo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
            .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 16.dp),
    ) {
        Text(
            text = stringResource(
                R.string.swipe_card_details,
                formatDate(photo.dateTaken),
                formatBytes(context, photo.sizeBytes),
            ),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
