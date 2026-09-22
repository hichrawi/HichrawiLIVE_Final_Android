package com.hichrawi.tv

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.videolan.libvlc.interfaces.IVLCVout
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class PlayerActivity : AppCompatActivity(), IVLCVout.Callback {
    private lateinit var videoSurface: SurfaceView
    private lateinit var logo: ImageView
    private lateinit var message: TextView
    private lateinit var root: FrameLayout

    private val prefs by lazy { getSharedPreferences("hichrawi", MODE_PRIVATE) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpClient by lazy { OkHttpClient() }

    private var libVlc: LibVLC? = null
    private var vlcPlayer: MediaPlayer? = null
    private var currentMedia: Media? = null
    private var guardJob: Job? = null
    private var packageJob: Job? = null
    private var presenceJob: Job? = null

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
        videoSurface = findViewById(R.id.videoSurface)
        logo = findViewById(R.id.channelLogo)
        message = findViewById(R.id.playerMessage)

        findViewById<View>(R.id.playerBack)?.setOnClickListener { finish() }
        findViewById<View>(R.id.playerExit)?.setOnClickListener { finish() }

        currentChannelId = intent.getLongExtra("channel_id", 0L)
        currentChannelName = intent.getStringExtra("channel_name").orEmpty()
        message.text = currentChannelName
        setChannelLogo(currentChannelName, intent.getStringExtra("logo_url"))
        configureDeviceControls()
        startPlayback(currentChannelId)
        loadCurrentPackageChannels(showOverlay = false)
    }

    private fun configureDeviceControls() {
        val back = findViewById<View>(R.id.playerBack)
        val exit = findViewById<View>(R.id.playerExit)
        val isTv = packageManager.hasSystemFeature("android.software.leanback")
        back?.visibility = if (isTv) View.GONE else View.VISIBLE
        exit?.visibility = if (isTv) View.GONE else View.VISIBLE
    }

    private fun sportLogoFor(name: String): Int {
        val normalized = name.lowercase().replace(" ", "").replace("-", "")
        val number = Regex("^hichrawisport(\\d+)$")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
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

        if (!url.isNullOrBlank()) {
            logo.visibility = View.VISIBLE
            loadWatermark(url)
        } else {
            logo.setImageDrawable(null)
            logo.visibility = View.GONE
        }
    }

    private fun startPlayback(channelId: Long) {
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

                val directUrl =
                    if (channelId == currentChannelId) intent.getStringExtra("direct_url") else null
                val url = if (!directUrl.isNullOrBlank() && channelId == currentChannelId) {
                    directUrl
                } else {
                    Api.playback(this@PlayerActivity, deviceId, channelId)
                }

                withContext(Dispatchers.Main) {
                    prepareVlc(url)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showPlaybackError(e.message ?: "تعذر تشغيل المحتوى")
                }
            }
        }
    }

    private fun prepareVlc(url: String) {
        currentStreamUrl = url
        releaseVlc()

        message.visibility = View.VISIBLE
        message.text = currentChannelName
        findViewById<View>(R.id.playerBack)?.visibility = View.GONE
        findViewById<View>(R.id.playerExit)?.visibility = View.GONE

        try {
            val options = arrayListOf(
                "--audio-time-stretch",
                "--network-caching=6000",
                "--http-reconnect",
                "--avcodec-skiploopfilter=1"
            )

            libVlc = LibVLC(this, options).also {
                it.setUserAgent("HICHRAWI LIVE", "HichrawiLiveVlc")
            }

            val vlc = MediaPlayer(libVlc).also { player ->
                player.setEventListener(object : MediaPlayer.EventListener {
                    override fun onEvent(event: MediaPlayer.Event) {
                        val mediaEvent = event
                        when (mediaEvent.type) {
                            MediaPlayer.Event.Playing,
                            MediaPlayer.Event.Vout -> {
                                runOnUiThread { hidePlaybackOverlays() }
                            }
                            MediaPlayer.Event.EndReached -> {
                                runOnUiThread {
                                    showPlaybackError("انتهى البث")
                                }
                            }
                            MediaPlayer.Event.EncounteredError -> {
                                runOnUiThread {
                                    if (currentStreamUrl == url) {
                                        showPlaybackError("تعذر تشغيل البث — جاري إيقاف المشغل بأمان")
                                        releaseVlc()
                                    } else {
                                        showPlaybackError("تعذر تشغيل البث")
                                    }
                                }
                            }
                        }
                    }
                })

                val vout = player.vlcVout
                vout.setVideoView(videoSurface)
                vout.addCallback(this)
                vout.attachViews()
            }
            vlcPlayer = vlc

            val media = Media(libVlc, Uri.parse(url)).also {
                // Stable software decoding path for X96Q ARMv7.
                it.setHWDecoderEnabled(false, false)
                it.addOption(":network-caching=6000")
                it.addOption(":http-reconnect=true")
            }
            currentMedia = media
            vlc.setMedia(media)
            vlc.play()
            startLicenseGuard()
            startPresenceGuard()
        } catch (e: Exception) {
            releaseVlc()
            showPlaybackError(e.message ?: "خطأ في إنشاء مشغل البث")
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
        val isTv = packageManager.hasSystemFeature("android.software.leanback")
        findViewById<View>(R.id.playerBack)?.visibility = if (isTv) View.GONE else View.VISIBLE
        findViewById<View>(R.id.playerExit)?.visibility = if (isTv) View.GONE else View.VISIBLE
    }

    private fun reportPresence() {
        val deviceKey = AppConfig.deviceKey(this)
        val packageName = intent.getStringExtra("package_name")
            ?.trim()
            .orEmpty()

        val channelId = currentChannelId
        val channelName = currentChannelName

        if (deviceKey.isBlank() || channelName.isBlank()) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Api.updatePresence(
                    this@PlayerActivity,
                    deviceKey,
                    channelId,
                    channelName,
                    packageName
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun startPresenceGuard() {
        presenceJob?.cancel()
        reportPresence()

        presenceJob = lifecycleScope.launch {
            while (true) {
                delay(30_000)
                reportPresence()
            }
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
                        releaseVlc()
                        showPlaybackError("الاشتراك لم يعد فعالاً")
                        break
                    }
                } catch (_: Exception) {
                    // Keep the current stream alive if a periodic license check is temporarily unavailable.
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_CHANNEL_UP -> {
                    if (packageOverlay?.visibility != View.VISIBLE && event.repeatCount == 0) {
                        switchAdjacentChannel(1)
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (packageOverlay?.visibility != View.VISIBLE && event.repeatCount == 0) {
                        switchAdjacentChannel(-1)
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    if (packageOverlay?.visibility == View.VISIBLE) {
                        return super.dispatchKeyEvent(event)
                    }
                    if (event.repeatCount == 0) {
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

    private fun togglePackageOverlay() {
        if (packageOverlay?.visibility == View.VISIBLE) {
            hidePackageOverlay()
        } else {
            loadCurrentPackageChannels()
        }
    }

    private fun loadCurrentPackageChannels(showOverlay: Boolean = true) {
        val deviceId = prefs.getLong(
            "firebase_device_id",
            prefs.getLong("server_device_id", 0L)
        )

        val packageId = intent.getLongExtra("package_id", -1L)
        val packageName = intent.getStringExtra("package_name")
            ?.trim()
            .orEmpty()

        packageJob?.cancel()
        packageJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val globalChannels = Api.channels(this@PlayerActivity, deviceId)
                val packages = Api.packages(this@PlayerActivity, deviceId, globalChannels)

                val selectedPackage =
                    packages.firstOrNull {
                        packageName.isNotBlank() &&
                            it.name.trim().equals(packageName, ignoreCase = true)
                    }
                        ?: packages.firstOrNull {
                            it.id == packageId
                        }

                val channels = if (selectedPackage != null) {
                    selectedPackage.channels
                        .map { pc ->
                            Api.Channel(
                                id = ("package_" + selectedPackage.id + "_" + pc.id)
                                    .hashCode()
                                    .toLong(),
                                name = pc.name,
                                logoUrl = pc.logoUrl,
                                sortOrder = pc.sortOrder,
                                streamUrl = pc.streamUrl
                            )
                        }
                        .sortedWith(
                            compareBy<Api.Channel> {
                                Regex("(?i)HichrawiSport(\\d+)")
                                    .find(it.name.replace(" ", ""))
                                    ?.groupValues
                                    ?.getOrNull(1)
                                    ?.toIntOrNull()
                                    ?: it.sortOrder
                            }
                            .thenBy { it.sortOrder }
                            .thenBy { it.id }
                        )
                } else {
                    globalChannels
                }

                packageChannels = channels

                withContext(Dispatchers.Main) {
                    if (packageChannels.isEmpty()) {
                        if (showOverlay) {
                            Toast.makeText(
                                this@PlayerActivity,
                                "لا توجد قنوات مفعلة",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else if (showOverlay) {
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

    private fun switchChannel(channel: Api.Channel) {
        currentChannelId = channel.id
        currentChannelName = channel.name

        intent.putExtra("direct_url", channel.streamUrl)

        setChannelLogo(channel.name, channel.logoUrl)
        message.text = channel.name
        reportPresence()
        hidePackageOverlay()
        startPlayback(channel.id)
    }

    private fun switchAdjacentChannel(direction: Int) {
        if (packageChannels.isEmpty()) {
            loadCurrentPackageChannels(showOverlay = false)
            return
        }

        if (packageChannels.size <= 1) return

        val currentIndex = packageChannels.indexOfFirst {
            it.id == currentChannelId
        }

        val startIndex = if (currentIndex >= 0) currentIndex else 0

        val nextIndex =
            (startIndex + direction + packageChannels.size) % packageChannels.size

        switchChannel(packageChannels[nextIndex])
    }

    private fun showPackageOverlay() {
        if (packageOverlay == null) {
            packageOverlay = buildPackageOverlay()
        }
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
        videoSurface.requestFocus()
    }

    /**
     * Universal package channel menu.
     *
     * The exact same menu is used for every package:
     * Hichrawi Sport, Sport World, or any future package.
     * Nothing here depends on the package name.
     */
    private fun buildPackageOverlay(): View {
        val packageName = intent.getStringExtra("package_name")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "LIVE"

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xF20B0912.toInt())
            setPadding(20, 18, 20, 18)
            isFocusable = true
            elevation = 20f
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        titleBox.addView(TextView(this).apply {
            text = packageName
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, 34))

        titleBox.addView(TextView(this).apply {
            text = "قنوات الباقة • ${packageChannels.size}"
            textSize = 13f
            setTextColor(0xFFC4B8D6.toInt())
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-1, 26))

        header.addView(
            titleBox,
            LinearLayout.LayoutParams(0, 62, 1f)
        )

        header.addView(TextView(this).apply {
            text = "OK"
            textSize = 15f
            setTextColor(0xFF160B20.toInt())
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFFFC400.toInt())
                cornerRadius = 12f
            }
            isFocusable = false
        }, LinearLayout.LayoutParams(70, 44))

        panel.addView(
            header,
            LinearLayout.LayoutParams(-1, 66)
        )

        val list = RecyclerView(this).apply {
            id = View.generateViewId()
            clipToPadding = false
            setPadding(4, 10, 4, 12)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isFocusable = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            layoutManager = LinearLayoutManager(this@PlayerActivity)
            adapter = PackageChannelAdapter(packageChannels)
        }

        packageListView = list

        panel.addView(
            list,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        panel.addView(TextView(this).apply {
            text = "OK : مشاهدة   •   ▲▼ : تنقل   •   BACK : إخفاء"
            textSize = 13f
            setTextColor(0xFFC4B8D6.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, 38))

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val panelWidth = if (screenWidth > screenHeight) {
            (screenWidth * 0.52f).toInt()
        } else {
            (screenWidth * 0.92f).toInt()
        }

        val lp = FrameLayout.LayoutParams(panelWidth, -1).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setMargins(18, 18, 18, 18)
        }

        root.addView(panel, lp)
        return panel
    }

    private inner class PackageChannelAdapter(
        private val items: List<Api.Channel>
    ) : RecyclerView.Adapter<PackageChannelAdapter.Holder>() {

        inner class Holder(val row: LinearLayout) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): Holder {

            val row = LinearLayout(this@PlayerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 7, 12, 7)

                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xB5221A31.toInt())
                    cornerRadius = 10f
                }

                isFocusable = true
                isClickable = true
                stateListAnimator = null
                minimumHeight = 64
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

            val number = TextView(this@PlayerActivity).apply {
                text = "${position + 1}"
                textSize = 17f
                setTextColor(0xFFFFC400.toInt())
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            }

            row.addView(
                number,
                LinearLayout.LayoutParams(42, 56)
            )

            val image = ImageView(this@PlayerActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = channel.name
            }

            row.addView(
                image,
                LinearLayout.LayoutParams(58, 56).apply {
                    setMargins(4, 0, 8, 0)
                }
            )

            val name = TextView(this@PlayerActivity).apply {
                text = channel.name
                textSize = 17f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                includeFontPadding = false
                maxLines = 2
                ellipsize = null
            }

            row.addView(
                name,
                LinearLayout.LayoutParams(0, 60, 1f)
            )

            row.addView(TextView(this@PlayerActivity).apply {
                text = "●"
                textSize = 12f
                setTextColor(0xFF65D99A.toInt())
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(30, 56))

            fun normalBackground() =
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(
                        if (channel.id == currentChannelId)
                            0xFF6A4C93.toInt()
                        else
                            0xB5221A31.toInt()
                    )
                    cornerRadius = 10f
                }

            fun focusedBackground() =
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF8B2FD0.toInt())
                    setStroke(2, 0xFFFFC400.toInt())
                    cornerRadius = 10f
                }

            row.background = normalBackground()

            row.setOnClickListener {
                switchChannel(channel)
            }

            row.setOnKeyListener { _, keyCode, keyEvent ->
                if (
                    keyEvent.action == KeyEvent.ACTION_DOWN &&
                    (
                        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER
                    )
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
                    view.background = focusedBackground()
                } else {
                    view.scaleX = 1f
                    view.scaleY = 1f
                    view.elevation = 2f
                    view.background = normalBackground()
                }
            }

            val fixed = sportLogoFor(channel.name)

            if (fixed != 0) {
                image.setImageResource(fixed)
            } else if (!channel.logoUrl.isNullOrBlank()) {
                loadSmallImage(channel.logoUrl, image)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun loadSmallImage(url: String, image: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bytes = response.body?.bytes() ?: return@use
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                    withContext(Dispatchers.Main) { image.setImageBitmap(bitmap) }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadWatermark(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bytes = response.body?.bytes() ?: return@use
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                    withContext(Dispatchers.Main) {
                        logo.setImageBitmap(bitmap)
                        logo.visibility = View.VISIBLE
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun releaseVlc() {
        mainHandler.removeCallbacksAndMessages(null)
        guardJob?.cancel()
        guardJob = null

        try {
            vlcPlayer?.stop()
        } catch (_: Exception) {
        }

        try {
            vlcPlayer?.vlcVout?.removeCallback(this)
            vlcPlayer?.vlcVout?.detachViews()
        } catch (_: Exception) {
        }

        try {
            currentMedia?.release()
        } catch (_: Exception) {
        }
        currentMedia = null

        try {
            vlcPlayer?.release()
        } catch (_: Exception) {
        }
        vlcPlayer = null

        try {
            libVlc?.release()
        } catch (_: Exception) {
        }
        libVlc = null
    }

    override fun onSurfacesCreated(vout: IVLCVout) = Unit

    override fun onSurfacesDestroyed(vout: IVLCVout) = Unit

    override fun onStart() {
        super.onStart()
        if (currentStreamUrl != null && vlcPlayer == null) {
            prepareVlc(currentStreamUrl!!)
        }
    }

    override fun onStop() {
        packageJob?.cancel()
        packageJob = null
        presenceJob?.cancel()
        presenceJob = null
        releaseVlc()
        super.onStop()
    }
}
