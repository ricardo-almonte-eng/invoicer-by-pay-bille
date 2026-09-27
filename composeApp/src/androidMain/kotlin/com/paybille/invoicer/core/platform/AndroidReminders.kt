package com.paybille.invoicer.core.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Avisos con WorkManager: sobreviven a reinicios del teléfono y a que el sistema cierre la
 * app, sin permiso de alarmas exactas. Pueden llegar unos minutos tarde: para "hoy vence"
 * es suficiente.
 */
class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {

    override fun replaceAll(reminders: List<Reminder>) {
        val work = WorkManager.getInstance(context)
        work.cancelAllWorkByTag(TAG)
        val now = System.currentTimeMillis()
        reminders.filter { it.atEpochMillis > now }.forEach { reminder ->
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(reminder.atEpochMillis - now, TimeUnit.MILLISECONDS)
                .addTag(TAG)
                .setInputData(
                    workDataOf(
                        KEY_ID to reminder.id,
                        KEY_TITLE to reminder.title,
                        KEY_BODY to reminder.body,
                        KEY_SALE to (reminder.saleId ?: -1),
                    ),
                )
                .build()
            work.enqueueUniqueWork("$TAG-${reminder.id}", ExistingWorkPolicy.REPLACE, request)
        }
    }

    override fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    internal companion object {
        const val TAG = "invoice-reminder"
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_SALE = "saleId"
        const val CHANNEL_ID = "cobros"
    }
}

/** Extra del Intent que abre la app al tocar un aviso. `MainActivity` lo lee. */
const val EXTRA_SALE_ID = "com.paybille.invoicer.SALE_ID"

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success() // Sin permiso no hay nada que mostrar; no se reintenta.
        }
        ensureChannel(context)

        val saleId = inputData.getInt(AndroidReminderScheduler.KEY_SALE, -1)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            if (saleId > 0) putExtra(EXTRA_SALE_ID, saleId)
            addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val id = inputData.getString(AndroidReminderScheduler.KEY_ID).orEmpty()
        val pending = launch?.let {
            PendingIntent.getActivity(
                context,
                id.hashCode(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(context, AndroidReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(inputData.getString(AndroidReminderScheduler.KEY_TITLE))
            .setContentText(inputData.getString(AndroidReminderScheduler.KEY_BODY))
            .setStyle(NotificationCompat.BigTextStyle().bigText(inputData.getString(AndroidReminderScheduler.KEY_BODY)))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), notification) }
        return Result.success()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(AndroidReminderScheduler.CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(AndroidReminderScheduler.CHANNEL_ID, "Cobros y vencimientos", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Avisos de facturas que vencen o que hay que cobrar" },
        )
    }
}

@Composable
actual fun NotificationPermissionRequest(onResult: (granted: Boolean) -> Unit) {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        LaunchedEffect(Unit) { onResult(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
        return
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) onResult(true) else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
