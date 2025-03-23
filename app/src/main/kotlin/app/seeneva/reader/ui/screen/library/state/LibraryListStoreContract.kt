/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2025 Sergei Solodovnikov
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
@file:OptIn(ExperimentalCoroutinesApi::class)

package app.seeneva.reader.ui.screen.library.state

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.seeneva.reader.logic.ComicListViewType
import app.seeneva.reader.logic.ComicsSettings
import app.seeneva.reader.logic.comic.AddComicBookMode
import app.seeneva.reader.logic.comic.ComicHelper
import app.seeneva.reader.logic.comic.Library
import app.seeneva.reader.logic.entity.ComicListItem
import app.seeneva.reader.logic.entity.query.QueryParams
import app.seeneva.reader.logic.results.ChooseComicBookResult
import app.seeneva.reader.logic.usecase.ComicListUseCase
import app.seeneva.reader.ui.screen.library.model.LibraryAction
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Store for the library state
 */
typealias LibraryListStore = Store<LibraryListStoreContract.Intent, LibraryListStoreContract.State, LibraryListStoreContract.Label>

object LibraryListStoreContract {
    sealed interface State {
        /**
         * Is library menu enabled or not
         */
        val isMenuEnabled: Boolean
            get() = false

        /**
         * Is library in the process of sync
         */
        val isSyncing: Boolean
            get() = false

        /**
         * List of actions that can be performed on the library
         */
        val actions: List<LibraryAction>
            get() = emptyList()

        /**
         * The library is idle
         */
        data object Idle : State

        /**
         * The library is loading
         */
        data object Loading : State

        /**
         * The library is loaded
         *
         * @param viewType library view type
         * @param pagingData data pf the page
         * @param totalCount total count of comic books
         */
        data class Loaded(
            val viewType: ComicListViewType = ComicListViewType.default,
            val pagingData: PagingData<ComicListItem>,
            val totalCount: Long,
            private val syncState: Library.State = Library.State.IDLE
        ) : State {
            override val isMenuEnabled: Boolean
                get() = totalCount > 0L

            override val isSyncing: Boolean
                get() = syncState == Library.State.SYNCING

            override val actions: List<LibraryAction> = listOf(
                when (viewType) {
                    ComicListViewType.GRID ->
                        LibraryAction.ListView

                    ComicListViewType.LIST ->
                        LibraryAction.GridView
                },
                LibraryAction.Sort,
                LibraryAction.Filter,
                LibraryAction.Sync,
            )
        }
    }

    sealed interface Intent {
        /**
         * Load library paging data
         *
         * @param startIndex start loading position
         * @param pageSize size of loading page
         * @param queryParams
         */
        data class LoadPage(
            val startIndex: Int = 0,
            val pageSize: Int = 15,
            val queryParams: QueryParams = QueryParams.build()
        ) : Intent

        /**
         * Open a comic book selector
         *
         * @param mode selector mode
         * @param remember remember selected comic book mode
         */
        data class OpenLibrarySelector(
            val mode: AddComicBookMode,
            val remember: Boolean = false,
        ) : Intent

        /**
         * Process no comic book selector
         */
        data object ProcessNoLibrarySelector : Intent

        /**
         * Process a result of a comic book selector
         *
         * @param result result of a comic book selector
         */
        data class ProcessLibrarySelectorResult(
            val result: ChooseComicBookResult? = null
        ) : Intent

        /**
         * Add previously selected comic books to the library
         *
         * @see ProcessLibrarySelectorResult
         */
        data object AddToLibrary : Intent

        /**
         * Synchronize library
         */
        data object SyncLibrary : Intent

        /**
         * Set a new library view type
         */
        data class SetViewType(val viewType: ComicListViewType) : Intent
    }

    sealed interface Label {
        /**
         * Show a comic book selector
         *
         * @param mode selector mode
         */
        data class ShowLibrarySelector(
            val mode: AddComicBookMode
        ) : Label

        /**
         * Request required permission for the comic book adding process
         * @param permission permission to ask
         */
        data class RequestLibraryPermission(
            val permission: String
        ) : Label

        /**
         * Show install file manager message
         *
         * @param installIntent optional [android.content.Intent] to start
         */
        data class ShowInstallLibrarySelectorMsg(
            val installIntent: android.content.Intent? = null
        ) : Label

        /**
         * Notify user that add process is started
         */
        data object OnAddLibraryStarted : Label
    }

    private sealed interface Action {
        /**
         * Add previously selected comic books to the library
         *
         * @see Intent.AddToLibrary
         */
        data object AddToLibrary : Action
    }

    private sealed interface Msg {
        data object SetLibraryIdle : Msg

        data object SetLibraryLoading : Msg

