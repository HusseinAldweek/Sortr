package com.sortr.app.faces

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sortr.app.MainActivity
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount
import com.sortr.data.faces.FaceIndexer
import com.sortr.domain.model.FaceIndexProgress
import com.sortr.domain.model.FaceModelState
import com.sortr.domain.repository.AppSettings
import com.sortr.domain.repository.PeopleRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ScanStatus {
    IDLE,

    /** Queued: waiting for the charger, enough battery, or its turn. */
    WAITING,
    RUNNING,
}

/** Starts the background face scan. Safe to call often: an active scan is left alone. */
@Singleton
class FaceScanScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettings,
    private val people: PeopleRepository,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** [restart] replaces a running scan, e.g. after the charging setting changed. Progress is kept. */
    suspend fun schedule(restart: Boolean = false) {
        if (people.modelState.value != FaceModelState.READY) return
        val constraints = Constraints.Builder()
            .setRequiresCharging(settings.scanOnlyWhileCharging.first())
            .setRequiresBatteryNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<FaceScanWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            WORK_NAME,
            if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun status(): Flow<ScanStatus> = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
        when (infos.firstOrNull()?.state) {
            WorkInfo.State.RUNNING -> ScanStatus.RUNNING
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ScanStatus.WAITING
            else -> ScanStatus.IDLE
        }
    }

    private companion object {
        const val WORK_NAME = "face-scan"
    }
}

/**
 * Scans photos for faces until none are left. Runs in the foreground with a progress
 * notification so Android doesn't pause it; if that isn't allowed (e.g. started while the app is
 * in the background) it carries on as a normal job. Progress is saved after every batch.
 */
@HiltWorker
class FaceScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val indexer: FaceIndexer,
    private val people: PeopleRepository,
) : CoroutineWorker(context, params) {

    private var inForeground = false

    override suspend fun doWork(): Result {
        if (people.modelState.value != FaceModelState.READY) return Result.success()
        indexer.cleanUp()
        var progress = indexer.progress()
        if (progress.isComplete) return Result.success()

        inForeground = try {
            setForeground(foregroundInfo(progress))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "Scanning without a foreground notification", e)
            false
        }

        while (true) {
            val handled = try {
                indexer.indexNextBatch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Face scan batch failed", e)
                return if (people.modelState.value == FaceModelState.UNSUPPORTED) Result.failure() else Result.retry()
            }
            if (handled == 0) break
            progress = indexer.progress()
            if (inForeground) runCatching { setForeground(foregroundInfo(progress)) }
        }
        indexer.cleanUp()
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(indexer.progress())

    private fun foregroundInfo(progress: FaceIndexProgress): ForegroundInfo =
        ForegroundInfo(
            ScanNotifications.NOTIFICATION_ID,
            ScanNotifications.build(applicationContext, progress),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

    private companion object {
        const val TAG = "FaceScanWorker"
    }
}

object ScanNotifications {
    const val NOTIFICATION_ID = 1001
    private const val CHANNEL_ID = "face_scan"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_scan),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.notification_channel_scan_body) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(context: Context, progress: FaceIndexProgress): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_scan_title))
            .setContentText(
                context.getString(R.string.notification_scan_progress, formatCount(progress.indexed), formatCount(progress.total)),
            )
            .setProgress(progress.total.coerceAtLeast(1), progress.indexed, false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
