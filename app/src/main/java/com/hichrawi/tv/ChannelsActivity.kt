package com.hichrawi.tv

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("hichrawi", MODE_PRIVATE) }
    private lateinit var list: LinearLayout
    private lateinit var info: TextView
    private var deviceId = 0L
    private var licenseJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF050507.toInt()
        window.navigationBarColor = 0xFF050507.toInt()
        deviceId = prefs.getLong("server_device_id", 0L)
        buildUi()
        loadChannels()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 18, 24, 18)
            setBackgroundColor(0xFF050507.toInt())
        }
        root.addView(TextView(this).apply {
            text = "HICHRAWI LIVE"
            textSize = 27f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, 60))
        info = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFBBBBBB.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(info)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 15, 8, 30) }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "تحديث القنوات"
            isAllCaps = false
            setOnClickListener { loadChannels() }
        }, LinearLayout.LayoutParams(-1, 58))
        setContentView(root)
    }

    private fun loadChannels() {
        info.text = "جاري تحميل القنوات..."
        list.removeAllViews()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val state = Api.license(this@ChannelsActivity, deviceId)
                if (state.optJSONObject("subscription")?.optBoolean("active") != true) {
                    throw Exception("الاشتراك غير فعال أو منتهي")
                }
                val channels = Api.channels(this@ChannelsActivity, deviceId)
                withContext(Dispatchers.Main) {
                    info.text = if (channels.isEmpty()) "لا توجد قنوات متاحة حالياً" else "${channels.size} قناة متاحة"
                    channels.forEach(::addChannel)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { info.text = e.message ?: "تعذر تحميل القنوات" }
            }
        }
    }

    private fun addChannel(ch: Api.Channel) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 10, 18, 10)
            setBackgroundColor(0xFF151519.toInt())
            isFocusable = true
            isClickable = true
        }
        val logo = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        card.addView(logo, LinearLayout.LayoutParams(90, 70))
        val name = TextView(this).apply {
            text = ch.name
            textSize = 19f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 0, 18, 0)
        }
        card.addView(name, LinearLayout.LayoutParams(0, 70, 1f))
        card.addView(Button(this).apply {
            text = "مشاهدة"
            isAllCaps = false
            setOnClickListener { play(ch) }
        }, LinearLayout.LayoutParams(120, 60))
        ch.logoUrl?.let { loadImage(it, logo) }
        list.addView(card, LinearLayout.LayoutParams(-1, 82).apply { setMargins(0, 0, 0, 12) })
    }

    private fun loadImage(url: String, image: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder().url(url).build()
                okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bytes = response.body?.bytes() ?: return@use
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                    withContext(Dispatchers.Main) { image.setImageBitmap(bmp) }
                }
            } catch (_: Exception) { }
        }
    }

    private fun play(ch: Api.Channel) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("channel_id", ch.id)
            putExtra("channel_name", ch.name)
            putExtra("logo_url", ch.logoUrl)
        })
    }

    override fun onStart() {
        super.onStart()
        licenseJob = lifecycleScope.launch {
            while (true) {
                delay(60_000)
                try {
                    val state = withContext(Dispatchers.IO) { Api.license(this@ChannelsActivity, deviceId) }
                    if (state.optJSONObject("subscription")?.optBoolean("active") != true) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ChannelsActivity, "الاشتراك لم يعد فعالاً", Toast.LENGTH_LONG).show()
                            startActivity(Intent(this@ChannelsActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                            finish()
                        }
                        break
                    }
                } catch (_: Exception) { }
            }
        }
    }

    override fun onStop() {
        licenseJob?.cancel()
        licenseJob = null
        super.onStop()
    }
}
