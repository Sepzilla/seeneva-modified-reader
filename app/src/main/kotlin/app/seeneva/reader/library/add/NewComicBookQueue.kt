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

package app.seeneva.reader.library.add

import android.net.Uri
import app.seeneva.reader.logic.comic.AddComicBookMethod
import app.seeneva.reader.logic.usecase.FileDataUseCase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

interface NewComicBookQueue {
    /**
     * Add new paths to the queue
     * @param paths comic book paths to add
     * @param mode add mode
     */
    suspend fun add(paths: List<Uri>, mode: AddComicBookMethod)

    /**
     * Retrieve the first item from the queue
     * @return `null` if there is no more items
     */
    suspend fun pop(): Item?

    data class Item(
        val path: Uri,
        val name: String,
        val size: Long,
        val mode: AddComicBookMethod
    )
}

@Single
class DefaultNewComicBookQueue(
    @Provided
    private val fileDataUseCase: FileDataUseCase,
) : NewComicBookQueue {
    private val queue = linkedMapOf<Uri, NewComicBookQueue.Item>()

    private val mutex = Mutex()

    override suspend fun add(paths: List<Uri>, mode: AddComicBookMethod) {
        mutex.withLock {
            paths.forEach {
                // Filter out duplicates
                if (!queue.contains(it)) {
                    //I decided to split file data receiving and file hash calculation to show notifications as soon as possible
                    val fileData = fileDataUseCase.getFileData(it)

                    queue[fileData.path] = NewComicBookQueue.Item(
                        path = fileData.path,
                        name = fileData.name,
                        size = fileData.size,
                        mode = mode
                    )
                }
            }
        }
    }

    override suspend fun pop(): NewComicBookQueue.Item? {
        return mutex.withLock { queue.remove(queue.keys.firstOrNull()) }
    }
}