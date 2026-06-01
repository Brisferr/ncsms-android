package be.ncsms

import android.content.Context
import android.net.Uri
import android.provider.Telephony

object SmsReader {

    private val SMS_URI = Uri.parse("content://sms")
    private val PROJECTION = arrayOf("_id", "address", "body", "date", "read", "seen", "type")

    fun readSince(context: Context, sinceTimestamp: Long): List<SmsEntry> {
        val results = mutableListOf<SmsEntry>()
        val selection = if (sinceTimestamp > 0) "date > ?" else null
        val selArgs = if (sinceTimestamp > 0) arrayOf(sinceTimestamp.toString()) else null

        context.contentResolver.query(
            SMS_URI, PROJECTION, selection, selArgs, "date ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("_id")
            val addrIdx = cursor.getColumnIndexOrThrow("address")
            val bodyIdx = cursor.getColumnIndexOrThrow("body")
            val dateIdx = cursor.getColumnIndexOrThrow("date")
            val readIdx = cursor.getColumnIndexOrThrow("read")
            val seenIdx = cursor.getColumnIndexOrThrow("seen")
            val typeIdx = cursor.getColumnIndexOrThrow("type")

            while (cursor.moveToNext()) {
                val type = cursor.getInt(typeIdx)
                val mbox = when (type) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> 0
                    Telephony.Sms.MESSAGE_TYPE_SENT -> 1
                    Telephony.Sms.MESSAGE_TYPE_DRAFT -> 2
                    else -> 0
                }
                results.add(SmsEntry(
                    _id = cursor.getLong(idIdx),
                    address = cursor.getString(addrIdx) ?: "",
                    body = cursor.getString(bodyIdx) ?: "",
                    date = cursor.getLong(dateIdx),
                    read = if (cursor.getInt(readIdx) == 1) "true" else "false",
                    seen = if (cursor.getInt(seenIdx) == 1) "true" else "false",
                    mbox = mbox,
                    type = type
                ))
            }
        }
        return results
    }
}
