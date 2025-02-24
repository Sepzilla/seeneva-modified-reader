/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2024 Sergei Solodovnikov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.seeneva.reader.ui.screen.library

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.seeneva.reader.R
import app.seeneva.reader.logic.ComicListViewType
import app.seeneva.reader.logic.comic.Library
import app.seeneva.reader.logic.entity.ComicListItem
import app.seeneva.reader.logic.image.ImageLoader
import app.seeneva.reader.screen.list.adapter.ComicsAdapter
import app.seeneva.reader.ui.screen.library.state.LibraryListStoreContract
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel = koinViewModel(),
    onMenuClick: () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        //TODO
        libraryViewModel.sendIntent(LibraryListStoreContract.Intent.LoadPage())
    }

    val coroutineScope = rememberCoroutineScope()

    val topBarBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Flow of the clicked bottom bar actions. The fragment will consume it
    val clickedActions = remember { MutableSharedFlow<LibraryAction>() }

    // Lis of bottom bar actions to show
    var bottomBarActions by remember { mutableStateOf(emptyList<LibraryAction>()) }

    Scaffold(
        topBar = {
            LibraryTopAppBar(
                scrollBehavior = topBarBehavior,
                onMenuClick = onMenuClick,
            )
        },
        bottomBar = {
            BottomBar(
                actions = bottomBarActions,
                onActionClick = {
                    coroutineScope.launch {
                        clickedActions.emit(it)
                    }
                }
            )
        },
        modifier = Modifier.nestedScroll(connection = topBarBehavior.nestedScrollConnection)
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()

            when (val state = libraryState) {
                LibraryListStoreContract.State.Idle -> {

                }

                is LibraryListStoreContract.State.Loaded -> {
                    LibraryList(
                        pagingData = state.pagingData,
                        isSyncing = state.syncState == Library.State.SYNCING,
                        onStartSyncing = {
                            libraryViewModel.sendIntent(LibraryListStoreContract.Intent.Sync)
                        }
                    )
                }

                LibraryListStoreContract.State.Loading -> {

                }
            }
        }
    }
}

/**
 * Top bar for the library
 *
 * @param searchValue current search value. Empty string if there is no value
 * @param scrollBehavior app bar scroll behavior
 * @param onMenuClick on menu icon click
 * @param onSearchValueChange on search value change
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopAppBar(
    searchValue: String = "",
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
    onMenuClick: () -> Unit = {},
    onSearchValueChange: (String) -> Unit = {}
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = null
                )
            }
        },
        title = {
            TextField(
                modifier = Modifier.fillMaxSize(),
                value = searchValue,
                placeholder = {
                    Text(stringResource(R.string.comic_list_search_hint))
                },
                singleLine = true,
                trailingIcon = {
                    if (searchValue.isNotEmpty()) {
                        IconButton(
                            onClick = {}
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = null,
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                onValueChange = onSearchValueChange
            )
        },
        modifier = Modifier
            .padding(16.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(18.dp)
            ),
        scrollBehavior = scrollBehavior
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryList(
    pagingData: PagingData<ComicListItem>,
    isSyncing: Boolean = false,
    onStartSyncing: () -> Unit = {}
) {

    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = onStartSyncing
    ) {
        val coroutineScope = rememberCoroutineScope()

        val lifecycle = LocalLifecycleOwner.current.lifecycle

        val imageLoader =
            koinInject<ImageLoader>(named<ImageLoader>()) { parametersOf(lifecycle) }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                /**
                 * Set margin between comic items in the grid
                 * @param margin margin between items
                 */
                class ComicGridMarginDecoration(private val margin: Int) :
                    RecyclerView.ItemDecoration() {
                    override fun getItemOffsets(
                        outRect: Rect,
                        view: View,
                        parent: RecyclerView,
                        state: RecyclerView.State
                    ) {
                        outRect.offset(margin, margin)
                    }
                }

                val recyclerView = RecyclerView(it)

                recyclerView.setHasFixedSize(true)

                recyclerView.adapter = ComicsAdapter(
                    comicViewType = ComicListViewType.GRID,
                    imageLoader,
                    LayoutInflater.from(it),
                    object : ComicsAdapter.Callback {
                        override fun onItemDeleteClick(comic: ComicListItem) {
                        }

                        override fun onItemRenameClick(comic: ComicListItem) {
                        }

                        override fun onItemInfoClick(comic: ComicListItem) {

                        }

                        override fun onMarkAsReadClick(comic: ComicListItem) {
                        }

                        override fun isItemSelected(comic: ComicListItem): Boolean {
                            return false
                        }

                        override fun onComicBookClick(comic: ComicListItem) {

                        }
                    })

                recyclerView.layoutManager =
                    GridLayoutManager(it, it.resources.getInteger(R.integer.comic_thumb_grid_size))

                recyclerView.addItemDecoration(
                    ComicGridMarginDecoration(
                        it.resources.getDimensionPixelSize(R.dimen.comic_thumb_grid_margin)
                    )
                )

                recyclerView
            },
            onRelease = { recyclerView ->
                recyclerView.adapter = null
            }
        ) { recyclerView ->
            val adapter = recyclerView.adapter as ComicsAdapter

            coroutineScope.launch {
                adapter.submitData(pagingData)
            }
        }
    }
}

