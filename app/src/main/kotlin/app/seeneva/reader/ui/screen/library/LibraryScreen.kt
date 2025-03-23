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

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.seeneva.reader.R
import app.seeneva.reader.logic.ComicListViewType
import app.seeneva.reader.logic.comic.AddComicBookMode
import app.seeneva.reader.logic.entity.ComicListItem
import app.seeneva.reader.logic.image.ImageLoader
import app.seeneva.reader.logic.results.ChooseComicBookContract
import app.seeneva.reader.logic.results.ChooseComicBookResult
import app.seeneva.reader.screen.list.adapter.ComicsAdapter
import app.seeneva.reader.ui.screen.library.model.LibraryAction
import app.seeneva.reader.ui.screen.library.state.LibraryListStoreContract
import app.seeneva.reader.ui.widget.CheckboxWithText
import app.seeneva.reader.ui.widget.RadioButtonWithText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.tinylog.kotlin.Logger
import app.seeneva.reader.ui.screen.library.state.LibraryListStoreContract.Intent as LibraryIntent
import app.seeneva.reader.ui.screen.library.state.LibraryListStoreContract.Label as LibrarySideEffect
import app.seeneva.reader.ui.screen.library.state.LibraryListStoreContract.State as LibraryState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel = koinViewModel(),
    onMenuClick: () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        //TODO
        libraryViewModel.sendIntent(LibraryIntent.LoadPage())
    }

    val topBarBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbarState = remember { SnackbarHostState() }

    CollectSideEffects(
        sideEffects = libraryViewModel.sideEffects,
        snackbarState = snackbarState,
        onNoComicBookSelector = {
            libraryViewModel.sendIntent(LibraryIntent.ProcessNoLibrarySelector)
        },
        onComicBookSelectorResult = {
            libraryViewModel.sendIntent(LibraryIntent.ProcessLibrarySelectorResult(it))
        },
        onAddToLibrary = {
            libraryViewModel.sendIntent(LibraryIntent.AddToLibrary)
        }
    )

    // `true` means that the bottom sheet should be visible
    var showAddComicBookModeSheet by rememberSaveable { mutableStateOf(false) }

    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            LibraryTopAppBar(
                scrollBehavior = topBarBehavior,
                onMenuClick = onMenuClick,
            )
        },
        bottomBar = {
            BottomBar(
                actions = libraryState.actions,
                actionsEnabled = libraryState.isMenuEnabled,
                isSyncing = libraryState.isSyncing,
                onActionClick = { action ->
                    when (action) {
                        LibraryAction.ListView ->
                            libraryViewModel.sendIntent(
                                LibraryIntent.SetViewType(ComicListViewType.LIST)
                            )

                        LibraryAction.GridView ->
                            libraryViewModel.sendIntent(
                                LibraryIntent.SetViewType(ComicListViewType.GRID)
                            )

                        LibraryAction.Sort -> TODO()
                        LibraryAction.Filter -> TODO()
                        LibraryAction.Sync ->
                            libraryViewModel.sendIntent(LibraryIntent.SyncLibrary)
                    }
                },
                onAddClick = {
                    showAddComicBookModeSheet = true
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarState) },
        modifier = Modifier.nestedScroll(connection = topBarBehavior.nestedScrollConnection)
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val state = libraryState) {
                LibraryState.Idle -> {

                }

                is LibraryState.Loaded -> {
                    LibraryList(
                        pagingData = state.pagingData,
                        viewType = state.viewType,
                        isSyncing = state.isSyncing,
                        onStartSyncing = {
                            libraryViewModel.sendIntent(LibraryIntent.SyncLibrary)
                        }
                    )
                }

                LibraryState.Loading -> {

                }
            }
        }
    }

    if (showAddComicBookModeSheet) {
        PickAddComicBookModeBottomSheet(
            onModeSelected = { mode, remember ->
                libraryViewModel.sendIntent(
                    LibraryIntent.OpenLibrarySelector(
                        mode = mode,
                        remember = remember,
                    )
                )
            },
            onDismiss = { showAddComicBookModeSheet = false }
        )
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
    viewType: ComicListViewType = ComicListViewType.default,
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

        val gridSpanCount = when (viewType) {
            ComicListViewType.GRID ->
                integerResource(R.integer.comic_thumb_grid_size)

            ComicListViewType.LIST ->
                1
        }

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
                    comicViewType = viewType,
                    imageLoader = imageLoader,
                    inflater = LayoutInflater.from(it),
                    callback = object : ComicsAdapter.Callback {
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

                recyclerView.layoutManager = GridLayoutManager(it, gridSpanCount)

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
            val layoutManager = recyclerView.layoutManager as GridLayoutManager

            layoutManager.spanCount = gridSpanCount
            adapter.setComicViewType(viewType)

            coroutineScope.launch {
                adapter.submitData(pagingData)
            }
        }
    }
}

