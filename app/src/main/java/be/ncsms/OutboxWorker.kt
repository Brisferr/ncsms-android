package be.ncsms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class OutboxWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("ncsms", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        val username  = prefs.getString("username", "")  ?: ""
        val password  = prefs.getString("password", "")  ?: ""

        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) return Result.success()

        return try {
            val client   = OcSmsClient(serverUrl, username, password)
            val messages = client.getOutboxMessages()
            Log.d("NcSms", "Outbox: ${messages.size} message(s) to send")

            val smsManager = applicationContext.getSystemService(SmsManager::class.java)

            for (msg in messages) {
                val success = trySend(smsManager, msg)
                if (success) client.markSent(msg.id) else client.markFailed(msg.id)
            }
            // Sync immediately so the sent SMS and any replies appear in ocsms
            if (messages.isNotEmpty()) SyncWorker.runNow(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e("NcSms", "OutboxWorker error", e)
            // Retry with backoff — WorkManager will try again automatically
            Result.retry()
        }
    }

    private fun trySend(smsManager: SmsManager, msg: OutboxMessage): Boolean {
        return try {
            val parts = smsManager.divideMessage(msg.msg)
            // sentIntent fires when the message leaves the device (handed to carrier)
            val sentIntent = PendingIntent.getBroadcast(
                applicationContext,
                msg.id,
                Intent("be.ncsms.SMS_SENT"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val sentIntents = ArrayList<PendingIntent>(parts.size).apply {
                repeat(parts.size) { add(sentIntent) }
            }
            smsManager.sendMultipartTextMessage(msg.address, null, parts, sentIntents, null)
            true
        } catch (e: Exception) {
            Log.e("NcSms", "SMS send failed to ${msg.address}", e)
            false
        }
    }

    companion object {
        const val WORK_NAME         = "ncsms_outbox"
        const val WORK_NAME_ONESHOT = "ncsms_outbox_now"

        /** Periodic fallback — runs every 15 min (Android minimum) */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<OutboxWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /** Immediate one-shot — triggered by UnifiedPush wake-up */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<OutboxWorker>()
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONESHOT, ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}