@Composable
private fun BottomBar(
    actions: List<LibraryAction> = emptyList(),
    onActionClick: (action: LibraryAction) -> Unit = {}
) {
    BottomAppBar(
        actions = {
            actions.forEach {
                IconButton(enabled = it.enabled, onClick = { onActionClick(it) }) {
                    it.Icon()
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onActionClick(LibraryAction.Add) }) {
                LibraryAction.Add.Icon()
            }
        }
    )
}

@Composable
private fun LibraryAction.Icon() {
    when (this) {
        is LibraryAction.ListView ->
            Icon(
                Icons.AutoMirrored.Rounded.List,
                contentDescription = stringResource(R.string.comic_list_as_list)
            )

        is LibraryAction.Sort ->
            Icon(
                painter = painterResource(R.drawable.ic_round_sort_24dp),
                contentDescription = stringResource(R.string.comic_list_sort)
            )

        is LibraryAction.Filter ->
            Icon(
                painter = painterResource(R.drawable.ic_round_filter_list_24dp),
                contentDescription = stringResource(R.string.comic_list_filter)
            )

        is LibraryAction.Sync ->
            Icon(
                painter = painterResource(R.drawable.ic_round_sync_24dp),
                contentDescription = stringResource(R.string.comic_list_sync)
            )

        is LibraryAction.Add ->
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.comic_list_add)
            )

        is LibraryAction.GridView ->
            Icon(
                painter = painterResource(R.drawable.ic_round_view_module_24dp),
                contentDescription = stringResource(R.string.comic_list_as_grid)
            )
    }
}

/**
 * Type of an action on the library screen
 */
sealed interface LibraryAction {
    /**
     * Is action enabled
     */
    val enabled: Boolean

    /**
     * Add a new item to the library
     */
    data object Add : LibraryAction {
        override val enabled: Boolean
            get() = true
    }

    /**
     * Switch to the list view
     */
    data object ListView : LibraryAction {
        override val enabled: Boolean
            get() = true
    }

    /**
     * Switch to the grid mode
     */
    data object GridView : LibraryAction {
        override val enabled: Boolean
            get() = true
    }

    /**
     * Sort items in the library
     */
    data object Sort : LibraryAction {
        override val enabled: Boolean
            get() = true
    }

    /**
     * Filter items in the library
     */
    data object Filter : LibraryAction {
        override val enabled: Boolean
            get() = true
    }

    /**
     * Sync items in the library
     */
    data class Sync(override val enabled: Boolean = true) : LibraryAction
}