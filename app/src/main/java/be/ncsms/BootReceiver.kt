package be.ncsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("ncsms", Context.MODE_PRIVATE)
            val intervalHours = prefs.getLong("interval_hours", 1L)
            SyncWorker.schedule(context, intervalHours)
        }
    }
}
