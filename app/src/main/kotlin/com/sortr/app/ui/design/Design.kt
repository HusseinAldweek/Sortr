package com.sortr.app.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sortr.app.R
import com.sortr.app.ui.theme.palette

/** Side margin used by every screen. */
val ScreenPadding = 16.dp

/** Indent of the hairline between rows that have an icon badge: badge + gap. */
val IconRowIndent = 16.dp + 30.dp + 14.dp

/**
 * A screen with an iOS-style large title that shrinks into the bar as the content scrolls.
 * [content] gets the padding to apply and must be scrollable for the title to collapse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeTitleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { if (onBack != null) BackButton(onBack) },
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        content = content,
    )
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
            contentDescription = stringResource(R.string.action_back),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** A blue text button for a top bar, like "Select" or "Done". */
@Composable
fun BarTextButton(text: String, onClick: () -> Unit, enabled: Boolean = true, bold: Boolean = false) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Bold section title, like "Browse" or "People", with an optional "See all" style link. */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

/** Builder for [GroupedSection]: each [row] becomes one line, with hairlines in between. */
class GroupScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/**
 * A white (or dark grey) rounded card holding rows, like an iOS inset grouped list. The small
 * grey [header] and [footer] sit outside the card. Renders nothing if no rows were added.
 */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    separatorIndent: Dp = IconRowIndent,
    content: GroupScope.() -> Unit,
) {
    val rows = GroupScope().apply(content).rows
    if (rows.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                text = header.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
            )
        }
        GroupedCard {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.palette.separator,
                        modifier = Modifier.padding(start = separatorIndent),
                    )
                }
                row()
            }
        }
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
    }
}

/** The plain rounded card behind grouped rows and other content. */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.surfaceContainer
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, modifier = modifier.fillMaxWidth()) {
            Column(content = content)
        }
    } else {
        Surface(shape = shape, color = color, modifier = modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}

/** A coloured rounded square with a white symbol, like the icons in iOS Settings. */
@Composable
fun IconBadge(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    shape: RoundedCornerShape = RoundedCornerShape(size * 0.27f),
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(color),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

/**
 * One line of a grouped list: optional badge, title with an optional grey subtitle, a grey
 * value on the trailing side and a chevron when it opens something.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    value: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(14.dp))
            }
            icon != null -> {
                IconBadge(icon, iconColor)
                Spacer(Modifier.width(14.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (onClick != null && showChevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** Row with a blue checkmark when chosen, for single or multiple choice lists. */
@Composable
fun CheckRow(title: String, checked: Boolean, onClick: () -> Unit, subtitle: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = checked, role = Role.Checkbox, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (checked) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The main call to action: a full-width filled button with rounded corners. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A quieter button: tinted background with coloured text, for secondary actions. */
@Composable
fun TintedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = if (compact) CircleShape else MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.14f), contentColor = color),
        contentPadding = PaddingValues(horizontal = if (compact) 14.dp else 20.dp),
        modifier = modifier.height(if (compact) 34.dp else 52.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (compact) 16.dp else 20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/**
 * A square-ish tile for the Browse grid, like the smart lists in iOS Reminders: badge and
 * count on top, name underneath.
 */
@Composable
fun SmartTile(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: String? = null,
    caption: String? = null,
) {
    GroupedCard(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, color, size = 34.dp, shape = CircleShape)
                Spacer(Modifier.weight(1f))
                if (count != null) {
                    Text(count, style = MaterialTheme.typography.headlineSmall, maxLines = 1, softWrap = false)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** [columns] tiles per line; a short last line keeps the same tile width. */
@Composable
fun TileGrid(tiles: List<@Composable (Modifier) -> Unit>, modifier: Modifier = Modifier, columns: Int = 2) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(columns).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                line.forEach { tile -> tile(Modifier.weight(1f)) }
                repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}


/** iOS segmented control: equal parts, the chosen one raised on a white pill. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.palette.fill)
            .padding(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val background by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.surfaceBright else Color.Transparent,
                label = "segment",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .then(if (isSelected) Modifier.shadow(2.dp, RoundedCornerShape(8.dp)) else Modifier)
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) }),
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Capsule-shaped choice, blue when chosen. */
@Composable
fun Capsule(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.palette.fill,
        label = "capsule",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(background)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Round selection mark for grid items: empty ring, or a blue disc with a tick. */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(24.dp)
            .shadow(if (selected) 0.dp else 1.dp, CircleShape)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f))
            .then(
                if (selected) Modifier else Modifier.padding(2.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.08f)),
            ),
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

/** Centred icon, title and explanation for empty or finished states. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
    ) {
        IconBadge(icon, iconColor, size = 72.dp, shape = CircleShape)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        content()
    }
}

