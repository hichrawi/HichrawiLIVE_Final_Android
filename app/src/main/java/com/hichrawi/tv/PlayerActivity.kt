package com.hichrawi.tv

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.graphics.BitmapFactory
import android.graphics.Color
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
        message = findViewById(R.id.playerMessage)
        findViewById<View>(R.id.playerBack).setOnClickListener { finish() }
        message.text = intent.getStringExtra("channel_name").orEmpty()
        // HICHRAWI SPORT 1..8 use their fixed channel logos.
        // All other channels keep the logo supplied by Admin.
        val channelName = intent.getStringExtra("channel_name").orEmpty()
        val sportLogo = sportLogoFor(channelName)
        if (sportLogo != 0) {
            logo.setImageResource(sportLogo)
            logo.visibility = View.VISIBLE
        } else {
            logo.setImageResource(R.drawable.hichrawi_live_logo)
            logo.visibility = View.VISIBLE
            val logoUrl = intent.getStringExtra("logo_url")
            if (!logoUrl.isNullOrBlank()) loadWatermark(logoUrl)
        }
        startPlayback()
    }


    private fun sportLogoFor(name: String): Int {
        val n = name.lowercase().replace(" ", "").replace("-", "")
        return when {
            n.contains("hichrawisport1") -> R.drawable.hichrawi_sport_1
            n.contains("hichrawisport2") -> R.drawable.hichrawi_sport_2
            n.contains("hichrawisport3") -> R.drawable.hichrawi_sport_3
            n.contains("hichrawisport4") -> R.drawable.hichrawi_sport_4
            n.contains("hichrawisport5") -> R.drawable.hichrawi_sport_5
            n.contains("hichrawisport6") -> R.drawable.hichrawi_sport_6
            n.contains("hichrawisport7") -> R.drawable.hichrawi_sport_7
            n.contains("hichrawisport8") -> R.drawable.hichrawi_sport_8
            else -> 0
        }
    }

    private fun loadWatermark(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder().url(url).build()
                okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bytes = response.body?.bytes() ?: return@use
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                    withContext(Dispatchers.Main) { logo.setImageBitmap(bmp) }
                }
            } catch (_: Exception) { }
        }
    }

    private fun startPlayback() {
        val deviceId = prefs.getLong("server_device_id", 0L)
        val channelId = intent.getLongExtra("channel_id", 0L)
        val directUrl = intent.getStringExtra("direct_url")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val state = Api.license(this@PlayerActivity, deviceId)
                if (state.optJSONObject("subscription")?.optBoolean("active") != true)
                    throw Exception("الاشتراك غير فعال")
                val url = if (!directUrl.isNullOrBlank()) directUrl else Api.playback(this@PlayerActivity, deviceId, channelId)
                withContext(Dispatchers.Main) { prepare(url) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { message.text = e.message ?: "تعذر تشغيل المحتوى" }
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
                    if (state.optJSONObject("subscription")?.optBoolean("active") != true) {
                        player?.stop()
                        message.text = "الاشتراك لم يعد فعالاً"
                        break
                    }
                } catch (_: Exception) { }
            }
        }
    }


    override fun onStop() {
        guardJob?.cancel()
        player?.release()
        player = null
        super.onStop()
    }
}
