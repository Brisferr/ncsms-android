package be.ncsms

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class SmsEntry(
    val _id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: String,
    val seen: String,
    val mbox: Int,
    val type: Int
)

data class PushPayload(val smsCount: Int, val smsDatas: List<SmsEntry>)

class OcSmsClient(private val baseUrl: String, username: String, password: String) {

    private val gson = Gson()
    private val json = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Authorization", Credentials.basic(username, password))
                .header("OCS-APIREQUEST", "true")
                .build()
            chain.proceed(req)
        }
        .build()

    private fun url(path: String) = baseUrl.trimEnd('/') + "/apps/ocsms" + path

    fun getLastTimestamp(): Long {
        val req = Request.Builder().url(url("/get/lastmsgtime")).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return 0L
            val body = resp.body?.string() ?: return 0L
            val obj = gson.fromJson(body, JsonObject::class.java)
            return obj.get("timestamp")?.asLong ?: 0L
        }
    }

    fun push(messages: List<SmsEntry>): Boolean {
        if (messages.isEmpty()) return true
        val payload = PushPayload(messages.size, messages)
        val body = gson.toJson(payload).toRequestBody(json)
        val req = Request.Builder().url(url("/push")).post(body).build()
        client.newCall(req).execute().use { resp ->
            return resp.isSuccessful
        }
    }
}
