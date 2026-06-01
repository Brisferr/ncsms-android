package be.ncsms

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient

    init {
        // Accept all SSL certificates (needed for self-signed certs on home servers)
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())

        client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    private fun url(path: String) = baseUrl.trimEnd('/') + "/apps/ocsms" + path

    fun getLastTimestamp(): Long {
        val req = Request.Builder().url(url("/get/lastmsgtime")).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code} sur ${url("/get/lastmsgtime")}")
            }
            val body = resp.body?.string() ?: return 0L
            return try {
                gson.fromJson(body, JsonObject::class.java).get("timestamp")?.asLong ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
    }

    fun push(messages: List<SmsEntry>): Boolean {
        if (messages.isEmpty()) return true
        val payload = PushPayload(messages.size, messages)
        val body = gson.toJson(payload).toRequestBody(jsonMedia)
        val req = Request.Builder().url(url("/push")).post(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code} sur /push")
            }
            return true
        }
    }
}
