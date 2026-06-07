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

        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            saveError(prefs, "Paramètres manquants (URL, utilisateur ou mot de passe vide)")
            return Result.failure()
        }

        if (ContextCompat.checkSelfPermission(
                applicationContext, android.Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            saveError(prefs, "Permission SMS non accordée")
            return Result.failure()
        }

        return try {
            val client = OcSmsClient(serverUrl, username, password)

            val lastTs = try {
                client.getLastTimestamp()
            } catch (e: Exception) {
                saveError(prefs, "Connexion échouée: ${e.javaClass.simpleName}: ${e.message}")
                Log.e("NcSms", "getLastTimestamp failed", e)
                return Result.failure()
            }

            val messages = SmsReader.readSince(applicationContext, lastTs)
            Log.d("NcSms", "Found ${messages.size} new messages since $lastTs")

            if (messages.isEmpty()) {
                prefs.edit()
                    .putLong("last_sync", System.currentTimeMillis())
                    .putInt("last_count", 0)
                    .putString("last_error", "")
                    .apply()
                return Result.success()
            }

            var pushed = 0
            for (chunk in messages.chunked(200)) {
                val ok = try {
                    client.push(chunk)
                } catch (e: Exception) {
                    saveError(prefs, "Envoi échoué: ${e.javaClass.simpleName}: ${e.message}")
                    Log.e("NcSms", "push failed", e)
                    return Result.failure()
                }
                if (!ok) {
                    saveError(prefs, "Le serveur a refusé les messages (vérifiez l'URL et le mot de passe)")
                    return Result.failure()
                }
                pushed += chunk.size
            }

            prefs.edit()
                .putLong("last_sync", System.currentTimeMillis())
                .putInt("last_count", pushed)
                .putString("last_error", "")
                .apply()
            Result.success()

        } catch (e: Exception) {
            saveError(prefs, "Erreur inattendue: ${e.javaClass.simpleName}: ${e.message}")
            Log.e("NcSms", "Unexpected sync error", e)
            Result.failure()
        }
    }

    private fun saveError(prefs: android.content.SharedPreferences, msg: String) {
        prefs.edit().putString("last_error", msg).apply()
        Log.e("NcSms", "Sync error: $msg")
    }

    companion object {
        const val WORK_NAME         = "ncsms_sync"
        const val WORK_NAME_ONESHOT = "ncsms_sync_now"

        fun schedule(context: Context, intervalHours: Long = 1L) {
            OutboxWorker.schedulePeriodic(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONESHOT, ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}
