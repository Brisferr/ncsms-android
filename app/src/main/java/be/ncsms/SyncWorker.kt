package be.ncsms

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("ncsms", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        val username = prefs.getString("username", "") ?: ""
        val password = prefs.getString("password", "") ?: ""

        if (serverUrl.isBlank() || username.isBlank()) {
            return Result.failure()
        }

        if (ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure()
        }

        return try {
            val client = OcSmsClient(serverUrl, username, password)
            val lastTs = client.getLastTimestamp()
            val messages = SmsReader.readSince(applicationContext, lastTs)

            if (messages.isEmpty()) {
                prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply()
                return Result.success()
            }

            val chunkSize = 200
            var allOk = true
            messages.chunked(chunkSize).forEach { chunk ->
                if (!client.push(chunk)) allOk = false
            }

            if (allOk) {
                prefs.edit()
                    .putLong("last_sync", System.currentTimeMillis())
                    .putInt("last_count", messages.size)
                    .apply()
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("NcSms", "Sync error", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "ncsms_sync"

        fun schedule(context: Context, intervalHours: Long = 1L) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalHours, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun runNow(context: Context): androidx.work.WorkContinuation {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            return WorkManager.getInstance(context)
                .beginUniqueWork(
                    "${WORK_NAME}_manual",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }
}
