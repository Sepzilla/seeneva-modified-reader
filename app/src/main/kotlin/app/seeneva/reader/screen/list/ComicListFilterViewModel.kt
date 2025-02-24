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

package app.seeneva.reader.screen.list

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.seeneva.reader.screen.list.state.ComicListFilterStore
import app.seeneva.reader.screen.list.state.ComicListFilterStoreContract
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class ComicListFilterViewModel(
    private val store: ComicListFilterStore,
) : ViewModel() {
    val state: StateFlow<ComicListFilterStoreContract.State>
        get() = store.stateFlow(scope = viewModelScope)

    val labels: Flow<ComicListFilterStoreContract.Label>
        get() = store.labels

    @MainThread
    fun sendIntent(intent: ComicListFilterStoreContract.Intent) {
        store.accept(intent)
    }

    override fun onCleared() {
        super.onCleared()
        store.dispose()
    }
}