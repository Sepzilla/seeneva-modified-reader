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

package app.seeneva.reader.ui.screen.library

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.seeneva.reader.ui.screen.library.state.LibraryAddComicBookParamStore
import app.seeneva.reader.ui.screen.library.state.LibraryListStore
import app.seeneva.reader.ui.screen.library.state.LibraryListStoreContract
import com.arkivanov.mvikotlin.extensions.coroutines.labelsChannel
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Provides data related to user's library
 *
 * @param libraryListStore
 */
class LibraryViewModel(
    private val libraryListStore: LibraryListStore,
    private val libraryAddComicBookParamStore: LibraryAddComicBookParamStore,
) : ViewModel() {
    val state = libraryListStore.stateFlow(viewModelScope)
    val sideEffects = libraryListStore.labelsChannel(viewModelScope).receiveAsFlow()

    @MainThread
    fun sendIntent(intent: LibraryListStoreContract.Intent) {
        libraryListStore.accept(intent)
    }

    override fun onCleared() {
        super.onCleared()
        libraryListStore.dispose()
        libraryAddComicBookParamStore.dispose()
    }
}