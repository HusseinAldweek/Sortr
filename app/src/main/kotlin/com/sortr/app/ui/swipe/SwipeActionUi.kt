package com.sortr.app.ui.swipe

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.sortr.app.R
import com.sortr.app.ui.theme.SwipeColors
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.model.SwipeAction

internal val SwipeAction.color: Color
    get() = when (this) {
        SwipeAction.KEEP -> SwipeColors.Keep
        SwipeAction.DELETE -> SwipeColors.Delete
        SwipeAction.PRIVATE -> SwipeColors.Private
        SwipeAction.SKIP -> SwipeColors.Skip
    }

internal val SwipeAction.icon: ImageVector
    get() = when (this) {
        SwipeAction.KEEP -> Icons.Rounded.Check
        SwipeAction.DELETE -> Icons.Rounded.Delete
        SwipeAction.PRIVATE -> Icons.Rounded.Lock
        SwipeAction.SKIP -> Icons.Rounded.ArrowDownward
    }

@get:StringRes
internal val SwipeAction.labelRes: Int
    get() = when (this) {
        SwipeAction.KEEP -> R.string.action_keep
        SwipeAction.DELETE -> R.string.action_delete
        SwipeAction.PRIVATE -> R.string.action_private
        SwipeAction.SKIP -> R.string.action_skip
    }

/** Buttons under the stack, left to right, matching the physical swipe directions. */
internal val ButtonOrder = listOf(SwipeAction.DELETE, SwipeAction.SKIP, SwipeAction.PRIVATE, SwipeAction.KEEP)

@get:StringRes
internal val SessionFilter.titleRes: Int
    get() = when (this) {
        SessionFilter.AllPhotos -> R.string.session_all_photos
    }
