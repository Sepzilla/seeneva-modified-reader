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

package app.seeneva.reader.ui.screen.library.state

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.seeneva.reader.logic.comic.Library
import app.seeneva.reader.logic.entity.ComicListItem
import app.seeneva.reader.logic.entity.query.QueryParams
import app.seeneva.reader.logic.usecase.ComicListUseCase
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

/**
 * Store for the library state
 */
typealias LibraryListStore = Store<LibraryListStoreContract.Intent, LibraryListStoreContract.State, LibraryListStoreContract.Label>

object LibraryListStoreContract {
    sealed interface State {
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
         * @param pagingData data pf the page
         * @param totalCount total count of comic books
         * @param
         */
        data class Loaded(
            val pagingData: PagingData<ComicListItem>,
            val totalCount: Long,
            val syncState: Library.State = Library.State.IDLE
        ) : State
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
         * Synchronize library
         */
        data object Sync : Intent
    }

    sealed interface Label

    private sealed interface Action {
        data object CollectLibraryState : Action
    }

    private sealed interface Msg {
        data object SetLibraryIdle : Msg

        data object SetLibraryLoading : Msg

        data class SetLibraryPageData(
            val pageData: PagingData<ComicListItem>,
            val totalCount: Long
        ) : Msg

        data class SetSyncState(val syncState: Library.State) : Msg
    }

    fun createStore(
        storeFactory: StoreFactory,
        library: Library,
        comicListUseCase: ComicListUseCase
    ): LibraryListStore =
        storeFactory.create<Intent, Action, Msg, State, Label>(
            name = "ComicListFilterStore",
            initialState = State.Idle,
            executorFactory = coroutineExecutorFactory {
                onIntent<Intent.LoadPage> { intent ->
                    launch {
                        comicListUseCase.getPagingData(
                            PagingConfig(intent.pageSize),
                            intent.queryParams,
                            intent.startIndex
                        )
                            .cachedIn(this)
                            .mapLatest {
                                val totalCount = comicListUseCase.totalCount(intent.queryParams)

                                Msg.SetLibraryPageData(it, totalCount)
                            }.onStart {
                                dispatch(Msg.SetLibraryLoading)
                            }.onCompletion {
                                dispatch(Msg.SetLibraryIdle)
                            }.collect { msg ->
                                dispatch(msg)
                                forward(Action.CollectLibraryState)
                            }
                    }
                }

                onIntent<Intent.Sync> {
                    if (library.state.value != Library.State.CHANGING && state() is State.Loaded) {
                        dispatch(Msg.SetSyncState(Library.State.SYNCING))

                        launch {
                            //Just a delay for a beautiful animation
                            delay(500L)
                            library.sync()
                        }
                    }
                }

                onAction<Action.CollectLibraryState> {
                    launch {
                        library.state
                            .takeWhile { state() is State.Loaded }
                            .collect {
                                dispatch(Msg.SetSyncState(it))
                            }
                    }
                }
            },
            reducer = { msg ->
                when (msg) {
                    Msg.SetLibraryIdle ->
                        State.Idle

                    Msg.SetLibraryLoading ->
                        State.Loading

                    is Msg.SetLibraryPageData -> {
                        if (this is State.Loaded) {
                            copy(
                                pagingData = pagingData,
                                totalCount = msg.totalCount
                            )
                        } else {
                            State.Loaded(
                                pagingData = msg.pageData,
                                totalCount = msg.totalCount,
                                syncState = library.state.value
                            )
                        }
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