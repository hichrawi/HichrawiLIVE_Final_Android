package com.hichrawi.tv

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
    private lateinit var tabChannels: Button
    private lateinit var tabPackages: Button
    private var deviceId = 0L
    private var licenseJob: Job? = null
    private var allChannels: List<Api.Channel> = emptyList()
    private var packages: List<Api.Package> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF07090D.toInt(); window.navigationBarColor = 0xFF07090D.toInt()
        deviceId = prefs.getLong("server_device_id", 0L); buildUi(); loadData()
    }

    private fun tv(s:String, size:Float, color:Int=0xFFFFFFFF.toInt(), bold:Boolean=false)=TextView(this).apply{ text=s;textSize=size;setTextColor(color);gravity=Gravity.CENTER; if(bold)setTypeface(typeface,android.graphics.Typeface.BOLD)}

    private fun buildUi() {
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(0xFF07090D.toInt());setPadding(18,14,18,14)}
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val logo=ImageView(this).apply{setImageResource(R.drawable.hichrawi_live_logo);scaleType=ImageView.ScaleType.CENTER_INSIDE}
        head.addView(logo,LinearLayout.LayoutParams(62,62)); head.addView(tv("HICHRAWI LIVE",22f,0xFFFFFFFF.toInt(),true),LinearLayout.LayoutParams(0,62,1f))
        val refresh=Button(this).apply{text="↻";textSize=22f;isAllCaps=false;setOnClickListener{loadData()}}
        head.addView(refresh,LinearLayout.LayoutParams(58,58)); root.addView(head)
        root.addView(tv("شاهد قنواتك مباشرة",14f,0xFFAEB6C5.toInt()),LinearLayout.LayoutParams(-1,36))
        val tabs=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,8,0,8)}
        tabChannels=Button(this).apply{text="القنوات";isAllCaps=false;setTextColor(0xFFFFFFFF.toInt());background=getDrawable(R.drawable.bg_tab);isSelected=true;setOnClickListener{selectTab(true)}}
        tabPackages=Button(this).apply{text="الباقات";isAllCaps=false;setTextColor(0xFFFFFFFF.toInt());background=getDrawable(R.drawable.bg_tab);setOnClickListener{selectTab(false)}}
        tabs.addView(tabChannels,LinearLayout.LayoutParams(0,56,1f).apply{setMargins(0,0,6,0)});tabs.addView(tabPackages,LinearLayout.LayoutParams(0,56,1f).apply{setMargins(6,0,0,0)});root.addView(tabs)
        info=tv("جاري التحميل...",14f,0xFFAEB6C5.toInt());root.addView(info,LinearLayout.LayoutParams(-1,42))
        val scroll=ScrollView(this);list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(2,8,2,30)};scroll.addView(list);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }

    private fun selectTab(channels:Boolean){tabChannels.isSelected=channels;tabPackages.isSelected=!channels; if(channels)showChannels() else showPackages()}

    private fun loadData(){
        info.text="جاري تحميل القنوات والباقات...";list.removeAllViews()
        lifecycleScope.launch(Dispatchers.IO){
            try{
                val state=Api.license(this@ChannelsActivity,deviceId);if(state.optJSONObject("subscription")?.optBoolean("active")!=true)throw Exception("الاشتراك غير فعال أو منتهي")
                allChannels=Api.channels(this@ChannelsActivity,deviceId);packages=Api.packages(this@ChannelsActivity,deviceId,allChannels)
                withContext(Dispatchers.Main){info.text="${allChannels.size} قناة متاحة";showChannels()}
            }catch(e:Exception){withContext(Dispatchers.Main){info.text=e.message?:"تعذر تحميل البيانات"}}
        }
    }

    private fun showChannels(){list.removeAllViews();info.text="${allChannels.size} قناة متاحة";allChannels.forEach(::addChannel)}

    private fun showPackages(){list.removeAllViews(); if(packages.isEmpty()){list.addView(tv("لا توجد باقات إضافية حالياً",16f,0xFFAEB6C5.toInt()),LinearLayout.LayoutParams(-1,80));return};packages.forEach{p->
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=getDrawable(R.drawable.bg_card);setPadding(20,18,20,18);isFocusable=true;isClickable=true}
        card.addView(tv(p.name,21f,0xFFFFFFFF.toInt(),true),LinearLayout.LayoutParams(-1,42));card.addView(tv("${p.channelIds.size} قناة",14f,0xFFAEB6C5.toInt()),LinearLayout.LayoutParams(-1,36));
        val b=Button(this).apply{text="عرض القنوات";isAllCaps=false;setTextColor(0xFFFFFFFF.toInt());background=getDrawable(R.drawable.bg_button);setOnClickListener{showPackageChannels(p)}};card.addView(b,LinearLayout.LayoutParams(-1,56));list.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,12)})
    }}

    private fun showPackageChannels(p:Api.Package){val ids=p.channelIds.toSet();val old=allChannels;allChannels=old.filter{it.id in ids};selectTab(true);allChannels=old}

    private fun addChannel(ch:Api.Channel){
        val card=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;background=getDrawable(R.drawable.bg_card);isFocusable=true;isClickable=true;setPadding(12,10,12,10)}
        val logo=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_INSIDE;contentDescription=ch.name};card.addView(logo,LinearLayout.LayoutParams(88,76))
        val middle=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;setPadding(14,0,10,0)}
        middle.addView(tv(ch.name,18f,0xFFFFFFFF.toInt(),true),LinearLayout.LayoutParams(-1,40));middle.addView(tv("LIVE • بث مباشر",12f,0xFF7DD3A7.toInt()),LinearLayout.LayoutParams(-1,30));card.addView(middle,LinearLayout.LayoutParams(0,76,1f))
        val watch=Button(this).apply{text="مشاهدة";isAllCaps=false;textSize=15f;setTextColor(0xFFFFFFFF.toInt());background=getDrawable(R.drawable.bg_button);setOnClickListener{play(ch)}};card.addView(watch,LinearLayout.LayoutParams(112,56))
        card.setOnClickListener{play(ch)};ch.logoUrl?.let{loadImage(it,logo)};list.addView(card,LinearLayout.LayoutParams(-1,96).apply{setMargins(0,0,0,12)})
    }

    private fun loadImage(url:String,image:ImageView){lifecycleScope.launch(Dispatchers.IO){try{val req=okhttp3.Request.Builder().url(url).build();okhttp3.OkHttpClient().newCall(req).execute().use{r->if(!r.isSuccessful)return@use;val bytes=r.body?.bytes()?:return@use;val bmp=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:return@use;withContext(Dispatchers.Main){image.setImageBitmap(bmp)}}}catch(_:Exception){}}}
    private fun play(ch:Api.Channel){startActivity(Intent(this,PlayerActivity::class.java).apply{putExtra("channel_id",ch.id);putExtra("channel_name",ch.name);putExtra("logo_url",ch.logoUrl)})}

    override fun onStart(){super.onStart();licenseJob=lifecycleScope.launch{while(true){delay(60000);try{val state=withContext(Dispatchers.IO){Api.license(this@ChannelsActivity,deviceId)};if(state.optJSONObject("subscription")?.optBoolean("active")!=true){startActivity(Intent(this@ChannelsActivity,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();break}}catch(_:Exception){}}}}
    override fun onStop(){licenseJob?.cancel();licenseJob=null;super.onStop()}
}
