package com.sortr.app.ui.design

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Hold a tile, then drag across others to select them all, like the Photos app. Everything
 * between where the finger went down and where it is now gets selected (or unselected, if the
 * first tile was already selected). Near the top or bottom edge the grid scrolls by itself.
 *
 * [ids] are the selectable item keys (as given to the grid) in display order; other keys, such as
 * year headers, are ignored.
 */
@Composable
fun <K : Any> Modifier.dragToSelect(
    state: LazyGridState,
    ids: List<K>,
    selected: Set<K>,
    onSelectionChange: (Set<K>) -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val speed = remember { mutableFloatStateOf(0f) }
    val currentIds by rememberUpdatedState(ids)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnChange by rememberUpdatedState(onSelectionChange)

    AutoScroll(state, speed)

    return pointerInput(state) {
        var anchor: K? = null
        var last: K? = null
        var before: Set<K> = emptySet()
        var adding = true

        fun keyAt(position: Offset): K? {
            val key = state.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                val x = item.offset.x
                val y = item.offset.y
                position.x >= x && position.x < x + item.size.width && position.y >= y && position.y < y + item.size.height
            }?.key ?: return null
            return currentIds.firstOrNull { it == key }
        }

        fun span(from: K, to: K): List<K> {
            val list = currentIds
            val a = list.indexOf(from)
            val b = list.indexOf(to)
            if (a < 0 || b < 0) return emptyList()
            return list.subList(minOf(a, b), maxOf(a, b) + 1)
        }

        detectDragGesturesAfterLongPress(
            onDragStart = { position ->
                val key = keyAt(position) ?: return@detectDragGesturesAfterLongPress
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                anchor = key
                last = key
                before = currentSelected
                adding = key !in before
                currentOnChange(if (adding) before + key else before - key)
            },
            onDrag = { change, _ ->
                val start = anchor ?: return@detectDragGesturesAfterLongPress
                val y = change.position.y
                val height = size.toSize().height
                speed.floatValue = when {
                    y > height - edge -> (y - (height - edge)) / 4f
                    y < edge -> -(edge - y) / 4f
                    else -> 0f
                }
                val key = keyAt(change.position) ?: return@detectDragGesturesAfterLongPress
                if (key != last) {
                    last = key
                    val range = span(start, key)
                    currentOnChange(if (adding) before + range else before - range.toSet())
                }
            },
            onDragEnd = {
                anchor = null
                speed.floatValue = 0f
            },
            onDragCancel = {
                anchor = null
                speed.floatValue = 0f
            },
        )
    }
}

@Composable
private fun AutoScroll(state: LazyGridState, speed: MutableFloatState) {
    val value = speed.floatValue
    LaunchedEffect(value != 0f) {
        while (isActive && speed.floatValue != 0f) {
            state.scrollBy(speed.floatValue)
            delay(10)
        }
    }
}
