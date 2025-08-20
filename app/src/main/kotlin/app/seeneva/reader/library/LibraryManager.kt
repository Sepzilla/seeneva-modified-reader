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

package app.seeneva.reader.library

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import app.seeneva.reader.AppNotification
import app.seeneva.reader.R
import app.seeneva.reader.library.add.AddComicBookWorker
import app.seeneva.reader.library.add.NewComicBookQueue
import app.seeneva.reader.logic.comic.AddComicBookMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

interface LibraryManager {
    fun add(paths: List<Uri>, addMode: AddComicBookMethod)
}

@Single
class DefaultLibraryManager(
    context: Context,
    @Provided
    private val workManager: WorkManager,
    private val provider: NewComicBookQueue,
    private val coroutineScope: CoroutineScope = ProcessLifecycleOwner.get().lifecycleScope
) : LibraryManager {
    private val context = context.applicationContext

    private val addWorkName: String = "${javaClass.name}@${ADD_WORK_NAME}"

    private val notificationManager =
        checkNotNull(context.getSystemService<NotificationManager>())

    init {
        coroutineScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(addWorkName)
                .collect {

                }
        }
    }

    override fun add(paths: List<Uri>, addMode: AddComicBookMethod) {
        coroutineScope.launch {
            val notification =
                NotificationCompat.Builder(context, AppNotification.Channel.FOREGROUND_TASK)
                    .setContentTitle("asdasdasd")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setGroup(AppNotification.Group.OPEN_COMIC_BOOK_METADATA)
                    .setGroupSummary(true)
                    .build()

            notificationManager.notify(1000, notification)

//            val r = paths.map {
//                OneTimeWorkRequestBuilder<AddComicBookWorker>()
//                    .setExpedited(OutOfQuotaPolicy.DROP_WORK_REQUEST)
//                    .build()
//            }
//
//            val rrr = buildList {
//                repeat(10){
//                    add(OneTimeWorkRequestBuilder<AddComicBookWorker>()
//                        .setExpedited(OutOfQuotaPolicy.DROP_WORK_REQUEST)
//                        .build())
//                }
//            }


            workManager.enqueueUniqueWork(
                addWorkName,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<AddComicBookWorker>()
                    .setExpedited(OutOfQuotaPolicy.DROP_WORK_REQUEST)
                    .build()
            )

//            workManager.enqueueUniqueWork(
//                addWorkName,
//                ExistingWorkPolicy.APPEND_OR_REPLACE,
//                r
//            )

//            paths.forEach {
//                val workRequest = OneTimeWorkRequestBuilder<AddComicBookWorker>()
//                    .setExpedited(OutOfQuotaPolicy.DROP_WORK_REQUEST)
//                    .build()
//
//
//
//                workManager.enqueueUniqueWork(
//                    addWorkName,
//                    ExistingWorkPolicy.APPEND_OR_REPLACE,
//                    workRequest
//                )
//            }


            //provider.add(paths, addMode)
        }
    }

    companion object {
        private const val ADD_WORK_NAME = "LIBRARY_ADD"
    }
}