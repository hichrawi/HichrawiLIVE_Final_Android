package com.hichrawi.tv

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.graphics.BitmapFactory
import android.graphics.Bitmap
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
        // Use the HICHRAWI logo assigned to this channel in Admin.
        // It is intentionally an overlay on top of the broadcaster logo area.
        val logoUrl = intent.getStringExtra("logo_url")
        if (!logoUrl.isNullOrBlank()) {
            loadWatermark(logoUrl)
        } else {
            logo.setImageResource(R.drawable.hichrawi_live_logo)
            logo.visibility = View.VISIBLE
        }
        startPlayback()
    }

    private fun loadWatermark(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder().url(url).build()
                okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bytes = response.body?.bytes() ?: return@use
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                    val cropped = cropTransparentBorders(bmp)
                    withContext(Dispatchers.Main) {
                        logo.setImageBitmap(cropped)
                        logo.visibility = View.VISIBLE
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun cropTransparentBorders(source: Bitmap): Bitmap {
        if (!source.hasAlpha()) return source
        val w = source.width
        val h = source.height
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h step 2) {
            for (x in 0 until w step 2) {
                val a = (source.getPixel(x, y) ushr 24) and 0xFF
                if (a > 12) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) return source
        val padX = ((right - left + 1) * 0.04f).toInt()
        val padY = ((bottom - top + 1) * 0.04f).toInt()
        left = (left - padX).coerceAtLeast(0)
        top = (top - padY).coerceAtLeast(0)
        right = (right + padX).coerceAtMost(w - 1)
        bottom = (bottom + padY).coerceAtMost(h - 1)
        return Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
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
