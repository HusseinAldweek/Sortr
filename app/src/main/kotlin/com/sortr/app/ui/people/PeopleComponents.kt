package com.sortr.app.ui.people

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.sortr.app.R
import com.sortr.app.image.faceImageRequest
import com.sortr.app.ui.common.formatCount
import com.sortr.domain.model.FaceRef
import com.sortr.domain.model.Person

/** A face cropped out of its photo. Shows a placeholder icon while there's no face. */
@Composable
fun FaceImage(
    face: FaceRef?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
) {
    val context = LocalPlatformContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (face != null) {
            AsyncImage(
                model = remember(face.faceId, sizePx) { faceImageRequest(context, face, sizePx) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun personLabel(person: Person): String = person.name ?: stringResource(R.string.person_unnamed)

/** Round face, name and photo count. Used in the People grid and on Home. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonCell(
    person: Person,
    avatarSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp)
            .alpha(if (person.hidden) 0.5f else 1f),
    ) {
        Box {
            FaceImage(
                face = person.cover,
                size = avatarSize,
                modifier = if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), CircleShape) else Modifier,
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = person.name ?: stringResource(R.string.person_unnamed_short),
            style = MaterialTheme.typography.titleSmall,
            color = if (person.name == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (person.hidden) {
                stringResource(R.string.person_hidden_label)
            } else {
                pluralStringResource(R.plurals.photo_count_short, person.photoCount, formatCount(person.photoCount))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** A square face tile for grids, with a check mark when selected. */
@Composable
fun FaceTile(
    face: FaceRef,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .then(if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        FaceImage(face = face, size = 120.dp, shape = shape, modifier = Modifier.fillMaxSize())
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            )
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

@Composable
fun RenameDialog(
    initial: String?,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.person_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.person_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifEmpty { null }) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Pick one person, e.g. to merge with or to add faces to. */
@Composable
fun PersonPickerDialog(
    title: String,
    people: List<Person>,
    onPick: (Person) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (people.isEmpty()) {
                Text(stringResource(R.string.people_picker_empty))
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(people, key = { it.id }) { person ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPick(person) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            FaceImage(face = person.cover, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(personLabel(person), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = pluralStringResource(R.plurals.photo_count_short, person.photoCount, formatCount(person.photoCount)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val MAX_NAME_LENGTH = 60
