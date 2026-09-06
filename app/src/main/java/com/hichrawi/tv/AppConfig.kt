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

    data class UpdateInfo(
        val latestVersionCode: Int,
        val apkUrl: String?,
        val force: Boolean,
        val message: String?
    )

    data class Social(
        val facebook: String = "",
        val facebookEnabled: Boolean = false,
        val tiktok: String = "",
        val tiktokEnabled: Boolean = false,
        val whatsapp: String = "",
        val whatsappEnabled: Boolean = false
    )

    data class AppSettings(
        val appName: String = "HICHRAWI LIVE",
        val subtitle: String = "قنوات مباشرة",
        val logoUrl: String = "",
        val social: Social = Social(),
        val maintenance: Boolean = false,
        val maintenanceMessage: String = "التطبيق تحت الصيانة مؤقتاً.",
        val homeLiveTitle: String = "LIVE",
    )

    private var cachedUpdate = UpdateInfo(0, null, false, null)
    @Volatile private var cachedSettings = AppSettings()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun apiBase(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(API_KEY, null)
        if (!saved.isNullOrBlank()) apiBase = saved.trimEnd('/')
        return apiBase
    }

    fun refreshRemote(context: Context) {
        try {
            val req = Request.Builder()
                .url(REMOTE_CONFIG)
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body?.string().orEmpty()
                val obj = JSONObject(body)

                val base = obj.optString("api_base").trim().trimEnd('/')
                if (base.startsWith("https://") || base.startsWith("http://")) {
                    apiBase = base
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString(API_KEY, base).apply()
                }

                cachedUpdate = UpdateInfo(
                    obj.optInt("latest_version_code", 0),
                    obj.optString("apk_url").takeIf { it.isNotBlank() },
                    obj.optBoolean("force_update", false),
                    obj.optString("update_message").takeIf { it.isNotBlank() }
                )
                cachedSettings = parseSettings(obj, cachedSettings)
            }
        } catch (_: Exception) { }
    }

    fun applyApiSettings(obj: JSONObject) {
        cachedSettings = parseSettings(obj, cachedSettings)
    }

    private fun parseSettings(obj: JSONObject, previous: AppSettings): AppSettings {
        val s = obj.optJSONObject("settings") ?: obj
        val socialObj = s.optJSONObject("social") ?: JSONObject()
        return previous.copy(
            appName = s.optString("app_name").takeIf { it.isNotBlank() } ?: previous.appName,
            subtitle = s.optString("subtitle").takeIf { it.isNotBlank() } ?: previous.subtitle,
            logoUrl = s.optString("logo_url").takeIf { it.isNotBlank() } ?: previous.logoUrl,
            maintenance = if (s.has("maintenance")) s.optBoolean("maintenance") else previous.maintenance,
            maintenanceMessage = s.optString("maintenance_message").takeIf { it.isNotBlank() } ?: previous.maintenanceMessage,
            homeLiveTitle = s.optString("home_live_title").takeIf { it.isNotBlank() } ?: previous.homeLiveTitle,
            social = previous.social.copy(
                facebook = socialObj.optString("facebook").takeIf { it.isNotBlank() } ?: previous.social.facebook,
                facebookEnabled = if (socialObj.has("facebook_enabled")) socialObj.optBoolean("facebook_enabled") else previous.social.facebookEnabled,
                tiktok = socialObj.optString("tiktok").takeIf { it.isNotBlank() } ?: previous.social.tiktok,
                tiktokEnabled = if (socialObj.has("tiktok_enabled")) socialObj.optBoolean("tiktok_enabled") else previous.social.tiktokEnabled,
                whatsapp = socialObj.optString("whatsapp").takeIf { it.isNotBlank() } ?: previous.social.whatsapp,
                whatsappEnabled = if (socialObj.has("whatsapp_enabled")) socialObj.optBoolean("whatsapp_enabled") else previous.social.whatsappEnabled
            )
        )
    }

    fun updateInfo(): UpdateInfo = cachedUpdate
    fun settings(): AppSettings = cachedSettings

    fun deviceKey(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var v = p.getString("device_key", null)
        if (v == null) {
            v = UUID.randomUUID().toString()
            p.edit().putString("device_key", v).apply()
        }
        return v
    }

    fun serverDeviceId(context: Context): Long = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getLong("server_device_id", 0L)

    fun saveServerDeviceId(context: Context, id: Long) = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putLong("server_device_id", id).apply()
}
