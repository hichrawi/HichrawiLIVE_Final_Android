package com.hichrawi.tv
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var logo: ImageView
    private lateinit var message: TextView
    private lateinit var root: FrameLayout
    private val prefs by lazy { getSharedPreferences("hichrawi", MODE_PRIVATE) }
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

        currentChannelId = intent.getLongExtra("channel_id", 0L)
        currentChannelName = intent.getStringExtra("channel_name").orEmpty()
        message.text = currentChannelName
        setChannelLogo(currentChannelName, intent.getStringExtra("logo_url"))
        startPlayback(currentChannelId)
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
    }

    private fun showPlaybackError(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
        findViewById<View>(R.id.playerBack)?.visibility = View.VISIBLE
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
        val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
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
        }

        message.visibility = View.VISIBLE
        message.text = currentChannelName
        findViewById<View>(R.id.playerBack)?.visibility = View.VISIBLE

        player = ExoPlayer.Builder(this)
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
        if (packageChannels.isNotEmpty()) {
            showPackageOverlay()
            return
        }
        val deviceId = prefs.getLong("firebase_device_id", prefs.getLong("server_device_id", 0L))
        packageJob?.cancel()
        packageJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val channels = Api.channels(this@PlayerActivity, deviceId)
                val packages = Api.packages(this@PlayerActivity, deviceId, channels)
                val pkg = packages.firstOrNull { currentChannelId in it.channelIds }
                    ?: packages.firstOrNull { p ->
                        p.name.contains("sport", true) && currentChannelName.contains("sport", true)
                    }
                val selected = pkg?.channelIds?.toSet().orEmpty()
                val filtered = if (selected.isNotEmpty()) channels.filter { it.id in selected }
                else channels.filter { it.name.contains("sport", true) || it.name.contains("سبورت", true) || it.name.contains("رياض", true) }
                packageChannels = filtered
                packageName = pkg?.name ?: if (filtered.isNotEmpty()) "الباقة الرياضية" else "قنوات الباقة"
                withContext(Dispatchers.Main) {
                    if (packageChannels.isEmpty()) {
                        Toast.makeText(this@PlayerActivity, "لا توجد قنوات أخرى في الباقة", Toast.LENGTH_SHORT).show()
                    } else showPackageOverlay()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlayerActivity, e.message ?: "تعذر تحميل القنوات", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showPackageOverlay() {
        if (packageOverlay == null) packageOverlay = buildPackageOverlay()
        packageOverlay?.visibility = View.VISIBLE
        packageOverlay?.bringToFront()
        packageListView?.requestFocus()
    }

    private fun hidePackageOverlay() {
        packageOverlay?.visibility = View.GONE
        playerView.requestFocus()
    }

    private fun buildPackageOverlay(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xF20A0D13.toInt())
            setPadding(28, 20, 28, 22)
            isFocusable = true
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = packageName
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
            setPadding(4, 10, 4, 16)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isFocusable = true
            layoutManager = GridLayoutManager(this@PlayerActivity, packageColumns())
            adapter = PackageChannelAdapter(packageChannels)
        }
        packageListView = list
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        val hint = TextView(this).apply {
            text = "OK: اختيار القناة   •   BACK: إخفاء القائمة   •   EXIT: الخروج"
            textSize = 13f
            setTextColor(0xFFB8C0CC.toInt())
            gravity = Gravity.CENTER
        }
        panel.addView(hint, LinearLayout.LayoutParams(-1, 34))

        val lp = FrameLayout.LayoutParams(-1, -1).apply {
            gravity = Gravity.CENTER
            setMargins(70, 55, 70, 45)
        }
        root.addView(panel, lp)
        return panel
    }

    private fun packageColumns(): Int {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return when {
            widthDp >= 1100 -> 5
            widthDp >= 700 -> 4
            else -> 2
        }
    }

    private inner class PackageChannelAdapter(private val items: List<Api.Channel>) :
        RecyclerView.Adapter<PackageChannelAdapter.Holder>() {
        inner class Holder(val card: LinearLayout) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val card = LinearLayout(this@PlayerActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = getDrawable(R.drawable.bg_card)
                setPadding(12, 10, 12, 10)
                isFocusable = true
                isClickable = true
                elevation = 3f
            }
            return Holder(card)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val ch = items[position]
            val card = holder.card
            card.removeAllViews()
            val image = ImageView(this@PlayerActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = ch.name
            }
            card.addView(image, LinearLayout.LayoutParams(-1, 82))
            card.addView(TextView(this@PlayerActivity).apply {
                text = ch.name
                textSize = 15f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 2
            }, LinearLayout.LayoutParams(-1, 42))
            card.setOnClickListener { switchChannel(ch) }
            card.setOnFocusChangeListener { v, hasFocus ->
                v.scaleX = if (hasFocus) 1.06f else 1f
                v.scaleY = if (hasFocus) 1.06f else 1f
                v.elevation = if (hasFocus) 12f else 3f
            }
            val fixed = sportLogoFor(ch.name)
            if (fixed != 0) image.setImageResource(fixed) else ch.logoUrl?.let { loadSmallImage(it, image) }
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
