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

import android.net.Uri
import app.seeneva.reader.library.LibraryManager
import app.seeneva.reader.logic.comic.AddComicBookMethod
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory

typealias LibraryAddComicBookParamStore = Store<LibraryAddComicBookContract.Intent, LibraryAddComicBookContract.State, Nothing>

object LibraryAddComicBookContract {
    sealed interface State {
        data object Empty : State

        data class Pending(
            val mode: AddComicBookMethod,
            val paths: List<Uri> = emptyList(),
            val permissionFlags: Int = 0
        ) : State
    }

    sealed interface Intent {
        data class SelectMode(val mode: AddComicBookMethod) : Intent, Msg
        data class SelectPath(val paths: List<Uri>, val permissionFlags: Int) : Intent, Msg
        data object Clear : Intent, Msg
        data object AddToLibrary : Intent
    }

    private sealed interface Msg

    fun createStore(
        storeFactory: StoreFactory,
        libraryManager: LibraryManager,
    ) =
        storeFactory.create<Intent, Nothing, Msg, State, Nothing>(
            name = "LibraryAddComicBookStore",
            initialState = State.Empty,
            executorFactory = coroutineExecutorFactory {
                onIntent<Intent.SelectMode> {
                    dispatch(it)
                }

                onIntent<Intent.SelectPath> {
                    dispatch(it)
                }

                onIntent<Intent.Clear> {
                    dispatch(it)
                }

                onIntent<Intent.AddToLibrary> {
                    val state = state()

                    dispatch(Intent.Clear)

                    if (state is State.Pending && state.paths.isNotEmpty()) {
                        libraryManager.add(state.paths, state.mode)
                    }
                }
            },
            reducer = { msg ->
                when (msg) {
                    is Intent.SelectMode -> {
                        State.Pending(msg.mode)
                    }

                    is Intent.SelectPath -> {
                        if (this is State.Pending) {
                            copy(
                                paths = msg.paths,
                                permissionFlags = msg.permissionFlags
                            )
                        } else {
                            this
                        }
                    }

                    Intent.Clear -> {
                        State.Empty
                    }
                }
            }
        )
}