package com.hichrawi.tv

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var logo: ImageView
    private lateinit var message: TextView
    private val prefs by lazy { getSharedPreferences("hichrawi", MODE_PRIVATE) }
    private var player: ExoPlayer? = null
    private var guardJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.playerView)
        logo = findViewById(R.id.channelLogo)
        logo.setImageResource(R.drawable.hichrawi_live_logo)
        logo.visibility = View.VISIBLE
        message = findViewById(R.id.playerMessage)
        message.text = intent.getStringExtra("channel_name").orEmpty()
        intent.getStringExtra("logo_url")?.let(::loadLogo)
        startPlayback()
    }

    private fun startPlayback() {
        val deviceId = prefs.getLong("server_device_id", 0L)
        val channelId = intent.getLongExtra("channel_id", 0L)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val state = Api.license(this@PlayerActivity, deviceId)
                if (!isLicenseActive(state))
                    throw Exception("الاشتراك غير فعال")
                val url = Api.playback(this@PlayerActivity, deviceId, channelId)
                withContext(Dispatchers.Main) { prepare(url) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { message.text = e.message ?: "تعذر تشغيل القناة" }
            }
        }
    }

    private fun prepare(url: String) {
        val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
        val clean = url.substringBefore('?').lowercase()
        val builder = MediaItem.Builder().setUri(Uri.parse(url))
        when {
            clean.endsWith(".m3u8") || clean.contains("/m3u8") -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            clean.endsWith(".mp4") -> builder.setMimeType(MimeTypes.VIDEO_MP4)
            clean.endsWith(".mp3") -> builder.setMimeType(MimeTypes.AUDIO_MPEG)
            clean.endsWith(".aac") -> builder.setMimeType(MimeTypes.AUDIO_AAC)
        }
        player = ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build().also { p ->
            playerView.player = p
            p.setMediaItem(builder.build())
            p.prepare()
            p.playWhenReady = true
        }
        startLicenseGuard()
    }

    private fun startLicenseGuard() {
        val deviceId = prefs.getLong("server_device_id", 0L)
        guardJob?.cancel()
        guardJob = lifecycleScope.launch {
            while (true) {
                delay(60_000)
                try {
                    val state = withContext(Dispatchers.IO) { Api.license(this@PlayerActivity, deviceId) }
                    if (!isLicenseActive(state)) {
                        player?.stop()
                        message.text = "الاشتراك لم يعد فعالاً"
                        break
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private fun isLicenseActive(obj: org.json.JSONObject): Boolean {
        val sub = obj.optJSONObject("subscription")
        if (sub?.optBoolean("active") == true) return true
        val lic = obj.optJSONObject("license")
        if (lic?.optString("status").equals("active", true)) return true
        if (obj.optString("status").equals("active", true)) return true
        return false
    }

    private fun loadLogo(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val req = okhttp3.Request.Builder().url(url).build()
                okhttp3.OkHttpClient().newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@use
                    val bytes = r.body?.bytes() ?: return@use
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                    withContext(Dispatchers.Main) {
                        logo.setImageBitmap(bmp)
                        logo.visibility = View.VISIBLE
                    }
                }
            } catch (_: Exception) { }
        }
    }

    override fun onStop() {
        guardJob?.cancel()
        player?.release()
        player = null
        super.onStop()
    }
}
