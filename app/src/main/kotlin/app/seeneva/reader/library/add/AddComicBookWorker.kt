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

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.seeneva.reader.AppNotification
import app.seeneva.reader.R
import app.seeneva.reader.logic.comic.Library
import app.seeneva.reader.logic.usecase.FileDataUseCase
import kotlinx.coroutines.delay
import org.koin.android.annotation.KoinWorker
import org.koin.core.annotation.Provided
import org.tinylog.kotlin.Logger

@KoinWorker
class AddComicBookWorker(
    appContext: Context,
    params: WorkerParameters,
    private val provider: NewComicBookQueue,
    @Provided
    private val fileDataUseCase: FileDataUseCase,
    @Provided
    private val library: Library,
) : CoroutineWorker(appContext, params) {
    private val notificationId = hashCode()

    private val notificationManager =
        checkNotNull(appContext.getSystemService<NotificationManager>())

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo())

//        while (currentCoroutineContext().isActive) {
//            val item = provider.pop() ?: break
//            processItem(item)
//        }

        Logger.debug("!!!!!!!!!!!!!!!!!!")

        delay(10_0000)

        return Result.success()
    }

    private suspend fun processItem(item: NewComicBookQueue.Item) {
        val fileHashData = fileDataUseCase.getFileHashData(item.path)

        delay(10_0000)

//        val result = library.add(
//            fileData = FullFileData(
//                path = item.path,
//                name = item.name,
//                size = fileHashData.size,
//                hash = fileHashData.hash
//            ),
//            addMode = item.mode
//        )
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification =
            NotificationCompat.Builder(applicationContext, AppNotification.Channel.FOREGROUND_TASK)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setChannelId(AppNotification.Channel.FOREGROUND_TASK)
                .setGroup(AppNotification.Group.OPEN_COMIC_BOOK_METADATA)
                .setContentTitle("Blah")
                .setOngoing(true)
                .build()

        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        return ForegroundInfo(
            notificationId,
            notification,
            foregroundServiceType
        )
    }

    companion object {

    }
}