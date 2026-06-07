package be.ncsms

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.MessagingReceiver

class UnifiedPushReceiver : MessagingReceiver() {

    /**
     * Called by the UnifiedPush distributor (ntfy, etc.) when the app
     * receives a new endpoint URL. We save it locally and register it
     * with Nextcloud so it can POST wake-up signals here.
     */
    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        Log.d("NcSms", "UP new endpoint: $endpoint")
        val prefs = context.getSharedPreferences("ncsms", Context.MODE_PRIVATE)
        val previous = prefs.getString("up_endpoint", "")

        prefs.edit().putString("up_endpoint", endpoint).apply()

        val serverUrl = prefs.getString("server_url", "") ?: ""
        val username  = prefs.getString("username", "")  ?: ""
        val password  = prefs.getString("password", "")  ?: ""
        if (serverUrl.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OcSmsClient(serverUrl, username, password)
                // Unregister old endpoint if it changed
                if (!previous.isNullOrBlank() && previous != endpoint) {
                    client.unregisterPushEndpoint(previous)
                }
                client.registerPushEndpoint(endpoint)
                Log.d("NcSms", "UP endpoint registered with Nextcloud")
            } catch (e: Exception) {
                Log.e("NcSms", "Failed to register UP endpoint with Nextcloud", e)
            }
        }
    }

    /**
     * Called when ntfy/the distributor delivers a push message.
     * We don't read the payload — we just use it as a wake-up signal
     * and trigger an immediate outbox poll.
     */
    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        Log.d("NcSms", "UP message received — triggering OutboxWorker")
        OutboxWorker.runNow(context)
    }

    override fun onRegistrationFailed(context: Context, instance: String) {
        Log.w("NcSms", "UP registration failed — WorkManager periodic fallback still active")
    }

    /**
     * Called when the user switches or removes the UP distributor.
     * Clean up the registration on Nextcloud so it stops sending push.
     */
    override fun onUnregistered(context: Context, instance: String) {
        val prefs    = context.getSharedPreferences("ncsms", Context.MODE_PRIVATE)
        val endpoint = prefs.getString("up_endpoint", "") ?: ""
        prefs.edit().remove("up_endpoint").apply()

        if (endpoint.isBlank()) return
        val serverUrl = prefs.getString("server_url", "") ?: ""
        val username  = prefs.getString("username", "")  ?: ""
        val password  = prefs.getString("password", "")  ?: ""
        if (serverUrl.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                OcSmsClient(serverUrl, username, password).unregisterPushEndpoint(endpoint)
            } catch (e: Exception) {
                Log.e("NcSms", "Failed to unregister UP endpoint", e)
            }
        }
    }
}
