package com.sortr.app.ui.smart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.design.EmptyState
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.ScreenPadding
import com.sortr.app.ui.design.SmartTile
import com.sortr.app.ui.design.TileGrid
import com.sortr.app.ui.theme.palette
import com.sortr.domain.analysis.SmartCategory
import com.sortr.domain.model.AnalysisProgress
import com.sortr.domain.model.CategoryCount
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CategoriesUiState(
    /** Null while loading. */
    val categories: List<CategoryCount>? = null,
    val progress: AnalysisProgress = AnalysisProgress(0, 0),
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(library: LibraryRepository) : ViewModel() {
    val state: StateFlow<CategoriesUiState> = combine(library.observeCategories(), library.observeSmartProgress(), ::CategoriesUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())
}

/** Documents, receipts, memes, food and pets found on the phone; each starts a session. */
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onStartSession: (filterKey: String) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LargeTitleScaffold(title = stringResource(R.string.categories_title), onBack = onBack) { padding ->
        val categories = state.categories
        when {
            categories == null -> CircularProgressIndicator(Modifier.padding(padding).padding(32.dp))
            categories.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Category,
                iconColor = MaterialTheme.palette.orange,
                title = stringResource(R.string.categories_empty_title),
                body = if (state.progress.isComplete) {
                    stringResource(R.string.categories_empty_body)
                } else {
                    stringResource(R.string.smart_progress, formatCount(state.progress.analysed), formatCount(state.progress.total))
                },
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                if (!state.progress.isComplete) item { ReadingLine(state.progress) }
                item {
                    TileGrid(
                        categories.map<CategoryCount, @Composable (Modifier) -> Unit> { item ->
                            { modifier ->
                                SmartTile(
                                    title = stringResource(item.category.labelRes),
                                    icon = item.category.icon,
                                    color = item.category.color,
                                    count = formatCount(item.toReview),
                                    caption = pluralStringResource(R.plurals.categories_total, item.count, formatCount(item.count)),
                                    onClick = { onStartSession(SessionFilter.Category(item.category).key) },
                                    modifier = modifier,
                                )
                            }
                        },
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.categories_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/** "Reading photos (x of y)…" while the background job is still going. */
@Composable
fun ReadingLine(progress: AnalysisProgress, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.padding(horizontal = 4.dp)) {
        CircularProgressIndicator(
            progress = { if (progress.total == 0) 0f else progress.analysed.toFloat() / progress.total },
            strokeWidth = 2.dp,
            trackColor = MaterialTheme.palette.fill,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.smart_progress, formatCount(progress.analysed), formatCount(progress.total)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

val SmartCategory.labelRes: Int
    get() = when (this) {
        SmartCategory.DOCUMENTS -> R.string.category_documents
        SmartCategory.RECEIPTS -> R.string.category_receipts
        SmartCategory.MEMES -> R.string.category_memes
        SmartCategory.FOOD -> R.string.category_food
        SmartCategory.PETS -> R.string.category_pets
    }

val SmartCategory.icon: ImageVector
    get() = when (this) {
        SmartCategory.DOCUMENTS -> Icons.Rounded.Description
        SmartCategory.RECEIPTS -> Icons.Rounded.Receipt
        SmartCategory.MEMES -> Icons.Rounded.EmojiEmotions
        SmartCategory.FOOD -> Icons.Rounded.Restaurant
        SmartCategory.PETS -> Icons.Rounded.Pets
    }

val SmartCategory.color: Color
    @Composable @ReadOnlyComposable get() = with(MaterialTheme.palette) {
        when (this@color) {
            SmartCategory.DOCUMENTS -> blue
            SmartCategory.RECEIPTS -> green
            SmartCategory.MEMES -> yellow
            SmartCategory.FOOD -> orange
            SmartCategory.PETS -> pink
        }
    }
