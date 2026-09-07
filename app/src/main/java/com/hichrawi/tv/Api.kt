package com.hichrawi.tv

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Api {
    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class Channel(val id: Long, val name: String, val logoUrl: String?, val sortOrder: Int)
    data class Package(val id: Long, val name: String, val channelIds: List<Long>)

    private fun request(context: Context, path: String, body: JSONObject? = null, method: String = "GET"): JSONObject {
        val base = AppConfig.apiBase(context)
        val builder = Request.Builder().url(base + path).header("Accept", "application/json")
        if (body != null) builder.method(method, body.toString().toRequestBody(json))
        else builder.method(method, null)
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val obj = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
            response.header("Date")?.takeIf { it.isNotBlank() }?.let { obj.put("_server_date", it) }
            if (!response.isSuccessful || (obj.optBoolean("ok", false).not() && !obj.has("subscription"))) {
                throw Exception(obj.optString("error").ifBlank { "تعذر الاتصال بالخادم" })
            }
            return obj
        }
    }

    fun registerDevice(context: Context, deviceKey: String): Long {
        val obj = request(context, "/api/v1/register-device.php", JSONObject().put("device_id", deviceKey), "POST")
        return obj.optLong("device_id").takeIf { it > 0 } ?: throw Exception("تعذر تسجيل الجهاز")
    }

    fun activate(context: Context, code: String, deviceId: Long): JSONObject = request(
        context, "/api/v1/activate.php", JSONObject().put("code", code).put("device_id", deviceId), "POST"
    )

    fun license(context: Context, deviceId: Long): JSONObject = request(context, "/api/v1/license.php?device_id=$deviceId")

    fun channels(context: Context, deviceId: Long): List<Channel> {
        val obj = request(context, "/api/v1/channels.php?device_id=$deviceId")
        val arr = obj.optJSONArray("channels") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val c = arr.optJSONObject(i) ?: return@mapNotNull null
            Channel(c.optLong("id"), c.optString("name").ifBlank { c.optString("channel_key", "Channel") },
                c.optString("logo_url").takeIf { it.isNotBlank() }, c.optInt("sort_order", i))
        }.sortedWith(compareBy<Channel> { it.sortOrder }.thenBy { it.id })
    }

    fun playback(context: Context, deviceId: Long, channelId: Long): String {
        val obj = request(context, "/api/v1/playback-token.php",
            JSONObject().put("channel_id", channelId).put("device_id", deviceId), "POST")
        return obj.optString("playback_url").takeIf { it.isNotBlank() }
            ?: throw Exception("لم يرجع الخادم رابط التشغيل")
    }

    fun packages(context: Context, deviceId: Long, channels: List<Channel>): List<Package> {
        return try {
            val obj = request(context, "/api/v1/packages.php?device_id=$deviceId")
            val arr = obj.optJSONArray("packages") ?: return fallbackPackages(channels)
            (0 until arr.length()).mapNotNull { i ->
                val p = arr.optJSONObject(i) ?: return@mapNotNull null
                val a = p.optJSONArray("channel_ids")
                val ids = if (a == null) emptyList() else (0 until a.length()).mapNotNull { j -> a.optLong(j).takeIf { id -> id > 0 } }
                Package(p.optLong("id", i.toLong()), p.optString("name", "الباقة"), ids)
            }
        } catch (_: Exception) { fallbackPackages(channels) }
    }

    private fun fallbackPackages(channels: List<Channel>): List<Package> {
        val sports = channels.filter { it.name.contains("sport", true) || it.name.contains("سبورت", true) || it.name.contains("رياض", true) }
        val result = mutableListOf<Package>()
        if (sports.isNotEmpty()) result += Package(1, "الباقة الرياضية", sports.map { it.id })
        if (channels.isNotEmpty()) result += Package(2, "الباقة الكاملة", channels.map { it.id })
        return result
    }
}
