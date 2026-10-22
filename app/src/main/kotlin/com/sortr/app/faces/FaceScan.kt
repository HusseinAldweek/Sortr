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
import com.sortr.data.analysis.ExactDuplicateIndexer
import com.sortr.data.analysis.PhotoAnalysisIndexer
import com.sortr.data.analysis.SmartIndexer
import com.sortr.data.faces.FaceIndexer
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

/**
 * Starts the background library scan: duplicates and blur first, then faces when the face
 * model is available. Safe to call often: an active scan is left alone.
 */
@Singleton
class FaceScanScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettings,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** [restart] replaces a running scan, e.g. after the charging setting changed. Progress is kept. */
    suspend fun schedule(restart: Boolean = false) {
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
 * Checks photos for similar copies and blur, then files for exact copies, then scans photos for
 * faces, until nothing is left.
 * Runs in the foreground with a progress notification so Android doesn't pause it; if that
 * isn't allowed (e.g. started while the app is in the background) it carries on as a normal
 * job. Progress is saved after every batch.
 */
@HiltWorker
class FaceScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val analysis: PhotoAnalysisIndexer,
    private val exactDuplicates: ExactDuplicateIndexer,
    private val smart: SmartIndexer,
    private val indexer: FaceIndexer,
    private val people: PeopleRepository,
) : CoroutineWorker(context, params) {

    private var inForeground = false

    private val facesEnabled: Boolean get() = people.modelState.value == FaceModelState.READY

    override suspend fun doWork(): Result {
        if (facesEnabled) indexer.cleanUp()
        val analysed = analysis.progress()
        val faces = if (facesEnabled) indexer.progress() else null
        val exactPending = exactDuplicates.pending()
        val smartDone = smart.progress().isComplete
        if (analysed.isComplete && exactPending == 0 && smartDone && (faces == null || faces.isComplete)) return Result.success()

        inForeground = try {
            setForeground(foregroundInfo(currentStage()))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "Scanning without a foreground notification", e)
            false
        }

        val analysisResult = runBatches(ScanStage.Kind.ANALYSIS) { analysis.analyseNextBatch() }
        if (analysisResult != null) return analysisResult
        val exactResult = runBatches(ScanStage.Kind.ANALYSIS) { exactDuplicates.fingerprintNextBatch() }
        if (exactResult != null) return exactResult
        if (facesEnabled) {
            val faceResult = runBatches(ScanStage.Kind.FACES) { indexer.indexNextBatch() }
            if (faceResult != null) {
                return if (people.modelState.value == FaceModelState.UNSUPPORTED) Result.failure() else faceResult
            }
            indexer.cleanUp()
        }
        // Categories and text search last: the slowest step, and the least needed for sorting.
        val smartResult = runBatches(ScanStage.Kind.SMART) { smart.indexNextBatch() }
        if (smartResult != null) return smartResult
        return Result.success()
    }

    /** Runs [batch] until it reports nothing left. Returns a result only if it has to stop early. */
    private suspend fun runBatches(kind: ScanStage.Kind, batch: suspend () -> Int): Result? {
        while (true) {
            val handled = try {
                batch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Scan batch failed ($kind)", e)
                return Result.retry()
            }
            if (handled == 0) return null
            if (inForeground) runCatching { setForeground(foregroundInfo(currentStage())) }
        }
    }

    private suspend fun currentStage(): ScanStage {
        val analysed = analysis.progress()
        if (!analysed.isComplete) return ScanStage(ScanStage.Kind.ANALYSIS, analysed.analysed, analysed.total)
        val faces = if (facesEnabled) indexer.progress() else null
        if (faces != null && !faces.isComplete) return ScanStage(ScanStage.Kind.FACES, faces.indexed, faces.total)
        val read = smart.progress()
        return ScanStage(ScanStage.Kind.SMART, read.analysed, read.total)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(currentStage())

    private fun foregroundInfo(stage: ScanStage): ForegroundInfo =
        ForegroundInfo(
            ScanNotifications.NOTIFICATION_ID,
            ScanNotifications.build(applicationContext, stage),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

    private companion object {
        const val TAG = "LibraryScan"
    }
}

/** What the background scan is doing, for its notification. */
data class ScanStage(val kind: Kind, val done: Int, val total: Int) {
    enum class Kind { ANALYSIS, FACES, SMART }
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

    fun build(context: Context, stage: ScanStage): Notification {
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
                context.getString(
                    when (stage.kind) {
                        ScanStage.Kind.ANALYSIS -> R.string.notification_analysis_progress
                        ScanStage.Kind.FACES -> R.string.notification_scan_progress
                        ScanStage.Kind.SMART -> R.string.notification_smart_progress
                    },
                    formatCount(stage.done),
                    formatCount(stage.total),
                ),
            )
            .setProgress(stage.total.coerceAtLeast(1), stage.done, false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
