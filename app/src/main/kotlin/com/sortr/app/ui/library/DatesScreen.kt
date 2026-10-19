package com.sortr.app.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.common.formatMonth
import com.sortr.app.ui.common.formatYear
import com.sortr.app.ui.design.EmptyState
import com.sortr.app.ui.design.GroupedCard
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.ListRow
import com.sortr.app.ui.design.ScreenPadding
import com.sortr.app.ui.design.TintedButton
import com.sortr.app.ui.theme.palette
import com.sortr.domain.model.DateBucket
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class YearGroup(val year: Int, val count: Int, val toReview: Int, val months: List<DateBucket>)

@HiltViewModel
class DatesViewModel @Inject constructor(library: LibraryRepository) : ViewModel() {

    /** Null while loading. Years newest first, months newest first inside each. */
    val years: StateFlow<List<YearGroup>?> = library.observeDateBuckets().map { buckets ->
        buckets.groupBy { it.year }.map { (year, months) ->
            YearGroup(year, count = months.sumOf { it.count }, toReview = months.sumOf { it.toReview }, months = months)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun DatesScreen(
    onBack: () -> Unit,
    onStartSession: (filterKey: String) -> Unit,
    viewModel: DatesViewModel = hiltViewModel(),
) {
    val years by viewModel.years.collectAsStateWithLifecycle()
    // The newest year starts open; the rest are folded.
    var expanded by rememberSaveable { mutableStateOf<List<Int>?>(null) }

    LargeTitleScaffold(title = stringResource(R.string.dates_title), onBack = onBack) { padding ->
        val list = years
        when {
            list == null -> CircularProgressIndicator(Modifier.padding(padding).padding(32.dp))
            list.isEmpty() -> EmptyState(
                icon = Icons.Rounded.CalendarMonth,
                iconColor = MaterialTheme.palette.red,
                title = stringResource(R.string.dates_empty_title),
                body = stringResource(R.string.dates_empty_body),
                modifier = Modifier.padding(padding),
            )
            else -> {
                val open = expanded ?: listOfNotNull(list.firstOrNull()?.year)
                LazyColumn(
                    contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    items(list, key = { it.year }) { group ->
                        YearCard(
                            group = group,
                            isOpen = group.year in open,
                            onToggle = {
                                expanded = if (group.year in open) open - group.year else open + group.year
                            },
                            onStartSession = onStartSession,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearCard(
    group: YearGroup,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onStartSession: (String) -> Unit,
) {
    val rotation by animateFloatAsState(if (isOpen) 180f else 0f, label = "chevron")
    GroupedCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(formatYear(group.year), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = countSummary(group.count, group.toReview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (group.toReview > 0) {
                TintedButton(
                    text = stringResource(R.string.action_start),
                    onClick = { onStartSession(SessionFilter.Year(group.year).key) },
                    compact = true,
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(visible = isOpen) {
            Column {
                group.months.forEach { month ->
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.palette.separator, modifier = Modifier.padding(start = 16.dp))
                    ListRow(
                        title = formatMonth(month.month),
                        subtitle = pluralStringResource(R.plurals.count_items, month.count, formatCount(month.count)),
                        value = if (month.toReview > 0) formatCount(month.toReview) else null,
                        trailing = if (month.toReview == 0) {
                            { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.palette.green) }
                        } else {
                            null
                        },
                        onClick = { onStartSession(SessionFilter.Month(month.year, month.month).key) },
                    )
                }
            }
        }
    }
}

/** "1,234 items · 56 to review", or just the item count when everything is sorted. */
@Composable
fun countSummary(count: Int, toReview: Int): String {
    val items = pluralStringResource(R.plurals.count_items, count, formatCount(count))
    return if (toReview > 0) {
        stringResource(R.string.count_with_review, items, pluralStringResource(R.plurals.session_to_review, toReview, formatCount(toReview)))
    } else {
        items
    }
}
