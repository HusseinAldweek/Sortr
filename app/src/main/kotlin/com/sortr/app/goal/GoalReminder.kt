package com.sortr.app.goal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sortr.app.R
import com.sortr.app.launch.LaunchRequests
import com.sortr.app.ui.common.formatCount
import com.sortr.domain.model.GoalProgress
import com.sortr.domain.repository.GoalRepository
import com.sortr.domain.repository.PhotoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules the daily goal reminder: once a day at the chosen time, while a goal is set. */
@Singleton
class GoalReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** [minutes] after midnight; null, or no [goal], turns the reminder off. */
    fun apply(goal: Int, minutes: Int?) {
        if (goal <= 0 || minutes == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<GoalReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntil(minutes), TimeUnit.MILLISECONDS)
            .build()
        // A new time starts a new daily rhythm.
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    private fun millisUntil(minutes: Int): Long {
        val now = LocalDateTime.now()
        var next = now.with(LocalTime.of(minutes / 60, minutes % 60))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }

    private companion object {
        const val WORK_NAME = "goal-reminder"
    }
}

/** Posts the reminder, but only if today's goal isn't reached and there's something left to sort. */
@HiltWorker
class GoalReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val goals: GoalRepository,
    private val photos: PhotoRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val progress = goals.progress.first()
        if (progress.enabled && !progress.reached && photos.observeSummary().first().toReview > 0) {
            GoalNotifications.show(applicationContext, progress)
        }
        return Result.success()
    }
}

object GoalNotifications {
    private const val CHANNEL_ID = "daily_goal"
    private const val NOTIFICATION_ID = 1002

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_goal),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_goal_body) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, progress: GoalProgress) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            context,
            REQUEST_SORT,
            LaunchRequests.sessionIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (progress.streak > 0) {
            context.resources.getQuantityString(R.plurals.notification_goal_streak_title, progress.streak, formatCount(progress.streak))
        } else {
            context.getString(R.string.notification_goal_title)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.goal_progress, formatCount(progress.today), formatCount(progress.goal)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private const val REQUEST_SORT = 2
}
