package com.hichrawi.tv

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("hichrawi", MODE_PRIVATE) }
    private lateinit var message: TextView
    private lateinit var codeInput: EditText
    private lateinit var activateButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF07090D.toInt()
        window.navigationBarColor = 0xFF07090D.toInt()
        showSplash()
    }

    private fun tv(value: String, size: Float, color: Int = 0xFFFFFFFF.toInt(), bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun showSplash() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(0xFF07090D.toInt()); setPadding(36, 30, 36, 30) }
        val logo = ImageView(this).apply { setImageResource(R.drawable.hichrawi_live_logo); scaleType = ImageView.ScaleType.CENTER_INSIDE }
        root.addView(logo, LinearLayout.LayoutParams(-1, 250))
        root.addView(tv("HICHRAWI LIVE", 30f, 0xFFFFFFFF.toInt(), true), LinearLayout.LayoutParams(-1, 52))
        root.addView(tv("مشاهدة مباشرة • قنواتك في مكان واحد", 15f, 0xFFAEB6C5.toInt()))
        setContentView(root)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { AppConfig.refreshRemote(this@MainActivity) }
            delay(2200)
            checkUpdateThenContinue()
        }
    }

    private fun checkUpdateThenContinue() {
        val info = AppConfig.updateInfo()
        if (info.latestVersionCode > BuildConfig.VERSION_CODE && !info.apkUrl.isNullOrBlank()) {
            val dialog = AlertDialog.Builder(this)
                .setTitle("تحديث جديد متوفر")
                .setMessage(info.message ?: "يتوفر إصدار أحدث من HICHRAWI LIVE.")
                .setPositiveButton("تحديث الآن") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl))) }
            if (info.force) { dialog.setCancelable(false); dialog.setNegativeButton(null, null) } else dialog.setNegativeButton("لاحقاً", null)
            dialog.show()
            if (info.force) return
        }
        continueFromSavedLicense()
    }

    private fun continueFromSavedLicense() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val id = ensureDevice(); val state = Api.license(this@MainActivity, id)
                val active = state.optJSONObject("subscription")?.optBoolean("active") == true
                withContext(Dispatchers.Main) { if (active) openChannels() else showActivation() }
            } catch (_: Exception) { withContext(Dispatchers.Main) { showActivation() } }
        }
    }

    private fun showActivation() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(28, 18, 28, 24); setBackgroundColor(0xFF07090D.toInt()) }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(8, 8, 8, 20) }
        val logo = ImageView(this).apply { setImageResource(R.drawable.hichrawi_live_logo); scaleType = ImageView.ScaleType.CENTER_INSIDE; contentDescription = "HICHRAWI LIVE" }
        body.addView(logo, LinearLayout.LayoutParams(-1, 210))
        body.addView(tv("HICHRAWI LIVE", 28f, 0xFFFFFFFF.toInt(), true), LinearLayout.LayoutParams(-1, 48))
        body.addView(tv("أدخل رمز الاشتراك للمتابعة", 17f, 0xFFAEB6C5.toInt()), LinearLayout.LayoutParams(-1, 42))

        codeInput = EditText(this).apply {
            hint = "أدخل كود التفعيل"
            textSize = 22f
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(13))
            isSingleLine = true
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF687185.toInt())
            setSelectAllOnFocus(false)
            background = getDrawable(R.drawable.bg_input)
            letterSpacing = 0.12f
            setPadding(18, 0, 18, 0)
        }
        body.addView(codeInput, LinearLayout.LayoutParams(-1, 76).apply { setMargins(0, 18, 0, 14) })

        activateButton = Button(this).apply {
            text = "تفعيل الاشتراك"; textSize = 18f; isAllCaps = false; setTextColor(0xFFFFFFFF.toInt()); background = getDrawable(R.drawable.bg_button); stateListAnimator = null
        }
        body.addView(activateButton, LinearLayout.LayoutParams(-1, 62))
        message = tv("أدخل كود الاشتراك للمتابعة", 14f, 0xFFAEB6C5.toInt())
        body.addView(message, LinearLayout.LayoutParams(-1, 60))
        body.addView(tv("لا يوجد اشتراك تجريبي تلقائي — استخدم رمز التجربة المخصص عند الحاجة.", 12f, 0xFF697387.toInt()), LinearLayout.LayoutParams(-1, 45))
        scroll.addView(body); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)

        lifecycleScope.launch(Dispatchers.IO) {
            try { val id = ensureDevice(); val state = Api.license(this@MainActivity, id); if (state.optJSONObject("subscription")?.optBoolean("active") == true) withContext(Dispatchers.Main) { openChannels() } }
            catch (_: Exception) { }
        }

        activateButton.setOnClickListener {
            val code = codeInput.text.toString().trim().uppercase()
            if (code.isEmpty()) { message.text = "اكتب كود الاشتراك أولاً"; return@setOnClickListener }
            hideKeyboard(); activateButton.isEnabled = false; message.text = "جاري التحقق من الكود..."
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val id = ensureDevice(); Api.activate(this@MainActivity, code, id)
                    val state = Api.license(this@MainActivity, id)
                    if (state.optJSONObject("subscription")?.optBoolean("active") != true) throw Exception("الكود غير فعال أو منتهي")
                    withContext(Dispatchers.Main) { openChannels() }
                } catch (e: Exception) { withContext(Dispatchers.Main) { message.text = friendlyError(e.message); activateButton.isEnabled = true } }
            }
        }
        codeInput.requestFocus()
    }

    private fun friendlyError(raw: String?): String = when {
        raw?.contains("invalid_or_disabled", true) == true -> "كود غير صحيح أو معطل"
        raw?.contains("expired", true) == true -> "هذا الكود منتهي الصلاحية"
        raw?.contains("device_limit", true) == true -> "تم بلوغ الحد الأقصى للأجهزة"
        raw?.contains("device_already", true) == true -> "الجهاز مرتبط باشتراك آخر"
        raw?.contains("server", true) == true -> "تعذر الاتصال بالخادم"
        else -> raw ?: "فشل التفعيل"
    }

    private fun hideKeyboard() { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(codeInput.windowToken, 0) }

    private suspend fun ensureDevice(): Long = withContext(Dispatchers.IO) {
        val saved = prefs.getLong("server_device_id", 0L); if (saved > 0L) return@withContext saved
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val key = prefs.getString("device_key", null) ?: ("android-$androidId-${UUID.randomUUID()}").also { prefs.edit().putString("device_key", it).apply() }
        Api.registerDevice(this@MainActivity, key).also { prefs.edit().putLong("server_device_id", it).apply() }
    }

    private fun openChannels() { startActivity(Intent(this, ChannelsActivity::class.java)); finish() }
}
