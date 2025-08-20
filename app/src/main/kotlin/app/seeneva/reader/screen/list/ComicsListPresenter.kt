/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021-2024 Sergei Solodovnikov
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

import android.net.Uri
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.savedstate.SavedStateRegistry
import app.seeneva.reader.common.coroutines.Dispatchers
import app.seeneva.reader.logic.ComicListViewType
import app.seeneva.reader.logic.ComicsSettings
import app.seeneva.reader.logic.comic.AddComicBookMethod
import app.seeneva.reader.logic.comic.Library
import app.seeneva.reader.logic.entity.query.QueryParams
import app.seeneva.reader.presenter.BasePresenter
import app.seeneva.reader.presenter.Presenter
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

interface ComicsListPresenter : Presenter {
    val pagingState: StateFlow<ComicsPagingState>

    val currentSearchQuery: String?

    /**
     * Load comic book paging data
     * @param startIndex start loading position
     * @param pageSize size of loading page
     * @see pagingState
     */
    fun loadComicsPagingData(startIndex: Int = 0, pageSize: Int = COMIC_PAGE_SIZE)

    /**
     * User click on sync button
     */
    fun onSyncClick()

    /**
     * Delete or mark as deleted
     * @param ids ids of comic book
     * @param permanent true - delete, false - mark as removed
     */
    fun deleteComicBook(ids: Set<Long>, permanent: Boolean = false)

    /**
     * Undo mark as removed
     * @param ids ids of comic book
     */
    fun undoComicRemove(ids: Set<Long>)

    /**
     * Rename comic book
     * @param id ikd of the comic book to rename
     * @param title buildNew title of the comic book
     */
    fun renameComicBook(id: Long, title: String)

    fun toggleComicCompletedMark(id: Long)

    /**
     * Set completed mark to all comics with provided [ids]
     * @param ids comic books ids
     * @param completed which flag should be set
     */
    fun setComicsCompletedMark(ids: Set<Long>, completed: Boolean)

    fun onListTypeChanged(listType: ComicListViewType)

    /**
     * Add provided comic books
     * @param mode adding mode
     * @param paths comic book paths
     * @param flags adding flags
     */
    fun addComicBooks(mode: AddComicBookMethod, paths: List<Uri>, flags: Int)

    companion object {
        private const val COMIC_PAGE_SIZE = 15
    }
}

class ComicsListPresenterImpl(
    view: ComicsListView,
    dispatchers: Dispatchers,
    private val settings: ComicsSettings,
    private val viewModel: ComicsListViewModel
) : BasePresenter<ComicsListView>(view, dispatchers),
    SavedStateRegistry.SavedStateProvider,
    ComicsListPresenter {
    override val pagingState: StateFlow<ComicsPagingState>
        get() = viewModel.pagingState

    private var queryParams: QueryParams
        get() = viewModel.queryParams
        set(newQueryParams) {
            if (queryParams.filters != newQueryParams.filters) {
                // showFilters(newQueryParams)
            }

            viewModel.queryParams = newQueryParams
        }

    override val currentSearchQuery: String?
        get() = queryParams.titleQuery

    init {
        //TODO
//        registerAndRestore(view) { state ->
//            view.setComicListType(settings.getComicListType())
//
//            if (state != null) {
//                //restore titleQuery if needed
//                onSearchQuery(state.getString(STATE_SEARCH_QUERY))
//            }
//        }

        viewScope.launch {
            view.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.eventsFlow
                        .collect {
                            when (val content = it) {
                                is ComicsMarkedAsRemoved -> view.onComicsMarkedRemoved(content.ids)
                                is ComicsOpened -> view.onComicAdded(content.result)
                            }
                        }
                }

                launch {
                    viewModel.libraryState
                        .collect {
                            when (it) {
                                Library.State.IDLE -> ComicsListView.SyncState.IDLE
                                Library.State.SYNCING -> ComicsListView.SyncState.IN_PROGRESS
                                Library.State.CHANGING -> ComicsListView.SyncState.DISABLED
                            }.also { state ->
                                Logger.debug("Set view sync state to: $state")

                                view.onSyncStateChanged(state)
                            }
                        }
                }
            }
        }
    }

    override fun saveState() =
        bundleOf(STATE_SEARCH_QUERY to currentSearchQuery)

    override fun loadComicsPagingData(startIndex: Int, pageSize: Int) {
        viewModel.loadComicsPagingData(startIndex, pageSize)
    }

    override fun onSyncClick() {
        viewModel.sync()
    }

    override fun deleteComicBook(ids: Set<Long>, permanent: Boolean) {
        if (permanent) {
            viewModel.permanentRemove(ids)
        } else {
            viewModel.setRemovedState(ids, true)
        }
    }

    override fun undoComicRemove(ids: Set<Long>) {
        viewModel.setRemovedState(ids, false)
    }

    override fun renameComicBook(id: Long, title: String) {
        viewModel.rename(id, title)
    }

    override fun toggleComicCompletedMark(id: Long) {
        viewModel.toggleCompletedMark(id)
    }

    override fun setComicsCompletedMark(ids: Set<Long>, completed: Boolean) {
        viewModel.setComicsCompletedMark(ids, completed)
    }

    override fun onListTypeChanged(listType: ComicListViewType) {
        settings.saveComicListType(listType)
    }

    override fun addComicBooks(mode: AddComicBookMethod, paths: List<Uri>, flags: Int) {
        viewModel.add(paths, mode, flags)
    }

    private companion object {
        private const val STATE_SEARCH_QUERY = "search_query"
    }
}