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

package app.seeneva.reader.screen.list.state

import app.seeneva.reader.logic.ComicsSettings
import app.seeneva.reader.logic.entity.query.QueryParams
import app.seeneva.reader.logic.entity.query.QuerySort
import app.seeneva.reader.logic.entity.query.filter.Filter
import app.seeneva.reader.logic.entity.query.filter.FilterGroup
import app.seeneva.reader.screen.list.entity.FilterLabel
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineBootstrapper
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import kotlinx.coroutines.launch

typealias ComicListFilterStore = Store<ComicListFilterStoreContract.Intent, ComicListFilterStoreContract.State, ComicListFilterStoreContract.Label>

object ComicListFilterStoreContract {
    sealed interface Intent {
        /**
         * Edit filters
         */
        data object EditFilters : Intent

        /**
         * Edit selected sort
         */
        data object EditSort : Intent

        /**
         * Remove the specified filter
         */
        data class RemoveFilter(val groupId: FilterGroup.ID) : Intent

        /**
         * Set provided filters
         */
        data class SetFilters(val filters: Map<FilterGroup.ID, Filter>) : Intent

        /**
         * Select provided sort
         */
        data class SelectSort(val selectedSort: QuerySort) : Intent

        /**
         * Set comic book title query
         */
        data class SetQuery(val query: String? = null) : Intent
    }

    sealed interface Label {
        data class OpenFilterEditor(val selectedFilters: Map<FilterGroup.ID, Filter>) : Label

        /**
         * Show sort selector
         * @param currentSort current selected sort
         */
        data class OpenSortSelector(val currentSort: QuerySort) : Label
    }

    private sealed interface Action {
        /**
         * Init query params
         */
        data object Init : Action

        /**
         * Save current query params
         */
        data class SaveQueryParams(val queryParams: QueryParams) : Action
    }

    private sealed interface Msg {
        data class NewQueryParams(val queryParams: QueryParams) : Msg
    }

    /**
     * @param params current params
     */
    data class State(
        val params: QueryParams = QueryParams.build(),
    ) {
        val filterLabels = params.filters.map { (groupId, filter) ->
            FilterLabel(groupId, filter.title)
        }
    }

    fun createComicListFilterStore(
        storeFactory: StoreFactory,
        settings: ComicsSettings
    ): ComicListFilterStore =
        storeFactory.create<Intent, Action, Msg, State, Label>(
            name = "ComicListFilterStore",
            initialState = State(),
            bootstrapper = coroutineBootstrapper {
                dispatch(Action.Init)
            },
            executorFactory = coroutineExecutorFactory {
                onAction<Action.Init> {
                    dispatch(Msg.NewQueryParams(queryParams = settings.getComicListQueryParams()))
                }

                onAction<Action.SaveQueryParams> {
                    launch {
                        settings.saveComicListQueryParams(it.queryParams)
                    }
                }

                onIntent<Intent.EditFilters> {
                    publish(Label.OpenFilterEditor(state().params.filters))
                }

                onIntent<Intent.EditSort> {
                    publish(Label.OpenSortSelector(state().params.sort))
                }

                onIntent<Intent.SelectSort> {
                    if (it.selectedSort == state().params.sort) {
                        return@onIntent
                    }

                    val newQueryParams = state().params.buildNew {
                        sort = it.selectedSort
                    }

                    dispatch(Msg.NewQueryParams(queryParams = newQueryParams))

                    forward(Action.SaveQueryParams(queryParams = newQueryParams))
                }

                onIntent<Intent.RemoveFilter> {
                    if (!state().params.filters.containsKey(it.groupId)) {
                        return@onIntent
                    }

                    val newQueryParams = state().params.buildNew {
                        removeFilter(
                            it.groupId
                        )
                    }

                    dispatch(Msg.NewQueryParams(queryParams = newQueryParams))

                    forward(Action.SaveQueryParams(queryParams = newQueryParams))
                }

                onIntent<Intent.SetQuery> {
                    if (state().params.titleQuery == it.query) {
                        return@onIntent
                    }

                    val newQueryParams = state().params.buildNew {
                        titleQuery = it.query
                    }

                    dispatch(Msg.NewQueryParams(queryParams = newQueryParams))

                    forward(Action.SaveQueryParams(queryParams = newQueryParams))
                }

                onIntent<Intent.SetFilters> {
                    val newQueryParams = state().params.buildNew {
                        it.filters.forEach { (id, filter) ->
                            addFilter(id, filter)
                        }
                    }

                    dispatch(Msg.NewQueryParams(queryParams = newQueryParams))

                    forward(Action.SaveQueryParams(queryParams = newQueryParams))
                }
            },
            reducer = { msg ->
                when (msg) {
                    is Msg.NewQueryParams -> {
                        copy(params = msg.queryParams)
                    }
                }
            }
        )
}