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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.lifecycleScope
import app.seeneva.reader.R
import app.seeneva.reader.logic.ComicListViewType
import app.seeneva.reader.screen.list.ComicsListFragment
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(onMenuClick: () -> Unit = {}) {
    val coroutineScope = rememberCoroutineScope()

    // Flow of the clicked bottom bar actions. The fragment will consume it
    val clickedActions = remember { MutableSharedFlow<LibraryAction>() }

    // Lis of bottom bar actions to show
    var bottomBarActions by remember { mutableStateOf(emptyList<LibraryAction>()) }

    Scaffold(
        bottomBar = {
            LibraryBottomAppBar(
                actions = bottomBarActions,
                onMenuClick = onMenuClick,
                onActionClick = {
                    coroutineScope.launch {
                        clickedActions.emit(it)
                    }
                }
            )
        }
    ) { padding ->
        AndroidFragment<ComicsListFragment>(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { libraryFragment ->
            // Listen to a menu state from the fragment
            libraryFragment.menuActions
                .onEach { bottomBarActions = it }
                .launchIn(libraryFragment.viewLifecycleOwner.lifecycleScope)


            // Pass clicks from the compose to the fragment
            clickedActions
                .onEach {
                    when (it) {
                        is LibraryAction.Add ->
                            libraryFragment.onAddComicBookClick()

                        is LibraryAction.ListView ->
                            libraryFragment.currentListType = ComicListViewType.LIST

                        is LibraryAction.GridView ->
                            libraryFragment.currentListType = ComicListViewType.GRID

                        is LibraryAction.Sort ->
                            libraryFragment.onSortClick()

                        is LibraryAction.Filter ->
                            libraryFragment.onFilterClick()

                        is LibraryAction.Sync ->
                            libraryFragment.onSyncClick()
                    }
                }
                .launchIn(libraryFragment.viewLifecycleOwner.lifecycleScope)
        }
    }
}

@Composable
private fun LibraryBottomAppBar(
    actions: List<LibraryAction> = emptyList(),
    onMenuClick: () -> Unit = {},
    onActionClick: (action: LibraryAction) -> Unit = {}
) {
    BottomAppBar(
        actions = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = null
                )
            }

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