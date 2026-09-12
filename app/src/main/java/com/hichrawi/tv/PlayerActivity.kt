package com.hichrawi.tv
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var logo: ImageView
    private lateinit var message: TextView
    private lateinit var root: FrameLayout
    private val prefs by lazy { getSharedPreferences("hichrawi", MODE_PRIVATE) }
    private val isTvDevice by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    private var player: ExoPlayer? = null
    private var guardJob: Job? = null
    private var packageJob: Job? = null
    private var currentChannelId = 0L
    private var currentChannelName = ""
    private var packageOverlay: View? = null
    private var packageListView: RecyclerView? = null
    private var packageChannels: List<Api.Channel> = emptyList()
    private var packageName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        setContentView(R.layout.activity_player)

        root = findViewById(R.id.playerRoot)
        playerView = findViewById(R.id.playerView)
        logo = findViewById(R.id.channelLogo)
        message = findViewById(R.id.playerMessage)
        findViewById<View>(R.id.playerBack)?.setOnClickListener { finish() }
        findViewById<View>(R.id.playerExit)?.setOnClickListener { finish() }
        configureDeviceControls()

        currentChannelId = intent.getLongExtra("channel_id", 0L)
        currentChannelName = intent.getStringExtra("channel_name").orEmpty()
        message.text = currentChannelName
        setChannelLogo(currentChannelName, intent.getStringExtra("logo_url"))
        startPlayback(currentChannelId)
    }

    private fun configureDeviceControls() {
        val back = findViewById<View>(R.id.playerBack)
        val exit = findViewById<View>(R.id.playerExit)

        if (isTvDevice) {
            back?.visibility = View.GONE
            exit?.visibility = View.GONE
        } else {
            back?.visibility = View.VISIBLE
            exit?.visibility = View.VISIBLE
        }
    }

    private fun sportLogoFor(name: String): Int {
        val n = name.lowercase().replace(" ", "").replace("-", "")
        val match = Regex("^hichrawisport(\\d+)$").find(n)
        val number = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return 0

        return when (number) {
            1 -> R.drawable.hichrawi_sport_1
            2 -> R.drawable.hichrawi_sport_2
            3 -> R.drawable.hichrawi_sport_3
            4 -> R.drawable.hichrawi_sport_4
            5 -> R.drawable.hichrawi_sport_5
            6 -> R.drawable.hichrawi_sport_6
            7 -> R.drawable.hichrawi_sport_7
            8 -> R.drawable.hichrawi_sport_8
            else -> 0
        }
    }

    private fun setChannelLogo(name: String, url: String?) {
        val sportLogo = sportLogoFor(name)
        if (sportLogo != 0) {
            logo.setImageResource(sportLogo)
            logo.visibility = View.VISIBLE
        } else {
            logo.setImageResource(R.drawable.hichrawi_live_logo)
            logo.visibility = View.VISIBLE
            if (!url.isNullOrBlank()) loadWatermark(url)
        }
    }

    private fun hidePlaybackOverlays() {
        message.visibility = View.GONE
        findViewById<View>(R.id.playerBack)?.visibility = View.GONE
        findViewById<View>(R.id.playerExit)?.visibility = View.GONE
    }

    private fun showPlaybackError(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
        findViewById<View>(R.id.playerBack)?.visibility = View.VISIBLE
        findViewById<View>(R.id.playerExit)?.visibility =
            if (isTvDevice) View.GONE else View.VISIBLE
    }

    private fun startPlayback(channelId: Long) {
        val deviceId = prefs.getLong("firebase_device_id", prefs.getLong("server_device_id", 0L))
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val state = Api.license(this@PlayerActivity, deviceId)
                if (state.optJSONObject("subscription")?.optBoolean("active") != true)
                    throw Exception("الاشتراك غير فعال")
                val directUrl = if (channelId == currentChannelId) intent.getStringExtra("direct_url") else null
                val url = if (!directUrl.isNullOrBlank() && channelId == currentChannelId) directUrl
                else Api.playback(this@PlayerActivity, deviceId, channelId)
                withContext(Dispatchers.Main) { prepare(url) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showPlaybackError(e.message ?: "تعذر تشغيل المحتوى")
                }
            }
        }
    }

    private fun prepare(url: String) {
        guardJob?.cancel()
        player?.release()
        player = null
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("VLC/3.0.21 LibVLC/3.0.21")
            .setDefaultRequestProperties(mapOf("Accept" to "*/*"))
        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                    DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
            )
        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory, extractorsFactory)
        val clean = url.substringBefore('?').lowercase()
        val builder = MediaItem.Builder().setUri(Uri.parse(url))
        when {
            clean.endsWith(".m3u8") || clean.contains("/m3u8") ->
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            clean.endsWith(".mp4") ->
                builder.setMimeType(MimeTypes.VIDEO_MP4)
            clean.endsWith(".mp3") ->
                builder.setMimeType(MimeTypes.AUDIO_MPEG)
            clean.endsWith(".aac") ->
                builder.setMimeType(MimeTypes.AUDIO_AAC)
            else ->
                builder.setMimeType(MimeTypes.VIDEO_MP2T)
        }

        message.visibility = View.VISIBLE
        message.text = currentChannelName
        findViewById<View>(R.id.playerBack)?.visibility =
            if (isTvDevice) View.GONE else View.VISIBLE
        findViewById<View>(R.id.playerExit)?.visibility =
            if (isTvDevice) View.GONE else View.VISIBLE

        val renderersFactory = DefaultRenderersFactory(this)
            .forceEnableMediaCodecAsynchronousQueueing()
            .setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                MediaCodecSelector.DEFAULT
                    .getDecoderInfos(
                        mimeType,
                        requiresSecureDecoder,
                        requiresTunnelingDecoder
                    )
                    .sortedBy { it.softwareOnly }
            }
            .setEnableDecoderFallback(true)

        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(1920, 1080)
                    .setMaxVideoBitrate(8_000_000)
            )
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                8_000,
                30_000,
                1_500,
                3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { p ->
                playerView.player = p
                p.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) hidePlaybackOverlays()
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) hidePlaybackOverlays()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val cause = error.cause
                        val details = buildString {
                            append("ExoPlayer: ")
                            append(error.errorCodeName)
                            append("\n")
                            append(error.message ?: "بدون رسالة")
                            if (cause != null) {
                                append("\n")
                                append(cause.javaClass.simpleName)
                                append(": ")
                                append(cause.message ?: "")
                            }
                        }
                        runOnUiThread {
                            showPlaybackError(details)
                        }
                    }
                })
                p.setMediaItem(builder.build())
                p.prepare()
                p.playWhenReady = true
            }
        startLicenseGuard()
    }

    private fun startLicenseGuard() {
        val deviceId = prefs.getLong("firebase_device_id", prefs.getLong("server_device_id", 0L))
        guardJob?.cancel()
        guardJob = lifecycleScope.launch {
            while (true) {
                delay(60_000)
                try {
                    val state = withContext(Dispatchers.IO) { Api.license(this@PlayerActivity, deviceId) }
                    if (state.optJSONObject("subscription")?.optBoolean("active") != true) {
                        player?.stop()
                        showPlaybackError("الاشتراك لم يعد فعالاً")
                        break
                    }
                } catch (_: Exception) { }
            }
        }
    }

    /** Press OK/Enter on the TV remote to open the channels of the current package. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    // When the package list is open, let the focused card/button receive OK.
                    if (packageOverlay?.visibility == View.VISIBLE) return super.dispatchKeyEvent(event)
                    if (!isRepeatedKey(event)) {
                        togglePackageOverlay()
                        return true
                    }
                }
                KeyEvent.KEYCODE_BACK -> {
                    if (packageOverlay?.visibility == View.VISIBLE) {
                        hidePackageOverlay()
                        return true
                    }
                }
                KeyEvent.KEYCODE_ESCAPE -> {
                    finish()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isRepeatedKey(event: KeyEvent): Boolean = event.repeatCount > 0

    private fun togglePackageOverlay() {
        if (packageOverlay?.visibility == View.VISIBLE) {
            hidePackageOverlay()
        } else {
            loadCurrentPackageChannels()
        }
    }

    private fun loadCurrentPackageChannels() {

        val deviceId = prefs.getLong(
            "firebase_device_id",
            prefs.getLong("server_device_id", 0L)
        )

        packageJob?.cancel()
        packageJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Load ALL enabled channels from Firebase/admin.
                // No package channelIds filtering and no fixed channel limit.
                val channels = Api.channels(this@PlayerActivity, deviceId)

                packageChannels = channels
                packageName = "القنوات"

                withContext(Dispatchers.Main) {
                    if (packageChannels.isEmpty()) {
                        Toast.makeText(
                            this@PlayerActivity,
                            "لا توجد قنوات مفعلة",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        showPackageOverlay()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@PlayerActivity,
                        e.message ?: "تعذر تحميل القنوات",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showPackageOverlay() {
        if (packageOverlay == null) packageOverlay = buildPackageOverlay()
        packageOverlay?.visibility = View.VISIBLE
        packageOverlay?.bringToFront()

        val index = packageChannels.indexOfFirst { it.id == currentChannelId }.coerceAtLeast(0)
        packageListView?.scrollToPosition(index)
        packageListView?.post {
            packageListView?.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()
        }
    }

    private fun hidePackageOverlay() {
        packageOverlay?.visibility = View.GONE
        playerView.requestFocus()
    }

    private fun buildPackageOverlay(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = getDrawable(R.drawable.bg_card)
            setPadding(18, 18, 18, 18)
            isFocusable = true
            elevation = 18f
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        top.addView(TextView(this).apply {
            text = "القنوات الرياضية (${packageChannels.size})"
            textSize = 21f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, 52, 1f))

        top.addView(Button(this).apply {
            text = "EXIT"
            isAllCaps = false
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            background = getDrawable(R.drawable.bg_button)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(110, 50))

        panel.addView(top)

        val list = RecyclerView(this).apply {
            id = View.generateViewId()
            setHasFixedSize(false)
            clipToPadding = false
            setPadding(4, 8, 4, 12)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isFocusable = true
            layoutManager = LinearLayoutManager(this@PlayerActivity, LinearLayoutManager.VERTICAL, false)
            adapter = PackageChannelAdapter(packageChannels)
        }

        packageListView = list
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        val hint = TextView(this).apply {
            text = "OK: اختيار   •   ▲▼: تنقل   •   BACK: إخفاء"
            textSize = 13f
            setTextColor(0xFFB8C0CC.toInt())
            gravity = Gravity.CENTER
        }

        panel.addView(hint, LinearLayout.LayoutParams(-1, 34))

        val lp = FrameLayout.LayoutParams(560, -1).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setMargins(28, 28, 0, 28)
        }

        root.addView(panel, lp)
        return panel
    }

    private inner class PackageChannelAdapter(private val items: List<Api.Channel>) :
        RecyclerView.Adapter<PackageChannelAdapter.Holder>() {

        inner class Holder(val row: LinearLayout) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(this@PlayerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 5, 12, 5)
                isFocusable = true
                isClickable = true
                background = getDrawable(R.drawable.bg_card)
                elevation = 2f
            }

            return Holder(row)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val ch = items[position]
            val row = holder.row

            row.removeAllViews()

            val number = TextView(this@PlayerActivity).apply {
                text = "${position + 1}"
                textSize = 17f
                setTextColor(0xFFB8C0CC.toInt())
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            row.addView(
                number,
                LinearLayout.LayoutParams(48, -1)
            )

            val image = ImageView(this@PlayerActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = ch.name
            }

            row.addView(
                image,
                LinearLayout.LayoutParams(58, 54)
            )

            val name = TextView(this@PlayerActivity).apply {
                text = ch.name
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            row.addView(
                name,
                LinearLayout.LayoutParams(0, -1, 1f)
            )

            if (ch.id == currentChannelId) {
                row.setBackgroundColor(0xFF6A4C93.toInt())
            }

            row.setOnClickListener {
                switchChannel(ch)
            }

            row.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyCode == KeyEvent.KEYCODE_ENTER)) {
                    switchChannel(ch)
                    true
                } else {
                    false
                }
            }

            row.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.scaleX = 1.015f
                    v.scaleY = 1.015f
                    v.elevation = 12f
                    v.setBackgroundColor(0xFF8B2FD0.toInt())
                } else {
                    v.scaleX = 1f
                    v.scaleY = 1f
                    v.elevation = 2f
                    if (ch.id == currentChannelId) {
                        v.setBackgroundColor(0xFF6A4C93.toInt())
                    } else {
                        v.background = getDrawable(R.drawable.bg_card)
                    }
                }
            }

            val fixed = sportLogoFor(ch.name)
            if (fixed != 0) {
                image.setImageResource(fixed)
            } else {
                ch.logoUrl?.let {
                    loadSmallImage(it, image)
                }
            }
        }

        override fun getItemCount() = items.size
    }

    private fun switchChannel(ch: Api.Channel) {
        currentChannelId = ch.id
        currentChannelName = ch.name
        setChannelLogo(ch.name, ch.logoUrl)
        message.text = ch.name
        hidePackageOverlay()
        startPlayback(ch.id)
    }

    private fun loadSmallImage(url: String, image: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val req = okhttp3.Request.Builder().url(url).build()
                okhttp3.OkHttpClient().newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@use
                    val bytes = r.body?.bytes() ?: return@use
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                    withContext(Dispatchers.Main) { image.setImageBitmap(bmp) }
                }
            } catch (_: Exception) { }
        }
    }

    private fun loadWatermark(url: String) {
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
        packageJob?.cancel()
        guardJob?.cancel()
        player?.release()
        player = null
        super.onStop()
    }
}
