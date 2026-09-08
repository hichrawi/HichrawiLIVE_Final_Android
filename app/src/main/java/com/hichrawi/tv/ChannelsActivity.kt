package com.hichrawi.tv

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class ChannelsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("hichrawi", MODE_PRIVATE) }
    private lateinit var list: RecyclerView
    private lateinit var info: TextView
    private lateinit var tabChannels: Button
    private lateinit var tabPackages: Button
    private var deviceId = 0L
    private var licenseJob: Job? = null
    private var allChannels: List<Api.Channel> = emptyList()
    private var packages: List<Api.Package> = emptyList()
    private var showingPackageChannels = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF07090D.toInt()
        window.navigationBarColor = 0xFF07090D.toInt()
        deviceId = prefs.getLong("server_device_id", 0L)
        buildUi()
        loadData()
    }

    private fun tv(s: String, size: Float, color: Int = 0xFFFFFFFF.toInt(), bold: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF07090D.toInt())
            setPadding(18, 14, 18, 14)
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.hichrawi_live_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        head.addView(logo, LinearLayout.LayoutParams(62, 62))
        head.addView(
            tv("HICHRAWI LIVE", 22f, 0xFFFFFFFF.toInt(), true),
            LinearLayout.LayoutParams(0, 62, 1f)
        )

        val subscription = Button(this).apply {
            text = "الاشتراك"
            textSize = 13f
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            background = getDrawable(R.drawable.bg_tab)
            setOnClickListener { showSubscription() }
        }
        head.addView(subscription, LinearLayout.LayoutParams(105, 58).apply { setMargins(4, 0, 4, 0) })

        val refresh = Button(this).apply {
            text = "↻"
            textSize = 22f
            isAllCaps = false
            setOnClickListener { loadData() }
        }
        head.addView(refresh, LinearLayout.LayoutParams(58, 58))
        root.addView(head)

        root.addView(tv("شاهد قنواتك مباشرة", 14f, 0xFFAEB6C5.toInt()), LinearLayout.LayoutParams(-1, 36))

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }
        tabChannels = Button(this).apply {
            text = "القنوات"
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            background = getDrawable(R.drawable.bg_tab)
            isSelected = true
            setOnClickListener { selectTab(true) }
        }
        tabPackages = Button(this).apply {
            text = "الباقات"
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            background = getDrawable(R.drawable.bg_tab)
            setOnClickListener { selectTab(false) }
        }
        tabs.addView(tabChannels, LinearLayout.LayoutParams(0, 56, 1f).apply { setMargins(0, 0, 6, 0) })
        tabs.addView(tabPackages, LinearLayout.LayoutParams(0, 56, 1f).apply { setMargins(6, 0, 0, 0) })
        root.addView(tabs)

        info = tv("جاري التحميل...", 14f, 0xFFAEB6C5.toInt())
        root.addView(info, LinearLayout.LayoutParams(-1, 42))

        list = RecyclerView(this).apply {
            setHasFixedSize(false)
            clipToPadding = false
            setPadding(2, 8, 2, 30)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isFocusable = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun channelColumns(): Int {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return when {
            widthDp >= 1100 -> 4
            widthDp >= 700 -> 3
            else -> 2
        }
    }

    private fun selectTab(channels: Boolean) {
        tabChannels.isSelected = channels
        tabPackages.isSelected = !channels
        showingPackageChannels = false
        if (channels) showChannels() else showPackages()
    }

    private fun loadData() {
        info.text = "جاري تحميل القنوات والباقات..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val state = Api.license(this@ChannelsActivity, deviceId)
                if (!isLicenseActive(state)) throw Exception("الاشتراك غير فعال أو منتهي")
                allChannels = Api.channels(this@ChannelsActivity, deviceId)
                packages = Api.packages(this@ChannelsActivity, deviceId, allChannels)
                withContext(Dispatchers.Main) {
                    showingPackageChannels = false
                    info.text = "${allChannels.size} قناة متاحة"
                    showChannels()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    info.text = e.message ?: "تعذر تحميل البيانات"
                }
            }
        }
    }

    private fun showChannels(channels: List<Api.Channel> = if (showingPackageChannels) displayedChannels else allChannels) {
        info.text = "${channels.size} قناة متاحة"
        list.layoutManager = GridLayoutManager(this, channelColumns()).apply {
            isItemPrefetchEnabled = true
        }
        list.adapter = ChannelAdapter(channels)
    }

    private val displayedChannels: List<Api.Channel>
        get() = allChannels

    private fun showPackages() {
        info.text = "${packages.size} باقة متاحة"
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = PackageAdapter(packages)
    }

    private fun showPackageChannels(p: Api.Package) {
        val ids = p.channelIds.toSet()
        showingPackageChannels = true
        val filtered = allChannels.filter { it.id in ids }
        info.text = "${filtered.size} قناة في ${p.name}"
        tabChannels.isSelected = true
        tabPackages.isSelected = false
        list.layoutManager = GridLayoutManager(this, channelColumns())
        list.adapter = ChannelAdapter(filtered)
    }

    private inner class ChannelAdapter(private val items: List<Api.Channel>) :
        RecyclerView.Adapter<ChannelAdapter.Holder>() {

        inner class Holder(val card: LinearLayout) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
            val card = LinearLayout(this@ChannelsActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = getDrawable(R.drawable.bg_card)
                setPadding(14, 14, 14, 14)
                isFocusable = true
                isFocusableInTouchMode = false
                isClickable = true
                stateListAnimator = null
                elevation = 3f
            }
            return Holder(card)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val ch = items[position]
            val card = holder.card
            card.removeAllViews()

            val logo = ImageView(this@ChannelsActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = ch.name
            }
            card.addView(logo, LinearLayout.LayoutParams(-1, 92))

            card.addView(tv(ch.name, 17f, 0xFFFFFFFF.toInt(), true), LinearLayout.LayoutParams(-1, 38))
            card.addView(tv("● LIVE", 12f, 0xFF7DD3A7.toInt()), LinearLayout.LayoutParams(-1, 28))

            val watch = Button(this@ChannelsActivity).apply {
                text = "مشاهدة"
                isAllCaps = false
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
                background = getDrawable(R.drawable.bg_button)
                isFocusable = false
                setOnClickListener { play(ch) }
            }
            card.addView(watch, LinearLayout.LayoutParams(-1, 52))
            card.setOnClickListener { play(ch) }
            card.setOnFocusChangeListener { v, hasFocus ->
                v.scaleX = if (hasFocus) 1.04f else 1f
                v.scaleY = if (hasFocus) 1.04f else 1f
                v.elevation = if (hasFocus) 10f else 3f
            }
            ch.logoUrl?.let { loadImage(it, logo) }
        }

        override fun getItemCount() = items.size
    }

    private inner class PackageAdapter(private val items: List<Api.Package>) :
        RecyclerView.Adapter<PackageAdapter.Holder>() {
        inner class Holder(val card: LinearLayout) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
            val card = LinearLayout(this@ChannelsActivity).apply {
                orientation = LinearLayout.VERTICAL
                background = getDrawable(R.drawable.bg_card)
                setPadding(20, 18, 20, 18)
                isFocusable = true
                isClickable = true
            }
            return Holder(card)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val p = items[position]
            val card = holder.card
            card.removeAllViews()
            card.addView(tv(p.name, 21f, 0xFFFFFFFF.toInt(), true), LinearLayout.LayoutParams(-1, 42))
            card.addView(tv("${p.channelIds.size} قناة", 14f, 0xFFAEB6C5.toInt()), LinearLayout.LayoutParams(-1, 36))
            val b = Button(this@ChannelsActivity).apply {
                text = "عرض القنوات"
                isAllCaps = false
                setTextColor(0xFFFFFFFF.toInt())
                background = getDrawable(R.drawable.bg_button)
                setOnClickListener { showPackageChannels(p) }
            }
            card.addView(b, LinearLayout.LayoutParams(-1, 56))
            card.setOnClickListener { showPackageChannels(p) }
            card.setOnFocusChangeListener { v, hasFocus ->
                v.scaleX = if (hasFocus) 1.02f else 1f
                v.scaleY = if (hasFocus) 1.02f else 1f
                v.elevation = if (hasFocus) 10f else 3f
            }
        }

        override fun getItemCount() = items.size
    }

    private fun showSubscription() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val state = Api.license(this@ChannelsActivity, deviceId)
                val active = isLicenseActive(state)
                val activated = firstDate(state, "activated_at", "starts_at", "start_at", "created_at")
                    .ifBlank { prefs.getString("activation_recorded_at", "").orEmpty() }
                val expires = firstDate(state, "expires_at", "expiration_at", "expires")
                    .ifBlank { prefs.getString("subscription_expires_at", "").orEmpty() }
                val message = if (active) {
                    "الحالة: فعال\n\nتاريخ التفعيل: ${formatServerDate(activated)}\nتاريخ الانتهاء: ${formatServerDate(expires)}"
                } else "الحالة: غير فعال أو منتهي"
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@ChannelsActivity)
                        .setTitle("معلومات الاشتراك")
                        .setMessage(message)
                        .setPositiveButton("حسناً", null)
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChannelsActivity, e.message ?: "تعذر جلب الاشتراك", Toast.LENGTH_LONG).show()
                }
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

    private fun firstDate(obj: org.json.JSONObject, vararg keys: String): String {
        val subscription = obj.optJSONObject("subscription")
        val license = obj.optJSONObject("license")
        for (key in keys) {
            val a = subscription?.optString(key).orEmpty()
            if (a.isNotBlank()) return a
            val b = license?.optString(key).orEmpty()
            if (b.isNotBlank()) return b
            val c = obj.optString(key).orEmpty()
            if (c.isNotBlank()) return c
        }
        return ""
    }

    private fun formatServerDate(value: String): String {
        if (value.isBlank()) return "غير متوفر"
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        )
        for (input in formats) {
            try {
                val date = input.parse(value) ?: continue
                return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(date)
            } catch (_: Exception) { }
        }
        return value
    }

    private fun loadImage(url: String, image: ImageView) {
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
                    if (!isLicenseActive(state)) {
                        startActivity(Intent(this@ChannelsActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                        finish()
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