@Composable
private fun BottomBar(
    actions: List<LibraryAction> = emptyList(),
    actionsEnabled: Boolean = false,
    isSyncing: Boolean = false,
    onActionClick: (action: LibraryAction) -> Unit = {},
    onAddClick: () -> Unit = {}
) {
    BottomAppBar(
        actions = {
            actions.forEach { action ->
                BottomBarAction(
                    action,
                    enabled = if (action == LibraryAction.Sync) {
                        actionsEnabled && !isSyncing
                    } else {
                        actionsEnabled
                    },
                    onClick = { onActionClick(action) }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.comic_list_add)
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomBarAction(
    action: LibraryAction,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    val icon: ImageVector
    val name: String

    when (action) {
        LibraryAction.ListView -> {
            icon = Icons.AutoMirrored.Rounded.List
            name = stringResource(R.string.comic_list_as_list)
        }

        LibraryAction.GridView -> {
            icon = Icons.Rounded.GridView
            name = stringResource(R.string.comic_list_as_grid)
        }

        LibraryAction.Sort -> {
            icon = Icons.AutoMirrored.Rounded.Sort
            name = stringResource(R.string.comic_list_sort)
        }

        LibraryAction.Filter -> {
            icon = Icons.Rounded.FilterAlt
            name = stringResource(R.string.comic_list_filter)
        }

        LibraryAction.Sync -> {
            icon = Icons.Rounded.Sync
            name = stringResource(R.string.comic_list_sync)
        }
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text = name) } },
        state = rememberTooltipState()
    ) {
        IconButton(
            enabled = enabled,
            onClick = onClick
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickAddComicBookModeBottomSheet(
    onModeSelected: (mode: AddComicBookMode, remember: Boolean) -> Unit = { _, _ -> },
    onDismiss: () -> Unit = {}
) {
    var selectedMode by remember { mutableStateOf(AddComicBookMode.Import) }
    var rememberMode by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState()

    @Composable
    fun ModeButton(mode: AddComicBookMode) {
        RadioButtonWithText(
            modifier = Modifier.fillMaxWidth(),
            text = "Import",
            selected = selectedMode == mode,
            onClick = { selectedMode = mode }
        )
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = "Mode",
                style = MaterialTheme.typography.labelLarge,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.selectableGroup()) {
                    AddComicBookMode.entries.forEach { mode ->
                        ModeButton(mode = mode)
                    }
                }
            }
        }

        CheckboxWithText(
            modifier = Modifier.fillMaxWidth(),
            checked = rememberMode,
            onCheckedChange = {
                rememberMode = !rememberMode
            },
            text = "Remember"
        )

        Button(
            onClick = {
                onModeSelected(selectedMode, rememberMode)

                coroutineScope.launch {
                    try {
                        sheetState.hide()
                    } finally {
                        onDismiss()
                    }
                }
            }
        ) {
            Text(text = "Add")
        }
    }
}

/**
 * Collect screen side effects
 * @param sideEffects side effects flow
 * @param snackbarState snackbar state
 * @param onNoComicBookSelector function will be called if no comic book selector is available
 * @param onComicBookSelectorResult function will be called on comic book selector result
 * @param onAddToLibrary function will be called when comic book is ready to be added to the library
 */
@Composable
private fun CollectSideEffects(
    sideEffects: Flow<LibrarySideEffect>,
    snackbarState: SnackbarHostState,
    onComicBookSelectorResult: (ChooseComicBookResult?) -> Unit = {},
    onNoComicBookSelector: () -> Unit = {},
    onAddToLibrary: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // We do not care if the permission was granted or not, add the selected comioc book anyway
            onAddToLibrary()
        }

    val comicBookChooserLauncher =
        rememberLauncherForActivityResult(ChooseComicBookContract(), onComicBookSelectorResult)

    LaunchedEffect(sideEffects, lifecycleOwner, snackbarState) {
        lifecycleOwner.repeatOnLifecycle(state = Lifecycle.State.STARTED) {
            sideEffects.collectLatest { sideEffect ->
                when (sideEffect) {
                    is LibrarySideEffect.ShowLibrarySelector -> {
                        if (!sideEffect.handle(comicBookChooserLauncher)) {
                            onNoComicBookSelector()
                        }
                    }

                    is LibraryListStoreContract.Label.ShowInstallLibrarySelectorMsg -> {
                        sideEffect.handle(context, snackbarState)
                    }

                    is LibraryListStoreContract.Label.RequestLibraryPermission -> {
                        sideEffect.handle(permissionLauncher)
                    }

                    LibraryListStoreContract.Label.OnAddLibraryStarted -> {
                        snackbarState.showSnackbar(
                            message = context.getString(R.string.comic_list_message_add_progress)
                        )
                    }
                }
            }
        }
    }
}

private fun LibrarySideEffect.ShowLibrarySelector.handle(
    comicBookChooserLauncher: ActivityResultLauncher<AddComicBookMode>,
): Boolean {
    Logger.debug { "Start comic book selector with adding mode: '${mode}'" }

    return try {
        comicBookChooserLauncher.launch(mode)
        true
    } catch (e: ActivityNotFoundException) {
        Logger.error(e) { "Can't start comic book selector for the mode: '${mode}'" }
        false
    }
}

private suspend fun LibrarySideEffect.ShowInstallLibrarySelectorMsg.handle(
    context: Context,
    snackbarState: SnackbarHostState
) {
    val result = snackbarState.showSnackbar(
        message = context.getString(R.string.comic_list_error_no_file_manager),
        actionLabel = installIntent?.let { context.getString(R.string.install) },
        duration = SnackbarDuration.Short,
    )

    if (result == SnackbarResult.ActionPerformed) {
        context.startActivity(installIntent)
    }
}

private fun LibrarySideEffect.RequestLibraryPermission.handle(
    permissionLauncher: ActivityResultLauncher<String>
) {
    Logger.debug { "Request Android permission: '${permission}'" }

    permissionLauncher.launch(permission)
}