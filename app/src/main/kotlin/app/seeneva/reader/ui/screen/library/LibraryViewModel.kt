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
import app.seeneva.reader.ui.screen.library.state.LibraryListStore
import app.seeneva.reader.ui.screen.library.state.LibraryListStoreContract
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Provides data related to user's library
 *
 * @param store
 */
class LibraryViewModel(private val store: LibraryListStore) : ViewModel() {
    val state: StateFlow<LibraryListStoreContract.State> = store.stateFlow(viewModelScope)

    @MainThread
    fun sendIntent(intent: LibraryListStoreContract.Intent) {
        store.accept(intent)
    }

    override fun onCleared() {
        super.onCleared()
        store.dispose()
    }
}