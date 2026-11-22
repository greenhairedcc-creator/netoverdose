package app.netoverdose.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.netoverdose.MainActivity
import app.netoverdose.NetoverdoseApp
import app.netoverdose.R
import app.netoverdose.data.TaskEntity
import app.netoverdose.domain.TaskStatus
import java.util.concurrent.TimeUnit

/**
 * Inexact reminders via WorkManager. No exact alarms, no SCHEDULE_EXACT_ALARM.
 * WorkManager persists enqueued work across reboot; reschedule() also runs at
 * app start as a safety net against OEM battery managers.
 */
class Reminders(private val context: Context) {

    private val prefs = context.getSharedPreferences("netoverdose_settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun createChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = context.getString(R.string.notif_channel_desc)
        manager.createNotificationChannel(channel)
    }

    fun schedule(task: TaskEntity) {
        cancel(task.id)
        if (!enabled) return
        if (task.status == TaskStatus.DONE || task.status == TaskStatus.POSTPONED) return
        val now = System.currentTimeMillis()
        if (task.remindOnWindowStart && task.windowStart > now) {
            enqueue(task.id, KIND_WINDOW, 0, task.windowStart - now)
        }
        DEADLINE_REMINDER_HOURS.forEach { hours ->
            val at = task.deadline - hours * HOUR_MS
            if (at > now) enqueue(task.id, KIND_DEADLINE, hours, at - now)
        }
    }

    fun reschedule(tasks: List<TaskEntity>) {
        if (!enabled) {
            cancelAll()
            return
        }
        tasks.forEach { schedule(it) }
    }

    fun cancel(taskId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag(taskTag(taskId))
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_ALL)
    }

    private fun enqueue(taskId: Long, kind: String, hours: Int, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    KEY_TASK to taskId,
                    KEY_KIND to kind,
                    KEY_HOURS to hours
                )
            )
            .addTag(TAG_ALL)
            .addTag(taskTag(taskId))
            .build()
        val name = if (kind == KIND_WINDOW) "nd-$taskId-window" else "nd-$taskId-deadline-$hours"
        WorkManager.getInstance(context)
            .enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val CHANNEL_ID = "netoverdose_reminders"
        const val TAG_ALL = "netoverdose-reminder"
        const val KEY_TASK = "taskId"
        const val KEY_KIND = "kind"
        const val KEY_HOURS = "hours"
        const val KIND_WINDOW = "window"
        const val KIND_DEADLINE = "deadline"
        const val HOUR_MS = 3_600_000L
        val DEADLINE_REMINDER_HOURS = listOf(24, 3)
        private const val KEY_ENABLED = "notifications_enabled"
        fun taskTag(taskId: Long) = "netoverdose-task-$taskId"
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? NetoverdoseApp ?: return Result.success()
        val taskId = inputData.getLong(Reminders.KEY_TASK, 0L)
        if (taskId == 0L) return Result.success()
        if (!app.reminders.enabled) return Result.success()

        val task = app.database.tasks().get(taskId) ?: return Result.success()
        if (task.status == TaskStatus.DONE || task.status == TaskStatus.POSTPONED) return Result.success()

        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return Result.success()
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val kind = inputData.getString(Reminders.KEY_KIND) ?: Reminders.KIND_WINDOW
        val hours = inputData.getInt(Reminders.KEY_HOURS, 0)
        val title: String
        val text: String
        val index: Int
        if (kind == Reminders.KIND_WINDOW) {
            title = applicationContext.getString(R.string.notif_window_title)
            text = applicationContext.getString(R.string.notif_window_text, task.title)
            index = 0
        } else {
            title = applicationContext.getString(R.string.notif_deadline_title)
            text = applicationContext.getString(R.string.notif_deadline_text, task.title, hours)
            index = if (hours == 24) 1 else 2
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            applicationContext,
            (taskId * 10 + index).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, Reminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        manager.notify((taskId * 10 + index).toInt(), notification)
        return Result.success()
    }
}
