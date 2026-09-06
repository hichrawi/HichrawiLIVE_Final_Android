package com.hichrawi.tv

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object AppConfig {
    private const val PREFS = "hichrawi"
    private const val API_KEY = "api_base"
    private const val FALLBACK_API = "https://api.158.179.208.77.sslip.io"
    private const val REMOTE_CONFIG = "https://hichrawi-tv-app.web.app/app-config.json"
    @Volatile private var apiBase: String = FALLBACK_API

    data class UpdateInfo(val latestVersionCode: Int, val apkUrl: String?, val force: Boolean, val message: String?)
    private var cachedUpdate = UpdateInfo(0, null, false, null)

    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()

    fun apiBase(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(API_KEY, null)
        if (!saved.isNullOrBlank()) apiBase = saved.trimEnd('/')
        return apiBase
    }

    fun refreshRemote(context: Context) {
        try {
            val req = Request.Builder().url(REMOTE_CONFIG).header("Cache-Control", "no-cache").build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body?.string().orEmpty(); val obj = JSONObject(body)
                val base = obj.optString("api_base").trim().trimEnd('/')
                if (base.startsWith("https://") || base.startsWith("http://")) {
                    apiBase = base; context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(API_KEY, base).apply()
                }
                cachedUpdate = UpdateInfo(obj.optInt("latest_version_code", 0), obj.optString("apk_url").takeIf { it.isNotBlank() }, obj.optBoolean("force_update", false), obj.optString("update_message").takeIf { it.isNotBlank() })
            }
        } catch (_: Exception) { }
    }

    fun updateInfo(): UpdateInfo = cachedUpdate
    fun deviceKey(context: Context): String { val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);var v=p.getString("device_key",null);if(v==null){v=UUID.randomUUID().toString();p.edit().putString("device_key",v).apply()};return v }
    fun serverDeviceId(context: Context): Long = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("server_device_id", 0L)
    fun saveServerDeviceId(context: Context, id: Long) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("server_device_id", id).apply()
}