        data class SetLibraryPageData(
            val viewType: ComicListViewType,
            val pageData: PagingData<ComicListItem>,
            val totalCount: Long,
            val syncState: Library.State
        ) : Msg

        data class SetSyncState(val syncState: Library.State) : Msg
    }


    fun createStore(
        storeFactory: StoreFactory,
        context: Context,
        libraryAddComicBookParamStore: LibraryAddComicBookParamStore,
        library: Library,
        settings: ComicsSettings,
        comicListUseCase: ComicListUseCase
    ): LibraryListStore =
        storeFactory.create<Intent, Action, Msg, State, Label>(
            name = "LibraryListStore",
            initialState = State.Idle,
            executorFactory = coroutineExecutorFactory {
                onIntent<Intent.LoadPage> { intent ->
                    val libraryPageFlow = comicListUseCase.getPagingData(
                        PagingConfig(intent.pageSize),
                        intent.queryParams,
                        intent.startIndex
                    ).cachedIn(this)
                        .mapLatest { it to comicListUseCase.totalCount(intent.queryParams) }

                    launch {
                        combine(
                            libraryPageFlow,
                            library.state,
                            settings.comicListTypeFlow(),
                        ) { (pagingData, totalCount), libraryState, viewType ->
                            Msg.SetLibraryPageData(
                                viewType = viewType,
                                pageData = pagingData,
                                totalCount = totalCount,
                                syncState = libraryState,
                            ) as Msg
                        }.onStart {
                            emit(Msg.SetLibraryLoading)
                        }.onCompletion {
                            emit(Msg.SetLibraryIdle)
                        }.collect {
                            dispatch(it)
                        }
                    }
                }

                onIntent<Intent.OpenLibrarySelector> {
                    // Save selected mode in the store, this helps to survive screen config changes
                    libraryAddComicBookParamStore.accept(
                        LibraryAddComicBookContract.Intent.SelectMode(it.mode)
                    )

                    publish(Label.ShowLibrarySelector(it.mode))
                }

                onIntent<Intent.ProcessNoLibrarySelector> {
                    libraryAddComicBookParamStore.accept(
                        LibraryAddComicBookContract.Intent.Clear
                    )

                    val installFileManagerIntent = ComicHelper.installFileManagerIntent
                        .takeIf { i -> i.resolveActivity(context.packageManager) != null }

                    publish(Label.ShowInstallLibrarySelectorMsg(installFileManagerIntent))
                }

                onIntent<Intent.ProcessLibrarySelectorResult> {
                    if (it.result != null) {
                        // Save selected paths and flags, so it will be easier to pass all the data between activity results
                        libraryAddComicBookParamStore.accept(
                            LibraryAddComicBookContract.Intent.SelectPath(
                                paths = it.result.paths,
                                permissionFlags = it.result.permissionFlags
                            )
                        )

                        // We need to request a notification permission for the foreground service
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            publish(Label.RequestLibraryPermission(android.Manifest.permission.POST_NOTIFICATIONS))
                        } else {
                            forward(Action.AddToLibrary)
                        }
                    } else {
                        // Clear previously saved data
                        libraryAddComicBookParamStore.accept(
                            LibraryAddComicBookContract.Intent.Clear
                        )
                    }
                }

                onIntent<Intent.AddToLibrary> {
                    forward(Action.AddToLibrary)
                }

                onIntent<Intent.SetViewType> {
                    settings.saveComicListType(it.viewType)
                }

                onIntent<Intent.SyncLibrary> {
                    if (library.state.value != Library.State.CHANGING && state() is State.Loaded) {
                        dispatch(Msg.SetSyncState(Library.State.SYNCING))

                        launch {
                            //Just a delay for a beautiful animation
                            delay(500L)
                            library.sync()
                        }
                    }
                }

                onAction<Action.AddToLibrary> {
                    val addState = libraryAddComicBookParamStore.state

                    if (addState is LibraryAddComicBookContract.State.Pending && addState.paths.isNotEmpty()) {
                        publish(Label.OnAddLibraryStarted)
                    }

                    libraryAddComicBookParamStore.accept(LibraryAddComicBookContract.Intent.Clear)
                }
            },
            reducer = { msg ->
                when (msg) {
                    Msg.SetLibraryIdle ->
                        State.Idle

                    Msg.SetLibraryLoading ->
                        State.Loading

                    is Msg.SetLibraryPageData -> {
                        State.Loaded(
                            viewType = msg.viewType,
                            pagingData = msg.pageData,
                            totalCount = msg.totalCount,
                            syncState = msg.syncState,
                        )
                    }

                    is Msg.SetSyncState -> {
                        if (this is State.Loaded) {
                            copy(syncState = msg.syncState)
                        } else {
                            this
                        }
                    }
                }
            }
        )
}