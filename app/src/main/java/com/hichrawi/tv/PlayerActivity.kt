package com.hichrawi.tv

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class PlayerActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var logo: ImageView
    private lateinit var message: TextView
    private lateinit var root: FrameLayout

    private val prefs by lazy { getSharedPreferences("hichrawi", MODE_PRIVATE) }
    private val isTv by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    private var player: ExoPlayer? = null
    private var guardJob: Job? = null
    private var packageJob: Job? = null
    private var currentChannelId = 0L
    private var currentChannelName = ""
    private var currentStreamUrl: String? = null
    private var packageOverlay: View? = null
    private var packageListView: RecyclerView? = null
    private var packageChannels: List<Api.Channel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        setContentView(R.layout.activity_player)

        root = findViewById(R.id.playerRoot)
        playerView = findViewById(R.id.playerView)
        logo = findViewById(R.id.channelLogo)
        message = findViewById(R.id.playerMessage)

        findViewById<View>(R.id.playerBack).setOnClickListener { finish() }
        findViewById<View>(R.id.playerExit).setOnClickListener { finish() }

        currentChannelId = intent.getLongExtra("channel_id", 0L)
        currentChannelName = intent.getStringExtra("channel_name").orEmpty()
        setChannelLogo(currentChannelName, intent.getStringExtra("logo_url"))

        configureControls()
        startPlayback(currentChannelId, intent.getStringExtra("direct_url"))
    }

    private fun configureControls() {
        val back = findViewById<View>(R.id.playerBack)
        val exit = findViewById<View>(R.id.playerExit)
        back.visibility = if (isTv) View.GONE else View.VISIBLE
        exit.visibility = if (isTv) View.GONE else View.VISIBLE
    }

    private fun sportLogoFor(name: String): Int {
        val normalized = name.lowercase().replace(" ", "").replace("-", "")
        val number = Regex("^hichrawisport(\\d+)$")
            .find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return 0

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
        val fixed = sportLogoFor(name)
        if (fixed != 0) {
            logo.setImageResource(fixed)
            logo.visibility = View.VISIBLE
            return
        }

        logo.setImageResource(R.drawable.hichrawi_live_logo)
        logo.visibility = View.VISIBLE
        if (!url.isNullOrBlank()) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            logo.setImageBitmap(bitmap)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun startPlayback(channelId: Long, preferredUrl: String?) {
        val deviceId = prefs.getLong(
            "firebase_device_id",
            prefs.getLong("server_device_id", 0L)
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val state = Api.license(this@PlayerActivity, deviceId)
                if (state.optJSONObject("subscription")?.optBoolean("active") != true) {
                    throw IllegalStateException("الاشتراك غير فعال")
                }

                val url = if (!preferredUrl.isNullOrBlank() && channelId == currentChannelId) {
                    preferredUrl
                } else {
                    Api.playback(this@PlayerActivity, deviceId, channelId)
                }

                withContext(Dispatchers.Main) {
                    preparePlayer(url)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showPlaybackError(e.message ?: "تعذر تشغيل المحتوى")
                }
            }
        }
    }

    private fun preparePlayer(url: String) {
        currentStreamUrl = url
        guardJob?.cancel()
        player?.release()
        player = null

        message.visibility = View.VISIBLE
        message.text = currentChannelName
        if (!isTv) {
            findViewById<View>(R.id.playerBack).visibility = View.VISIBLE
            findViewById<View>(R.id.playerExit).visibility = View.VISIBLE
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setUserAgent("HICHRAWI LIVE AndroidTV")

        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(1920, 1080)
            )
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                45_000,
                1_500,
                3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val uri = Uri.parse(url)
        val clean = url.substringBefore("?").lowercase()
        val mediaBuilder = MediaItem.Builder().setUri(uri)

        if (clean.endsWith(".m3u8") || clean.contains("/m3u8")) {
            mediaBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(httpFactory)
            )
            .build()
            .also { exo ->
                playerView.player = exo

                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            hidePlaybackOverlays()
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            hidePlaybackOverlays()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        showPlaybackError(
                            "تعذر تشغيل البث\n${error.errorCodeName}"
                        )
                    }
                })

                exo.setMediaItem(mediaBuilder.build())
                exo.prepare()
                exo.playWhenReady = true
            }

        startLicenseGuard()
    }

    private fun hidePlaybackOverlays() {
        message.visibility = View.GONE
        if (!isTv) {
            findViewById<View>(R.id.playerBack).visibility = View.GONE
            findViewById<View>(R.id.playerExit).visibility = View.GONE
        }
    }

    private fun showPlaybackError(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
        if (!isTv) {
            findViewById<View>(R.id.playerBack).visibility = View.VISIBLE
            findViewById<View>(R.id.playerExit).visibility = View.VISIBLE
        }
    }

    private fun startLicenseGuard() {
        val deviceId = prefs.getLong(
            "firebase_device_id",
            prefs.getLong("server_device_id", 0L)
        )

        guardJob?.cancel()
        guardJob = lifecycleScope.launch {
            while (true) {
                delay(60_000)
                try {
                    val state = withContext(Dispatchers.IO) {
                        Api.license(this@PlayerActivity, deviceId)
                    }
                    if (state.optJSONObject("subscription")?.optBoolean("active") != true) {
                        player?.stop()
                        showPlaybackError("الاشتراك لم يعد فعالاً")
                        break
                    }
                } catch (_: Exception) {
                    // Keep the current stream alive during temporary Firebase/network errors.
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    if (packageOverlay?.visibility == View.VISIBLE) {
                        return super.dispatchKeyEvent(event)
                    }
                    if (event.repeatCount == 0) {
                        loadCurrentPackageChannels()
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

    private fun loadCurrentPackageChannels() {
        val deviceId = prefs.getLong(
            "firebase_device_id",
            prefs.getLong("server_device_id", 0L)
        )

        packageJob?.cancel()
        packageJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                packageChannels = Api.channels(this@PlayerActivity, deviceId)

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
        if (packageOverlay == null) {
            packageOverlay = buildPackageOverlay()
        }
        packageOverlay?.visibility = View.VISIBLE
        packageOverlay?.bringToFront()

        val index = packageChannels.indexOfFirst { it.id == currentChannelId }
            .coerceAtLeast(0)

        packageListView?.scrollToPosition(index)
        packageListView?.post {
            packageListView
                ?.findViewHolderForAdapterPosition(index)
                ?.itemView
                ?.requestFocus()
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
            setBackgroundColor(0xE6111118.toInt())
            setPadding(18, 18, 18, 18)
            isFocusable = true
            elevation = 18f
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(TextView(this).apply {
            text = "القنوات الرياضية (${packageChannels.size})"
            textSize = 21f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, 56, 1f))

        header.addView(TextView(this).apply {
            text = "OK"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF6A4C93.toInt())
        }, LinearLayout.LayoutParams(72, 44))

        panel.addView(header)

        val list = RecyclerView(this).apply {
            clipToPadding = false
            setPadding(4, 8, 4, 12)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isFocusable = true
            layoutManager = LinearLayoutManager(this@PlayerActivity)
            adapter = PackageChannelAdapter(packageChannels)
        }

        packageListView = list
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        panel.addView(TextView(this).apply {
            text = "OK: اختيار   •   ▲▼: تنقل   •   BACK: إخفاء"
            textSize = 13f
            setTextColor(0xFFB8C0CC.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, 36))

        val lp = FrameLayout.LayoutParams(560, -1).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setMargins(18, 18, 0, 18)
        }

        root.addView(panel, lp)
        return panel
    }

    private inner class PackageChannelAdapter(
        private val items: List<Api.Channel>
    ) : RecyclerView.Adapter<PackageChannelAdapter.Holder>() {

        inner class Holder(val row: LinearLayout) :
            RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): Holder {
            val row = LinearLayout(this@PlayerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 5, 12, 5)
                isFocusable = true
                isClickable = true
                setBackgroundColor(0xB322222B.toInt())
            }
            return Holder(row)
        }

        override fun onBindViewHolder(
            holder: Holder,
            position: Int
        ) {
            val channel = items[position]
            val row = holder.row
            row.removeAllViews()

            row.addView(TextView(this@PlayerActivity).apply {
                text = "${position + 1}"
                textSize = 17f
                setTextColor(0xFFB8C0CC.toInt())
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }, LinearLayout.LayoutParams(48, -1))

            val image = ImageView(this@PlayerActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = channel.name
            }

            row.addView(image, LinearLayout.LayoutParams(58, 54))

            row.addView(TextView(this@PlayerActivity).apply {
                text = channel.name
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -1, 1f))

            setChannelLogoInto(image, channel)

            if (channel.id == currentChannelId) {
                row.setBackgroundColor(0xFF6A4C93.toInt())
            }

            row.setOnClickListener {
                switchChannel(channel)
            }

            row.setOnKeyListener { _, keyCode, keyEvent ->
                if (keyEvent.action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    switchChannel(channel)
                    true
                } else {
                    false
                }
            }

            row.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.scaleX = 1.015f
                    view.scaleY = 1.015f
                    view.elevation = 12f
                    view.setBackgroundColor(0xFF8B2FD0.toInt())
                } else {
                    view.scaleX = 1f
                    view.scaleY = 1f
                    view.elevation = 2f
                    view.setBackgroundColor(
                        if (channel.id == currentChannelId)
                            0xFF6A4C93.toInt()
                        else
                            0xB322222B.toInt()
                    )
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun setChannelLogoInto(image: ImageView, channel: Api.Channel) {
        val fixed = sportLogoFor(channel.name)
        if (fixed != 0) {
            image.setImageResource(fixed)
            return
        }

        image.setImageResource(R.drawable.hichrawi_live_logo)
        val url = channel.logoUrl ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.instanceFollowRedirects = true
                connection.inputStream.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            image.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun switchChannel(channel: Api.Channel) {
        currentChannelId = channel.id
        currentChannelName = channel.name
        currentStreamUrl = channel.streamUrl

        setChannelLogo(channel.name, channel.logoUrl)
        message.text = channel.name

        hidePackageOverlay()

        startPlayback(channel.id, channel.streamUrl)
    }

    override fun onDestroy() {
        guardJob?.cancel()
        packageJob?.cancel()
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
