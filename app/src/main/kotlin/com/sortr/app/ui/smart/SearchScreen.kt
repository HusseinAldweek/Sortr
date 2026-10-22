package com.sortr.app.ui.smart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.design.EmptyState
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.MediaTile
import com.sortr.app.ui.design.PillButton
import com.sortr.app.ui.design.ScreenPadding
import com.sortr.app.ui.theme.palette
import com.sortr.app.ui.viewer.FullScreenViewer
import com.sortr.domain.model.AnalysisProgress
import com.sortr.domain.model.Photo
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    /** Null while the first results load. */
    val results: List<Photo>? = null,
    val progress: AnalysisProgress = AnalysisProgress(0, 0),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    library: LibraryRepository,
) : ViewModel() {

    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")

    private val results = query
        .debounce(SEARCH_DELAY_MS)
        .flatMapLatest { text -> library.searchText(text).map { text to it } }
        // So typing shows at once, before the first search is back.
        .onStart { emit("" to emptyList()) }

    val state: StateFlow<SearchUiState> = combine(query, results, library.observeSmartProgress()) { typed, (searched, photos), progress ->
        SearchUiState(query = typed, results = photos.takeIf { searched == typed }, progress = progress)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(value: String) {
        savedStateHandle[KEY_QUERY] = value
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val SEARCH_DELAY_MS = 250L
    }
}

/** Finds photos by the words in them, e.g. the screenshot with an address. */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onStartSession: (filterKey: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    val results = state.results.orEmpty()

    LaunchedEffect(Unit) { if (state.query.isEmpty()) focus.requestFocus() }

    LargeTitleScaffold(
        title = stringResource(R.string.search_title),
        onBack = onBack,
        bottomBar = {
            if (state.query.isNotBlank() && results.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    PillButton(
                        text = pluralStringResource(R.plurals.search_sort_these, results.size, formatCount(results.size)),
                        onClick = { onStartSession(SessionFilter.TextSearch(state.query).key) },
                        icon = Icons.Rounded.Style,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = MaterialTheme.shapes.medium,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.palette.fill,
                    unfocusedContainerColor = MaterialTheme.palette.fill,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 8.dp)
                    .focusRequester(focus),
            )
            if (!state.progress.isComplete) ReadingLine(state.progress, Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp))
            when {
                state.query.isBlank() -> EmptyState(
                    icon = Icons.Rounded.Search,
                    iconColor = MaterialTheme.palette.blue,
                    title = stringResource(R.string.search_intro_title),
                    body = stringResource(R.string.search_intro_body),
                )
                // Still searching for what was just typed.
                state.results == null -> Unit
                results.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Search,
                    iconColor = MaterialTheme.palette.gray,
                    title = stringResource(R.string.search_no_results, state.query.trim()),
                    body = stringResource(R.string.search_no_results_body),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = pluralStringResource(R.plurals.search_results, results.size, formatCount(results.size)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
                        )
                    }
                    items(results, key = { it.id }) { photo ->
                        MediaTile(photo = photo, onClick = { viewerIndex = results.indexOf(photo) })
                    }
                }
            }
        }
    }

    viewerIndex?.let { index -> FullScreenViewer(results, index, onDismiss = { viewerIndex = null }) }
}
