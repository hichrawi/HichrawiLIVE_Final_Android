import { initializeApp } from "https://www.gstatic.com/firebasejs/12.16.0/firebase-app.js";
import { getAuth, signInWithEmailAndPassword, onAuthStateChanged, signOut } from "https://www.gstatic.com/firebasejs/12.16.0/firebase-auth.js";
import { getFirestore, collection, getDocs, addDoc, deleteDoc, doc, setDoc, getDoc, updateDoc } from "https://www.gstatic.com/firebasejs/12.16.0/firebase-firestore.js";
import { getStorage, ref, uploadBytes, getDownloadURL, deleteObject } from "https://www.gstatic.com/firebasejs/12.16.0/firebase-storage.js";
import { firebaseConfig } from "./admin-config.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

// Railway stream engine API
const STREAM_ENGINE_URL = "https://hichrawi-tv.duckdns.org";
// ================= HICHRAWI V2 SOURCE TYPES =================
// Supported universal source types:
// iptv / hls / m3u8 / m3u / direct_video / mp4 / radio / mp3 / aac / rtmp / rtsp / youtube / videos

window.HICHRAWI_SOURCE_TYPES = [
  {value:"iptv", label:"📡 IPTV / M3U8"},
  {value:"m3u", label:"📋 M3U Playlist"},
  {value:"direct_video", label:"🎞️ رابط فيديو مباشر"},
  {value:"radio", label:"📻 Radio / MP3 / AAC"},
  {value:"rtmp", label:"🔴 RTMP"},
  {value:"rtsp", label:"🟣 RTSP"},
  {value:"youtube", label:"▶️ YouTube"},
  {value:"videos", label:"🎬 فيديوهاتي / Playlist"}
];

window.getHichrawiSourceTypeLabel = function(type){
  const x = HICHRAWI_SOURCE_TYPES.find(v=>v.value===type);
  return x ? x.label : type;
};


const sourceStatusStyle = document.createElement("style");
sourceStatusStyle.textContent = `
#sourceSwitchStatus{
  margin:14px 0 0;padding:13px 16px;border-radius:10px;
  background:#1b1b1b;border:1px solid #444;color:#ddd;
  font-weight:700;line-height:1.7;display:none
}
#sourceSwitchStatus.pending{display:block;border-color:#d99f00;color:#ffd45a}
#sourceSwitchStatus.success{display:block;border-color:#21a366;color:#52e58f}
#sourceSwitchStatus.error{display:block;border-color:#c0392b;color:#ff7b72}
`;
document.head.appendChild(sourceStatusStyle);

const storage = getStorage(app);

// دخول الإدارة
document.getElementById("loginBtn")?.addEventListener("click", async ()=>{
 try{
  await signInWithEmailAndPassword(
   auth,
   document.getElementById("loginEmail").value,
   document.getElementById("loginPassword").value
  );
 }catch(e){
  document.getElementById("loginError").innerHTML="❌ الإيميل أو كلمة السر خاطئة";
 }
});

onAuthStateChanged(auth,(user)=>{
 const box=document.getElementById("loginBox");
 if(user){
  if(box) box.style.display="none";
 loadChannels();
 loadSubscriptions();
loadVideos();
  loadServerVideos();
  loadStreamSettings();
  loadAnnouncement();
   loadSettings();
   loadBroadcastSources();
   refreshViewerStats();
   refreshLiveDashboard();
   if(!window.__hichrawiAdminPollingStarted){
     window.__hichrawiAdminPollingStarted = true;
     window.__hichrawiAdminPolling = setInterval(()=>{
       refreshViewerStats();
       refreshLiveDashboard();
       loadBroadcastSources();
     },10000);
   }
 }else{
  if(box) box.style.display="flex";
  if(window.__hichrawiAdminPolling){
    clearInterval(window.__hichrawiAdminPolling);
    window.__hichrawiAdminPolling = null;
    window.__hichrawiAdminPollingStarted = false;
  }
 }
});

document.querySelector(".logout")?.addEventListener("click",()=>signOut(auth));


// فتح نافذة إضافة قناة
window.openAddChannel=()=>{
 const m=document.getElementById("channelModal");
 if(m) m.style.display="flex";
};

window.closeChannel=()=>{
 const m=document.getElementById("channelModal");
 if(m) m.style.display="none";
};


// إضافة قناة
window.saveChannel=async()=>{
 const name=document.getElementById("newChannelName").value;
 const logo=document.getElementById("newChannelLogo").value;
 const stream=document.getElementById("newChannelStream").value;
 const status=document.getElementById("newChannelStatus").value;

 if(!name || !stream){
  alert("أدخل اسم القناة ورابط البث");
  return;
 }

 const channelSnap=await getDocs(collection(db,"channels"));
 const usedIds=channelSnap.docs.map(d=>Number(d.data()?.channelId)).filter(Number.isFinite);
 const channelId=usedIds.length?Math.max(...usedIds)+1:1;
 const keyInput=document.getElementById("newChannelKey");
 const alphabet="ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
 let channelKey="";
 if(keyInput?.value.trim()) channelKey=keyInput.value.trim().replace(/[^A-Za-z0-9]/g,"");
 if(channelKey.length===0){
  if(window.crypto?.getRandomValues){const bytes=new Uint8Array(8);crypto.getRandomValues(bytes);for(const b of bytes)channelKey+=alphabet[b%alphabet.length];}
  else for(let i=0;i<8;i++)channelKey+=String(Math.floor(Math.random()*10));
 }
 if(channelKey.length!==13){alert("❌ Channel Key يجب أن يكون 13 خانة.");return;}
 await addDoc(collection(db,"channels"),{
  channelId,name,logo,stream,status,channelKey,key:channelKey,enabled:true,sortOrder:channelId,createdAt:new Date()
 });

 alert("تمت إضافة القناة");
 closeChannel();
 loadChannels();
};


// عرض القنوات
async function loadChannels(){
 const table=document.getElementById("channelsTable");
 if(!table)return;

 table.innerHTML="";

 const snap=await getDocs(collection(db,"channels"));
 let count=0;

 snap.forEach(item=>{
  count++;
  const d=item.data();

  table.innerHTML+=`
  <tr>
   <td><img src="${d.logo||''}" width="50"></td>
   <td>${d.name||""}</td>
   <td><code>${d.channelKey||d.key||"—"}</code></td>
   <td>${d.stream||""}</td>
   <td>${d.status||""}</td>
   <td><button onclick="deleteChannel('${item.id}')">🗑 حذف</button></td>
  </tr>`;
 });

 document.getElementById("channelsCount").innerText=count;
}

window.deleteChannel=async(id)=>{
 if(confirm("حذف القناة؟")){
  await deleteDoc(doc(db,"channels",id));
  loadChannels();
 }
};



// ================= BROADCAST SOURCE MANAGER =================
// Saves source definitions in Firestore. The active source is also written to
// settings/stream as requestedSource/sourceType. The existing IPTV runtime
// remains untouched until the server-side source engine is connected.

function sourceTypeLabel(type){
  const labels = {
    iptv:"📡 IPTV / M3U8",
    m3u:"📋 M3U Playlist",
    direct_video:"🎞️ فيديو مباشر",
    radio:"📻 Radio / MP3 / AAC",
    rtmp:"🔴 RTMP",
    rtsp:"🟣 RTSP",
    youtube:"▶️ YouTube",
    videos:"🎬 فيديوهاتي / Playlist"
  };
  return labels[type] || ("📡 " + (type || "مصدر"));
}

function sourceTypeName(type){
  const supported = ["iptv","m3u","direct_video","radio","rtmp","rtsp","youtube","videos"];
  return supported.includes(type) ? type : "iptv";
}

window.saveHichrawiSource = async({name,type,url,libraryId})=>{
  const cleanName = (name || "").trim();
  const cleanUrl = (url || "").trim();

  if(!cleanName){
    throw new Error("اسم المصدر مطلوب");
  }

  if(type !== "videos" && !cleanUrl){
    throw new Error("رابط المصدر مطلوب");
  }

  await addDoc(collection(db,"broadcastSources"),{
    name: cleanName,
    type: sourceTypeName(type),
    url: cleanUrl,
    libraryId: type === "videos" ? (libraryId || "") : "",
    enabled: false,
    active: false,
    createdAt: new Date()
  });

  await loadBroadcastSources();
};


async function syncSourceStatusFromEngine(){
  try{
    const r = await fetch(STREAM_ENGINE_URL + "/api/status?ts=" + Date.now(), {cache:"no-store"});
    if(!r.ok) return null;
    const state = await r.json();
    const name = String(state.source_name || "").trim();
    const type = String(state.source_type || "").trim().toLowerCase();
    if(name){
      updateActiveSourceUI({name, type});
    }
    return state;
  }catch(e){
    console.warn("syncSourceStatusFromEngine", e);
    return null;
  }
}


async function loadFallbackSelector(sourcesSnap, fallbackState={}){
  const select=document.getElementById("fallbackSourceSelect");
  if(!select) return;
  const currentId=String(fallbackState?.fallback?.sourceId||"");
  const currentName=String(fallbackState?.fallback?.name||"");
  select.innerHTML='<option value="">اختر المصدر الاحتياطي</option>';
  let defaultNationalId="";
  sourcesSnap.forEach(item=>{
    const d=item.data();
    const name=String(d.name||"").trim();
    const o=document.createElement("option");
    o.value=item.id;
    o.textContent=name+" — "+(getHichrawiSourceTypeLabel(d.type)||d.type||"");
    select.appendChild(o);
    if(!defaultNationalId && (name==="الوطنية 1" || /الوطنية\s*1/.test(name))) defaultNationalId=item.id;
  });
  if(currentId) select.value=currentId;
  else if(currentName){
    const opt=[...select.options].find(o=>o.textContent.startsWith(currentName+" —"));
    if(opt) select.value=opt.value;
  }
  if(!currentId && !currentName && defaultNationalId){
    try{
      const source=await getSourceDefinitionForSchedule(defaultNationalId);
      await postFallbackSource(source);
      select.value=defaultNationalId;
      const label=document.getElementById("fallbackSourceName");
      if(label) label.textContent=source.name;
    }catch(e){ console.warn("default fallback",e); }
  }
}

window.saveSelectedFallback=async function(){
  try{
    const select=document.getElementById("fallbackSourceSelect");
    const id=select?.value||"";
    if(!id){showSourceSwitchStatus("error","⚠️ اختر مصدرًا احتياطيًا أولاً.");return;}
    const source=await getSourceDefinitionForSchedule(id);
    showSourceSwitchStatus("pending","🟡 جاري حفظ المصدر الاحتياطي: "+source.name);
    await postFallbackSource(source);
    const label=document.getElementById("fallbackSourceName");
    if(label) label.textContent=source.name;
    showSourceSwitchStatus("success","🟢 تم تغيير المصدر الاحتياطي إلى: "+source.name);
    await loadBroadcastSources();
  }catch(e){
    console.error("saveSelectedFallback",e);
    showSourceSwitchStatus("error","🔴 تعذر تغيير المصدر الاحتياطي: "+(e.message||e));
  }
};

async function loadBroadcastSources(){
  const list = document.getElementById("broadcastSourcesList");
  if(!list) return;

  try{
    const sourcesSnap = await getDocs(collection(db,"broadcastSources"));
    const playlistsSnap = await getDocs(collection(db,"playlists"));
    const playlistNames = {};
    playlistsSnap.forEach(p => { playlistNames[p.id] = p.data().name || p.data().title || p.id; });
    const streamSnap = await getDoc(doc(db,"settings","stream"));
    const streamData = streamSnap.exists() ? streamSnap.data() : {};
    let engineState = {};
    try{
      const er = await fetch(STREAM_ENGINE_URL + "/api/status?ts=" + Date.now(), {cache:"no-store"});
      if(er.ok) engineState = await er.json();
    }catch(e){ console.warn("engine status", e); }
    let fallbackState = {};
    try{
      const fr = await fetch(STREAM_ENGINE_URL + "/api/fallback?ts=" + Date.now(), {cache:"no-store"});
      if(fr.ok) fallbackState = await fr.json();
    }catch(e){ console.warn("fallback status", e); }
    await loadFallbackSelector(sourcesSnap, fallbackState);
    const engineName = String(engineState.source_name || "").trim();
    const engineType = String(engineState.source_type || "").trim().toLowerCase();
    const activeId = streamData.activeSourceId || "";
    const fallback = fallbackState.fallback || {};
    const fallbackName = String(fallback.name || "").trim();
    const fallbackType = String(fallback.type || "").trim().toLowerCase();
    const fallbackId = String(fallback.sourceId || "").trim();
    const fallbackLabel = fallbackName || "غير محدد";
    const fallbackUi = document.getElementById("fallbackSourceName");
    if(fallbackUi) fallbackUi.textContent = fallbackLabel;

    if(sourcesSnap.empty){
      list.innerHTML = `
        <div class="source-item">
          <div class="source-item-head">
            <strong>لا توجد مصادر محفوظة</strong>
            <span class="source-badge">متوقف</span>
          </div>
        </div>`;
      updateActiveSourceUI(null);
      return;
    }

    list.innerHTML = "";

    sourcesSnap.forEach(item=>{
      const d = item.data();
      const isActive = (engineName && String(d.name || "").trim() === engineName &&
                        (!engineType || String(d.type || "").toLowerCase() === engineType)) ||
                       (!engineName && item.id === activeId);
      const safeName = String(d.name || "").replace(/</g,"&lt;").replace(/>/g,"&gt;");
      const safeUrl = String(d.url || "").replace(/</g,"&lt;").replace(/>/g,"&gt;");

      list.innerHTML += `
        <div class="source-item ${isActive ? "active" : ""}" data-source-id="${item.id}">
          <div class="source-item-head">
            <strong>${safeName}</strong>
            <span class="source-badge ${isActive ? "active" : ""}">
              ${isActive ? "🟢 يعمل" : "متوقف"}
            </span>
          </div>
          <div class="source-help">
            ${sourceTypeLabel(d.type)}
            ${d.type === "videos" ? " — قائمة: " + (playlistNames[d.libraryId] || d.libraryId || "غير محددة") : (safeUrl ? " — " + safeUrl : "")}
          </div>
          <div class="source-actions">
            <button class="btn add" onclick="startHichrawiSource('${item.id}')">
              ▶️ تشغيل
            </button>
            <button class="btn stop" onclick="stopHichrawiSource('${item.id}')">
              ⏹️ إيقاف
            </button>
            <button class="btn secondary" onclick="setHichrawiFallback('${item.id}')">
              ${((fallbackId && fallbackId===item.id) || (!fallbackId && fallbackName && fallbackName===d.name)) ? "🛡️ احتياطي رئيسي" : "⭐ اجعله احتياطي"}
            </button>
            <button class="btn delete" onclick="deleteHichrawiSource('${item.id}')">
              🗑️ حذف
            </button>
          </div>
        </div>`;
    });

    if(activeId){
      const activeDoc = await getDoc(doc(db,"broadcastSources",activeId));
      updateActiveSourceUI(activeDoc.exists() ? {id:activeId, ...activeDoc.data()} : null);
    }else{
      updateActiveSourceUI(null);
    }
  }catch(error){
    console.error("loadBroadcastSources:", error);
    list.innerHTML = `
      <div class="source-item">
        <strong>تعذر تحميل المصادر</strong>
        <div class="source-help">تحقق من صلاحيات Firestore.</div>
      </div>`;
  }
}

function updateActiveSourceUI(source){
  const name = document.getElementById("activeSourceName");
  const type = document.getElementById("activeSourceType");
  const dot = document.getElementById("activeSourceDot");

  if(!name || !type || !dot) return;

  if(!source){
    name.textContent = "لا يوجد مصدر نشط";
    type.textContent = "متوقف";
    type.classList.remove("active");
    dot.classList.remove("active");
    return;
  }

  name.textContent = source.name || "مصدر";
  type.textContent = sourceTypeLabel(source.type);
  type.classList.add("active");
  dot.classList.add("active");
}


function ensureSourceSwitchStatus(){
  let el = document.getElementById("sourceSwitchStatus");
  if(el) return el;

  // Put the status immediately above the source control area if possible.
  const anchor = document.querySelector("#sourcesSection") ||
                 document.querySelector(".source-manager") ||
                 document.body;
  el = document.createElement("div");
  el.id = "sourceSwitchStatus";
  anchor.prepend(el);
  return el;
}

function showSourceSwitchStatus(type, message){
  const el = ensureSourceSwitchStatus();
  el.className = "sourceSwitchStatus " + type;
  el.style.display = "block";
  el.textContent = message;
}

async function waitForSourceSwitch(expectedName, expectedUrl) {
  const deadline = Date.now() + (60 * 1000);
  const wantedName = String(expectedName || "").trim();
  const wantedUrl = String(expectedUrl || "").trim().replace(/\/+$/, "");

  while (Date.now() < deadline) {
    try {
      const response = await fetch(
        STREAM_ENGINE_URL + "/api/status?ts=" + Date.now(),
        {
          cache: "no-store",
          headers: { "Accept": "application/json" }
        }
      );

      if (response.ok) {
        const state = await response.json();

        const status = String(state.status || "").trim().toLowerCase();
        const activeName = String(
          state.active_name ??
          state.source_name ??
          state.activeSourceName ??
          ""
        ).trim();

        const activeUrl = String(
          state.active_source ??
          state.source_url ??
          state.activeSource ??
          state.url ??
          ""
        ).trim().replace(/\/+$/, "");

        const message = String(state.message || "").trim().toLowerCase();

        // Railway's real successful state:
        // status=active + active_name/active_source
        // or the explicit success message.
        const nameOK = !wantedName || activeName === wantedName;
        const urlOK = !wantedUrl || activeUrl === wantedUrl;

        const success =
          (status === "active" && (nameOK || urlOK)) ||
          message.includes("source switched successfully") ||
          (wantedUrl && activeUrl === wantedUrl) ||
          (wantedName && activeName === wantedName);

        if (success) {
          showSourceSwitchStatus(
            "success",
            "🟢 نجحت العملية — تم تبديل البث إلى: " +
            (activeName || wantedName || "المصدر الجديد")
          );
          return true;
        }

        if (
          state.switch_failed === true ||
          state.switchFailed === true ||
          status === "failed" ||
          status === "error"
        ) {
          showSourceSwitchStatus(
            "error",
            "🔴 فشل تبديل المصدر — البث الحالي مستمر."
          );
          return false;
        }

        showSourceSwitchStatus(
          "pending",
          "🟡 جاري تحضير المصدر الجديد... لا تضغط تشغيل مرة أخرى."
        );
      }
    } catch (error) {
      console.warn("status check:", error);
    }

    await new Promise(resolve => setTimeout(resolve, 2000));
  }

  // One final status read before declaring failure.
  try {
    const finalResponse = await fetch(
      STREAM_ENGINE_URL + "/api/status?ts=" + Date.now(),
      { cache: "no-store", headers: { "Accept": "application/json" } }
    );

    if (finalResponse.ok) {
      const finalState = await finalResponse.json();
      const finalName = String(
        finalState.active_name ??
        finalState.source_name ??
        finalState.activeSourceName ??
        ""
      ).trim();
      const finalUrl = String(
        finalState.active_source ??
        finalState.source_url ??
        finalState.activeSource ??
        finalState.url ??
        ""
      ).trim().replace(/\/+$/, "");

      const nameMatches =
        !!wantedName && finalName === wantedName;

      const urlMatches =
        !!wantedUrl &&
        finalUrl.replace(/\/+$/, "") === wantedUrl.replace(/\/+$/, "");

      // IPTV: exact URL match is mandatory.
      // Video: exact name match is mandatory.
      const sourceMatches = wantedUrl
        ? urlMatches
        : nameMatches;

      if (
        finalState.status === "active" &&
        sourceMatches
      ) {
showSourceSwitchStatus(
          "success",
          "🟢 نجحت العملية — تم تبديل البث إلى: " +
          (finalName || wantedName || "المصدر الجديد")
        );
        return true;
      }
    }
  } catch (e) {
    console.warn("final status check:", e);
  }

  showSourceSwitchStatus(
    "error",
    "🔴 لم يصل تأكيد من محرك البث. تحقق من حالة المصدر قبل إعادة المحاولة."
  );
  return false;
}

window.startHichrawiSource = async(id)=>{
  try{
    // If no ID is supplied, use the currently marked active source.
    if(!id){
      const streamSnap = await getDoc(doc(db,"settings","stream"));
      const streamData = streamSnap.exists() ? streamSnap.data() : {};
      id = streamData.activeSourceId || "";
    }

    if(!id){
      alert("❌ اختر مصدرًا أولاً.");
      return;
    }

    const sourceRef = doc(db,"broadcastSources",id);
    const snap = await getDoc(sourceRef);

    if(!snap.exists()){
      alert("❌ المصدر غير موجود");
      return;
    }

    const source = snap.data();
    let items = [];

    // Resolve a video playlist into concrete server/local URLs.
    if(source.type === "videos"){
      if(!source.libraryId){
        alert("❌ لم يتم تحديد قائمة تشغيل للفيديوهات");
        return;
      }

      const pSnap = await getDoc(doc(db,"playlists",source.libraryId));
      if(!pSnap.exists()){
        alert("❌ قائمة التشغيل غير موجودة");
        return;
      }

      const ids = Array.isArray(pSnap.data().videoIds) ? pSnap.data().videoIds : [];
      for(const videoId of ids){
        const vSnap = await getDoc(doc(db,"videos",videoId));
        if(!vSnap.exists()) continue;
        const v = vSnap.data();

        // Prefer serverPath for videos already stored on Railway.
        if(v.serverPath){
          items.push("/videos/" + encodeURIComponent(v.serverPath).replace(/%2F/g,"/"));
        }else if(v.url){
          items.push(v.url);
        }
      }

      if(!items.length){
        alert("❌ قائمة التشغيل فارغة");
        return;
      }
    }

    const user = auth.currentUser;
    if(!user){
      alert("❌ انتهت جلسة الإدارة. سجل الدخول من جديد.");
      return;
    }

    const idToken = await user.getIdToken();

    const request = {
      type: source.type || "iptv",
      name: source.name || "",
      url: source.type === "videos" ? "" : (source.url || ""),
      items,
      requestedAt: Date.now()
    };

    showSourceSwitchStatus(
      "pending",
      "🟡 تم إرسال المصدر الجديد... جاري تحضيره قبل تبديل البث."
    );

    const response = await fetch(STREAM_ENGINE_URL + "/api/source",{
      method:"POST",
      headers:{
        "Content-Type":"application/json",
        "Authorization":"Bearer " + idToken,
        "X-Firebase-Api-Key": firebaseConfig.apiKey
      },
      body:JSON.stringify(request)
    });

    if(!response.ok){
      const text=await response.text();
      throw new Error(text || ("HTTP "+response.status));
    }

    // Mark the requested source in Firestore without changing the old URL blindly.
    const all = await getDocs(collection(db,"broadcastSources"));
    await Promise.all(all.docs.map(item =>
      updateDoc(doc(db,"broadcastSources",item.id),{
        enabled:item.id===id,
        active:item.id===id,
        updatedAt:new Date()
      })
    ));

    await setDoc(doc(db,"settings","stream"),{
      activeSourceId:id,
      activeSourceName:source.name||"",
      activeSourceType:source.type||"iptv",
      requestedSource:source.type==="videos" ? (source.libraryId||"") : (source.url||""),
      activeLibraryId:source.libraryId||"",
      sourceStatus:"pending",
      sourceRequestedAt:new Date()
    },{merge:true});

    await loadBroadcastSources();

    // The POST only starts the asynchronous switch. Confirm the actual active source
    // before reporting success. This keeps the UI truthful without touching the stream.
    const expectedUrl = source.type === "videos" ? "" : (source.url || "");
    const switched = await waitForSourceSwitch(source.name || "", expectedUrl);

    if (switched) {
      await setDoc(doc(db,"settings","stream"),{
        sourceStatus:"active",
        activeSourceId:id,
        activeSourceName:source.name || "",
        activeSourceType:source.type || "iptv",
        activeLibraryId:source.libraryId || "",
        sourceConfirmedAt:new Date()
      },{merge:true});
    } else {
      await setDoc(doc(db,"settings","stream"),{
        sourceStatus:"pending",
        activeSourceId:id,
        activeSourceName:source.name || "",
        activeSourceType:source.type || "iptv",
        activeLibraryId:source.libraryId || ""
      },{merge:true});
    }
  }catch(error){
    console.error("startHichrawiSource:",error);
    alert("❌ تعذر إرسال المصدر إلى محرك البث.\n"+(error.message||""));
  }
};

window.stopHichrawiSource = async(id)=>{
  try{
    const user = auth.currentUser;
    if(!user){
      alert("❌ سجل الدخول من جديد.");
      return;
    }

    const idToken = await user.getIdToken();
    const response = await fetch(STREAM_ENGINE_URL + "/api/source",{
      method:"POST",
      headers:{
        "Content-Type":"application/json",
        "Authorization":"Bearer " + idToken,
        "X-Firebase-Api-Key": firebaseConfig.apiKey
      },
      body:JSON.stringify({
        type:"stop",
        name:"إيقاف البث",
        requestedAt:Date.now()
      })
    });

    if(!response.ok) throw new Error("HTTP "+response.status);

    await setDoc(doc(db,"settings","stream"),{
      sourceStatus:"stopped",
      stoppedSourceId:id||"",
      stoppedAt:new Date()
    },{merge:true});

    await loadBroadcastSources();
    alert("⏹️ تم إرسال أمر الإيقاف.");
  }catch(error){
    console.error("stopHichrawiSource:",error);
    alert("❌ تعذر إيقاف المصدر.\n"+(error.message||""));
  }
};


async function postFallbackSource(source){
  const user = auth.currentUser;
  if(!user) throw new Error("انتهت جلسة الإدارة");
  const idToken = await user.getIdToken();
  const response = await fetch(STREAM_ENGINE_URL + "/api/fallback", {
    method:"POST",
    headers:{
      "Content-Type":"application/json",
      "Authorization":"Bearer " + idToken,
      "X-Firebase-Api-Key": firebaseConfig.apiKey
    },
    body:JSON.stringify({
      sourceId: source.id || "",
      name: source.name || "",
      type: source.type || "iptv",
      url: source.url || "",
      items: source.items || [],
      libraryId: source.libraryId || "",
      enabled:true,
      updatedAt: Date.now()
    })
  });
  if(!response.ok){
    const t=await response.text();
    throw new Error(t || ("HTTP "+response.status));
  }
  return response.json();
}

window.setHichrawiFallback = async(id)=>{
  try{
    const source = await getSourceDefinitionForSchedule(id);
    showSourceSwitchStatus("pending","🟡 جاري حفظ المصدر الرئيسي الاحتياطي...");
    await postFallbackSource(source);
    showSourceSwitchStatus("success","🟢 تم تعيين «"+source.name+"» كمصدر رئيسي احتياطي. إذا تعطل أي مصدر آخر، Railway يرجع له تلقائياً.");
    await loadBroadcastSources();
  }catch(e){
    console.error("setHichrawiFallback:",e);
    showSourceSwitchStatus("error","🔴 تعذر تعيين المصدر الاحتياطي: "+(e.message||e));
  }
};

window.clearHichrawiFallback = async()=>{
  try{
    const user=auth.currentUser;
    if(!user) throw new Error("انتهت جلسة الإدارة");
    const idToken=await user.getIdToken();
    const r=await fetch(STREAM_ENGINE_URL+"/api/fallback",{
      method:"POST",
      headers:{
        "Content-Type":"application/json",
        "Authorization":"Bearer "+idToken,
        "X-Firebase-Api-Key":firebaseConfig.apiKey
      },
      body:JSON.stringify({enabled:false})
    });
    if(!r.ok) throw new Error(await r.text());
    const label=document.getElementById("fallbackSourceName");
    if(label) label.textContent="غير محدد";
    const select=document.getElementById("fallbackSourceSelect");
    if(select) select.value="";
    showSourceSwitchStatus("success","🟢 تم إلغاء المصدر الرئيسي الاحتياطي.");
    await loadBroadcastSources();
  }catch(e){
    showSourceSwitchStatus("error","🔴 تعذر إلغاء المصدر الاحتياطي: "+(e.message||e));
  }
};

window.deleteHichrawiSource = async(id)=>{
  if(!confirm("حذف مصدر البث؟")) return;

  try{
    const streamRef = doc(db,"settings","stream");
    const streamSnap = await getDoc(streamRef);
    const stream = streamSnap.exists() ? streamSnap.data() : {};

    if(stream.activeSourceId === id){
      await setDoc(streamRef,{
        ...stream,
        activeSourceId:"",
        activeSourceName:"",
        activeSourceType:"",
        sourceStatus:"stopped",
        stoppedAt:new Date()
      },{merge:true});
    }

    await deleteDoc(doc(db,"broadcastSources",id));
    await loadBroadcastSources();
  }catch(error){
    console.error("deleteHichrawiSource:", error);
    alert("❌ تعذر حذف المصدر");
  }
};

// ================= STREAM SOURCE MANAGEMENT =================

// حفظ مصدر IPTV الجديد
// يحفظ الطلب في Firebase فقط؛ لا يوقف FFmpeg ولا يلمس البث الحالي.
window.saveStream=async()=>{
 const input = document.getElementById("streamUrl");
 const url = input?.value.trim() || "";

 if(!url){
  alert("❌ أدخل رابط IPTV أولاً");
  return;
 }

 if(!/^https?:\/\//i.test(url)){
  alert("❌ الرابط يجب أن يبدأ بـ http:// أو https://");
  return;
 }

 try{
  const streamRef = doc(db,"settings","stream");
  const snap = await getDoc(streamRef);
  const current = snap.exists() ? snap.data() : {};

  await setDoc(streamRef,{
   ...current,
   url,
   requestedSource:url,
   sourceStatus:"pending",
   sourceRequestedAt:new Date()
  },{merge:true});

  alert("✅ تم حفظ مصدر IPTV الجديد\n\nالبث الحالي يبقى كما هو إلى أن يصبح المصدر الجديد جاهزاً.");
 }catch(e){
  console.error("saveStream:",e);
  alert("❌ تعذر حفظ مصدر البث");
 }
};

// حفظ معلومات الاتصال
window.saveContact=async()=>{
 await setDoc(doc(db,"contact","info"),{
  phone:document.getElementById("phone")?.value||"",
  whatsapp:document.getElementById("whatsapp")?.value||"",
  address:document.getElementById("address")?.value||"",
  email:document.getElementById("email")?.value||"",
  adminEmail:document.getElementById("adminEmail")?.value||"",
  facebook:document.getElementById("facebook")?.value||"",
  instagram:document.getElementById("instagram")?.value||"",
  tiktok:document.getElementById("tiktok")?.value||"",
  youtube:document.getElementById("youtube")?.value||"",
  telegram:document.getElementById("telegram")?.value||""
 });
 alert("تم حفظ معلومات الاتصال");
};



// ================= FIREBASE SUBSCRIPTIONS =================
function generateSubscriptionCode(){
  const alphabet="ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
  let code="";
  if(window.crypto?.getRandomValues){
    const bytes=new Uint8Array(8); crypto.getRandomValues(bytes);
    for(const b of bytes) code += alphabet[b % alphabet.length];
  }else{
    for(let i=0;i<8;i++) code += String(Math.floor(Math.random()*10));
  }
  return code;
}
async function sha256Hex(value){
  const data=new TextEncoder().encode(value);
  const hash=await crypto.subtle.digest("SHA-256",data);
  return Array.from(new Uint8Array(hash)).map(b=>b.toString(16).padStart(2,"0")).join("");
}
window.generateSubscriptionCodeUI=()=>{ const el=document.getElementById("subscriptionCode"); if(el) el.value=generateSubscriptionCode(); };
window.createSubscription=async()=>{
  const input=document.getElementById("subscriptionCode");
  const code=(input?.value||generateSubscriptionCode()).trim().replace(/[^A-Za-z0-9]/g,"");
  const days=Math.max(1,Number(document.getElementById("subscriptionDays")?.value||30));
  const maxDevices=Math.max(1,Number(document.getElementById("subscriptionDevices")?.value||1));
  if(!/^\d{8}$/.test(code)){ alert("❌ كود الاشتراك يجب أن يكون 8 أرقام بالضبط."); return; }
  try{
    const ref=doc(db,"subscriptions",await sha256Hex(code));
    if((await getDoc(ref)).exists()){ alert("❌ الكود موجود مسبقًا. ولّد كودًا جديدًا."); return; }
    await setDoc(ref,{code,durationDays:days,maxDevices,status:"ready",active:false,deviceIds:[],createdAt:new Date()});
    if(input) input.value=code;
    alert("✅ تم إنشاء الكود: "+code);
    loadSubscriptions();
  }catch(e){ console.error("createSubscription",e); alert("❌ تعذر إنشاء الاشتراك. تحقق من صلاحيات Firestore."); }
};
window.loadSubscriptions=async()=>{
  const table=document.getElementById("subscriptionsTable"); if(!table) return;
  try{
    const snap=await getDocs(collection(db,"subscriptions"));
    table.innerHTML="";
    snap.docs.forEach(item=>{
      const d=item.data()||{}, n=Array.isArray(d.deviceIds)?d.deviceIds.length:0, active=d.active===true;
      table.innerHTML+=`<tr><td><code>${d.code||"—"}</code></td><td>${d.durationDays||"—"} يوم</td><td>${n}/${d.maxDevices||1}</td><td>${d.status||"ready"}</td><td><button onclick="toggleSubscription('${item.id}',${active})">${active?"تعطيل":"تفعيل"}</button> <button onclick="deleteSubscription('${item.id}')">🗑</button></td></tr>`;
    });
    const count=document.getElementById("subscriptionsCount"); if(count) count.textContent=snap.size;
  }catch(e){ console.error("loadSubscriptions",e); table.innerHTML='<tr><td colspan="5">تعذر تحميل الاشتراكات</td></tr>'; }
};
window.toggleSubscription=async(id,current)=>{ try{ await setDoc(doc(db,"subscriptions",id),{active:!current,status:!current?"active":"disabled",updatedAt:new Date()},{merge:true}); loadSubscriptions(); }catch(e){ alert("❌ تعذر تغيير الحالة"); } };
window.deleteSubscription=async(id)=>{ if(!confirm("حذف كود الاشتراك نهائيًا؟")) return; try{ await deleteDoc(doc(db,"subscriptions",id)); loadSubscriptions(); }catch(e){ alert("❌ تعذر الحذف"); } };

// ================= CHANNEL SETTINGS =================
window.loadSettings=async()=>{
 try{
  const snap=await getDoc(doc(db,"settings","general"));
  if(!snap.exists()) return;
  const d=snap.data()||{};
  const name=document.getElementById("channelName");
  const description=document.getElementById("channelDescription");
  if(name) name.value=d.name||"";
  if(description) description.value=d.description||"";
 }catch(e){
  console.error("loadSettings:",e);
 }
};

window.saveSettings=async()=>{
 try{
  await setDoc(doc(db,"settings","general"),{
   name:document.getElementById("channelName")?.value.trim() || "",
   description:document.getElementById("channelDescription")?.value.trim() || "",
   updatedAt:new Date()
  },{merge:true});
  const msg=document.getElementById("settingsMessage");
  if(msg) msg.textContent="✅ تم حفظ الإعدادات";
  else alert("تم حفظ إعدادات القناة");
 }catch(e){
  console.error("saveSettings:",e);
  alert("❌ تعذر حفظ إعدادات القناة: "+(e.message||e));
 }
};
// ================= VIDEO LIBRARY + PLAYLISTS =================

function setVideoManagerMessage(message, error=false){
    const el = document.getElementById("videoManagerMessage");
    if(!el) return;
    el.textContent = message;
    el.style.color = error ? "#ff7675" : "#aaa";
}

window.openAddVideo = () => {
    const m = document.getElementById("videoModal");
    if(m) m.style.display = "flex";
};

window.closeVideo = () => {
    const m = document.getElementById("videoModal");
    if(m) m.style.display = "none";
};

window.uploadVideoFile = async () => {
    const fileInput = document.getElementById("videoFile");
    const titleInput = document.getElementById("videoTitle");
    const file = fileInput?.files?.[0];

    if(!file){
        setVideoManagerMessage("اختر فيديو أولاً.", true);
        return;
    }

    const title = (titleInput?.value || file.name.replace(/\.[^.]+$/, "")).trim();
    if(!title){
        setVideoManagerMessage("اكتب اسم الفيديو.", true);
        return;
    }

    try{
        setVideoManagerMessage("⏳ جاري رفع الفيديو إلى Railway... لا تغلق الصفحة.");

        const safeName = file.name.replace(/[^\w\u0600-\u06FF.\- ]/g, "_");
        const uploadUrl = STREAM_ENGINE_URL + "/api/upload-video?filename=" + encodeURIComponent(safeName);
        const user = auth.currentUser;
        if(!user) throw new Error("انتهت جلسة الإدارة. سجل الدخول من جديد.");
        const idToken = await user.getIdToken();

        const response = await fetch(uploadUrl, {
            method: "POST",
            headers: {
                "Content-Type": file.type || "application/octet-stream",
                "Authorization": "Bearer " + idToken,
                "X-Firebase-Api-Key": firebaseConfig.apiKey
            },
            body: file
        });

        const raw = await response.text();
        let result = {};
        try{ result = raw ? JSON.parse(raw) : {}; }catch(_){ }

        if(!response.ok || !result.ok){
            throw new Error(result.error || raw || ("HTTP " + response.status));
        }

        const serverPath = result.serverPath || result.path || result.filename;
        const url = STREAM_ENGINE_URL + "/videos/" + encodeURIComponent(serverPath).replace(/%2F/g, "/");

        await addDoc(collection(db, "videos"), {
            title,
            url,
            serverPath,
            size: file.size,
            contentType: file.type || "",
            storagePath: "",
            createdAt: new Date()
        });

        fileInput.value = "";
        if(titleInput) titleInput.value = "";

        setVideoManagerMessage("✅ تم رفع الفيديو إلى Railway وإضافته للمكتبة.");
        await loadVideos();
        await loadPlaylists();
        await loadServerVideos();
    }catch(error){
        console.error("uploadVideoFile:", error);
        setVideoManagerMessage("❌ فشل رفع الفيديو إلى Railway: " + (error.message || error), true);
    }
};

window.saveVideo = async () => {
    const title = (document.getElementById("videoTitle")?.value || "").trim();
    const url = (document.getElementById("videoUrl")?.value || "").trim();

    if(!title || !url){
        setVideoManagerMessage("أدخل عنوان الفيديو والرابط.", true);
        return;
    }

    if(!/^https?:\/\//i.test(url)){
        setVideoManagerMessage("الرابط يجب أن يبدأ بـ http:// أو https://", true);
        return;
    }

    try{
        await addDoc(collection(db,"videos"),{
            title,
            url,
            storagePath:"",
            createdAt:new Date()
        });

        document.getElementById("videoTitle").value = "";
        document.getElementById("videoUrl").value = "";

        setVideoManagerMessage("✅ تمت إضافة الفيديو بالرابط.");
        await loadVideos();
        await loadPlaylists();
    }catch(error){
        console.error("saveVideo:", error);
        setVideoManagerMessage("❌ تعذر حفظ الفيديو.", true);
    }
};

async function loadVideos(){
    const list = document.getElementById("videoLibraryList");
    const videoSelect = document.getElementById("playlistVideoSelect");
    if(!list) return;

    try{
        const snap = await getDocs(collection(db,"videos"));

        if(snap.empty){
            list.innerHTML = '<div class="library-empty">لا توجد فيديوهات</div>';
            if(videoSelect) videoSelect.innerHTML = '<option value="">لا توجد فيديوهات</option>';
            return;
        }

        list.innerHTML = "";
        if(videoSelect) videoSelect.innerHTML = "";

        snap.forEach(item=>{
            const d = item.data();
            const title = String(d.title || "فيديو").replace(/</g,"&lt;").replace(/>/g,"&gt;");
            const url = String(d.url || "").replace(/</g,"&lt;").replace(/>/g,"&gt;");

            list.innerHTML += `
              <div class="video-row">
                <span>🎬</span>
                <div>
                  <div class="video-row-title">${title}</div>
                  <div class="video-row-url">${url}</div>
                </div>
                <button class="btn delete" onclick="deleteVideo('${item.id}')">🗑️</button>
              </div>`;

            if(videoSelect){
                const option = document.createElement("option");
                option.value = item.id;
                option.textContent = d.title || "فيديو";
                videoSelect.appendChild(option);
            }
        });
    }catch(error){
        console.error("loadVideos:", error);
        list.innerHTML = '<div class="library-empty">تعذر تحميل الفيديوهات</div>';
    }
}

window.deleteVideo = async(id)=>{
    if(!confirm("حذف الفيديو؟")) return;

    try{
        const videoRef = doc(db,"videos",id);
        const snap = await getDoc(videoRef);

        if(snap.exists()){
            const d = snap.data();
            if(d.storagePath){
                try{
                    await deleteObject(ref(storage, d.storagePath));
                }catch(storageError){
                    console.warn("Storage delete:", storageError);
                }
            }
        }

        await deleteDoc(videoRef);
        await loadVideos();
        await loadPlaylists();
        setVideoManagerMessage("🗑️ تم حذف الفيديو.");
    }catch(error){
        console.error("deleteVideo:", error);
        setVideoManagerMessage("❌ تعذر حذف الفيديو.", true);
    }
};

// ================= SERVER VIDEO LIBRARY =================
// Reads the server-side video library through the future /api/videos endpoint.
// No Railway/FFmpeg changes are made by admin.js in this stage.

let serverVideosCache = [];

window.loadServerVideos = async()=>{
    const list = document.getElementById("serverVideoList");
    const status = document.getElementById("serverVideoStatus");
    if(!list) return;

    list.innerHTML = '<div class="library-empty">⏳ جاري قراءة مكتبة السيرفر...</div>';
    if(status) status.textContent = "الاتصال بـ /api/videos ...";

    try{
        const response = await fetch(STREAM_ENGINE_URL + "/api/videos", {
            method:"GET",
            headers:{ "Accept":"application/json" },
            cache:"no-store"
        });

        if(!response.ok){
            throw new Error("HTTP " + response.status);
        }

        const data = await response.json();
        serverVideosCache = Array.isArray(data) ? data :
                            (Array.isArray(data.videos) ? data.videos : []);

        renderServerVideos();

        if(status){
            status.textContent = serverVideosCache.length
                ? `✅ تم العثور على ${serverVideosCache.length} فيديو في السيرفر.`
                : "المجلد موجود لكن لا توجد فيديوهات.";
        }
    }catch(error){
        console.error("loadServerVideos:", error);
        serverVideosCache = [];
        list.innerHTML = `
          <div class="library-empty">
            ⚠️ واجهة السيرفر /api/videos غير مربوطة بعد.
            <br>لن نغيّر البث الحالي. سيتم ربطها في مرحلة محرك البث.
          </div>`;
        if(status) status.textContent = "لم يتم الاتصال بمكتبة السيرفر.";
    }
};

function renderServerVideos(){
    const list=document.getElementById("serverVideoList");
    if(!list) return;

    const q=(document.getElementById("serverVideoSearch")?.value || "").trim().toLowerCase();
    const filtered=serverVideosCache.filter(v=>{
        const name=String(v.name || v.title || v.filename || "").toLowerCase();
        return !q || name.includes(q);
    });

    if(!filtered.length){
        list.innerHTML='<div class="library-empty">لا توجد نتائج.</div>';
        return;
    }

    list.innerHTML="";
    filtered.forEach((v,index)=>{
        const name=String(v.name || v.title || v.filename || "فيديو");
        const path=String(v.path || v.url || v.filename || "");
        const safeName=name.replace(/</g,"&lt;").replace(/>/g,"&gt;");
        const safePath=path.replace(/</g,"&lt;").replace(/>/g,"&gt;");

        list.innerHTML += `
          <div class="video-row">
            <span>📁</span>
            <div>
              <div class="video-row-title">${safeName}</div>
              <div class="video-row-url">${safePath}</div>
            </div>
            <button class="btn add" onclick="addServerVideoToLibrary(${index})">
              ➕ إضافة
            </button>
          </div>`;
    });
}

window.filterServerVideos=()=>renderServerVideos();

window.addServerVideoToLibrary=async(index)=>{
    const v=serverVideosCache[index];
    if(!v){
        setVideoManagerMessage("الفيديو غير موجود في قائمة السيرفر.", true);
        return;
    }

    const name=String(v.name || v.title || v.filename || "فيديو");
    const serverPath=String(v.path || v.url || v.filename || "");

    if(!serverPath){
        setVideoManagerMessage("مسار الفيديو غير موجود.", true);
        return;
    }

    try{
        // Avoid duplicates by serverPath.
        const snap=await getDocs(collection(db,"videos"));
        let exists=false;
        snap.forEach(item=>{
            if(item.data().serverPath === serverPath) exists=true;
        });

        if(exists){
            setVideoManagerMessage("الفيديو موجود بالفعل في المكتبة.");
            return;
        }

        await addDoc(collection(db,"videos"),{
            title:name,
            url:serverPath.startsWith("http") ? serverPath : `/videos/${encodeURIComponent(serverPath.split("/").pop())}`,
            serverPath,
            sourceType:"server",
            storagePath:"",
            createdAt:new Date()
        });

        await loadVideos();
        await loadPlaylists();
        setVideoManagerMessage("✅ تمت إضافة فيديو السيرفر إلى المكتبة.");
    }catch(error){
        console.error("addServerVideoToLibrary:",error);
        setVideoManagerMessage("❌ تعذر إضافة فيديو السيرفر.",true);
    }
};

// ================= PLAYLIST MANAGEMENT =================

window.createPlaylist = async()=>{
    const input = document.getElementById("playlistName");
    const name = (input?.value || "").trim();

    if(!name){
        setVideoManagerMessage("اكتب اسم قائمة التشغيل.", true);
        return;
    }

    try{
        await addDoc(collection(db,"playlists"),{
            name,
            videoIds:[],
            createdAt:new Date(),
            updatedAt:new Date()
        });

        input.value = "";
        await loadPlaylists();
        setVideoManagerMessage("✅ تم إنشاء قائمة التشغيل.");
    }catch(error){
        console.error("createPlaylist:", error);
        setVideoManagerMessage("❌ تعذر إنشاء القائمة.", true);
    }
};

async function loadPlaylists(){
    const list = document.getElementById("playlistList");
    const playlistSelect = document.getElementById("playlistSelect");
    const sourceSelect = document.getElementById("videosLibrary");

    if(!list) return;

    try{
        const snap = await getDocs(collection(db,"playlists"));

        if(playlistSelect) playlistSelect.innerHTML = "";
        if(sourceSelect) sourceSelect.innerHTML = "";

        if(snap.empty){
            list.innerHTML = '<div class="library-empty">لا توجد قوائم</div>';
            if(playlistSelect) playlistSelect.innerHTML = '<option value="">لا توجد قوائم</option>';
            if(sourceSelect) sourceSelect.innerHTML = '<option value="">🎬 أنشئ قائمة أولاً</option>';
            return;
        }

        list.innerHTML = "";

        for(const item of snap.docs){
            const d = item.data();
            const ids = Array.isArray(d.videoIds) ? d.videoIds : [];

            if(playlistSelect){
                const option = document.createElement("option");
                option.value = item.id;
                option.textContent = d.name || "قائمة";
                playlistSelect.appendChild(option);
            }

            if(sourceSelect){
                const option = document.createElement("option");
                option.value = item.id;
                option.textContent = "🎬 " + (d.name || "قائمة");
                sourceSelect.appendChild(option);
            }

            let titles = [];
            for(const id of ids){
                try{
                    const vSnap = await getDoc(doc(db,"videos",id));
                    if(vSnap.exists()) titles.push(vSnap.data().title || "فيديو");
                }catch(_){}
            }

            const safeName = String(d.name || "قائمة").replace(/</g,"&lt;").replace(/>/g,"&gt;");

            list.innerHTML += `
              <div class="playlist-row" data-playlist-id="${item.id}">
                <div class="playlist-head">
                  <strong>📋 ${safeName}</strong>
                  <span class="source-badge">${ids.length} فيديو</span>
                </div>
                <div class="playlist-videos">
                  ${titles.length ? titles.map((t,i)=>(i+1)+". "+String(t).replace(/</g,"&lt;")).join("<br>") : "القائمة فارغة"}
                </div>
                <div class="small-actions">
                  <button class="btn add" onclick="selectPlaylistAsSource('${item.id}')">📡 جعلها مصدر البث</button>
                  <button class="btn delete" onclick="deletePlaylist('${item.id}')">🗑️ حذف القائمة</button>
                </div>
              </div>`;
        }
    }catch(error){
        console.error("loadPlaylists:", error);
        list.innerHTML = '<div class="library-empty">تعذر تحميل القوائم</div>';
    }
}

window.addVideoToPlaylist = async()=>{
    const playlistId = document.getElementById("playlistSelect")?.value;
    const videoId = document.getElementById("playlistVideoSelect")?.value;

    if(!playlistId || !videoId){
        setVideoManagerMessage("اختر قائمة وفيديو أولاً.", true);
        return;
    }

    try{
        const playlistRef = doc(db,"playlists",playlistId);
        const snap = await getDoc(playlistRef);

        if(!snap.exists()){
            setVideoManagerMessage("القائمة غير موجودة.", true);
            return;
        }

        const d = snap.data();
        const ids = Array.isArray(d.videoIds) ? [...d.videoIds] : [];

        if(!ids.includes(videoId)) ids.push(videoId);

        await updateDoc(playlistRef,{videoIds:ids,updatedAt:new Date()});
        await loadPlaylists();
        setVideoManagerMessage("✅ تمت إضافة الفيديو إلى القائمة.");
    }catch(error){
        console.error("addVideoToPlaylist:", error);
        setVideoManagerMessage("❌ تعذر تعديل القائمة.", true);
    }
};

window.selectPlaylistAsSource = async(playlistId)=>{
    const sourceSelect = document.getElementById("videosLibrary");
    if(sourceSelect) sourceSelect.value = playlistId;

    const typeSelect = document.getElementById("sourceType");
    if(typeSelect){
        typeSelect.value = "videos";
        if(typeof updateSourceForm === "function") updateSourceForm();
    }

    const playlist = await getDoc(doc(db,"playlists",playlistId));
    if(playlist.exists()){
        const nameInput = document.getElementById("sourceName");
        if(nameInput) nameInput.value = playlist.data().name || "فيديوهاتي";
    }

    document.getElementById("sourcesSection")?.scrollIntoView({behavior:"smooth"});
    setVideoManagerMessage("تم اختيار القائمة كمصدر. احفظ المصدر من قسم 🎛️ مصادر البث.");
};

window.deletePlaylist = async(id)=>{
    if(!confirm("حذف قائمة التشغيل؟ الفيديوهات نفسها لن تُحذف.")) return;

    try{
        await deleteDoc(doc(db,"playlists",id));
        await loadPlaylists();
        setVideoManagerMessage("🗑️ تم حذف قائمة التشغيل.");
    }catch(error){
        console.error("deletePlaylist:", error);
        setVideoManagerMessage("❌ تعذر حذف القائمة.", true);
    }
};

// ================= STREAM LOGO MANAGEMENT =================


async function applyLogoUrlToEngine(url){
  const clean = String(url||"").trim();
  if(!clean) return;
  const user=auth.currentUser;
  if(!user) throw new Error("انتهت جلسة الإدارة");
  const idToken=await user.getIdToken();
  const r=await fetch(STREAM_ENGINE_URL+"/api/logo",{
    method:"POST",
    headers:{
      "Content-Type":"application/json",
      "Authorization":"Bearer "+idToken,
      "X-Firebase-Api-Key":firebaseConfig.apiKey
    },
    body:JSON.stringify({url:clean})
  });
  if(!r.ok){
    const t=await r.text();
    throw new Error(t||("HTTP "+r.status));
  }
  return r.json();
}

window.uploadStreamLogo = async()=>{
  try{
    const file=document.getElementById("streamLogoFile")?.files?.[0];
    if(!file){ alert("اختر صورة الشعار أولاً"); return; }
    if(!/^image\//i.test(file.type)){ alert("الملف يجب أن يكون صورة"); return; }
    if(file.size > 5*1024*1024){ alert("حجم الشعار يجب ألا يتجاوز 5MB"); return; }

    const safeName = file.name.replace(/[^a-zA-Z0-9._-]/g,"_");
    const path = `stream-logo/${Date.now()}-${safeName}`;
    const storageRef = ref(storage, path);
    await uploadBytes(storageRef,file,{contentType:file.type});
    const url = await getDownloadURL(storageRef);

    await setDoc(doc(db,"settings","stream"),{logo:url,logoUpdatedAt:new Date()},{merge:true});
    await applyLogoUrlToEngine(url);

    const input=document.getElementById("streamLogo");
    const preview=document.getElementById("streamLogoPreview");
    if(input) input.value=url;
    if(preview) preview.src=url;
    alert("🟢 تم رفع الشعار وحفظه وتطبيقه على البث.");
  }catch(e){
    console.error("uploadStreamLogo:",e);
    alert("🔴 تعذر رفع/تطبيق الشعار.\n"+(e.message||e));
  }
};

// حفظ شعار البث
window.saveStreamLogo = async()=>{

 const logo = document.getElementById("streamLogo")?.value || "";

 await setDoc(doc(db,"settings","stream"),{
    logo: logo
 },{merge:true});

 if(logo){
   await applyLogoUrlToEngine(logo);
 }

 alert("تم حفظ شعار البث وتطبيقه على البث.");

 const preview=document.getElementById("streamLogoPreview");
 if(preview && logo){
    preview.src=logo;
 }

};


// حذف شعار البث
window.removeStreamLogo = async()=>{

 await setDoc(doc(db,"settings","stream"),{
    logo:""
 },{merge:true});

 const preview=document.getElementById("streamLogoPreview");
 if(preview){
    preview.src="";
 }

 const input=document.getElementById("streamLogo");
 if(input){
    input.value="";
 }

 alert("تم حذف الشعار");

};


// تحميل إعدادات البث (المصدر + الشعار)
async function loadStreamSettings(){

 const snap = await getDoc(doc(db,"settings","stream"));

 if(!snap.exists()) return;

 const data = snap.data();

 const streamInput = document.getElementById("streamUrl");
 if(streamInput) streamInput.value = data.url || "";

 const logoInput = document.getElementById("streamLogo");
 const preview = document.getElementById("streamLogoPreview");

 if(logoInput) logoInput.value = data.logo || "";

 if(preview) preview.src = data.logo || "";
}

// توافق مع أي كود قديم يستدعي هذه الدالة
async function loadStreamLogo(){
 await loadStreamSettings();
}



// ================= VIEWER ANALYTICS =================
async function refreshViewerStats(){
  try{
    const r = await fetch(STREAM_ENGINE_URL + "/api/viewers?ts=" + Date.now(), {cache:"no-store"});
    if(!r.ok) throw new Error("HTTP " + r.status);
    const d = await r.json();
    const live = document.getElementById("liveViewers");
    const today = document.getElementById("todayViews");
    const total = document.getElementById("totalViews");
    if(live) live.textContent = Number(d.live_viewers || 0).toLocaleString("ar-TN");
    if(today) today.textContent = Number(d.today_views || 0).toLocaleString("ar-TN");
    if(total) total.textContent = Number(d.total_views || 0).toLocaleString("ar-TN");
  }catch(e){
    console.warn("viewer stats", e);
  }
}
window.refreshViewerStats = refreshViewerStats;

// ================= LIVE BROADCAST DASHBOARD =================
function formatDashTime(value){
  if(!value) return "—";
  const d = new Date(typeof value === "number" ? value : value);
  if(Number.isNaN(d.getTime())) return "—";
  return d.toLocaleString("ar-TN", {
    day:"2-digit", month:"2-digit", year:"numeric",
    hour:"2-digit", minute:"2-digit", second:"2-digit"
  });
}

function dashSet(id, value, cls=""){
  const el = document.getElementById(id);
  if(!el) return;
  el.textContent = value;
  el.className = "stat-value " + cls;
}

async function refreshLiveDashboard(){
  const statusEl = document.getElementById("liveDashboardStatus");
  if(statusEl) statusEl.textContent = "🟡 جاري قراءة حالة البث...";

  try{
    const r = await fetch(STREAM_ENGINE_URL + "/api/status?ts=" + Date.now(), {
      cache:"no-store"
    });
    if(!r.ok) throw new Error("HTTP " + r.status);

    const s = await r.json();
    const state = String(s.status || "unknown");

    let label = "⚪ غير معروف";
    let cls = "warn";

    if(state === "running"){
      label = s.switch_failed
        ? "🟠 يعمل — آخر تبديل فشل، والمصدر الحالي مستمر"
        : "🟢 يعمل";
      cls = s.switch_failed ? "warn" : "ok";
    }else if(state === "switching"){
      label = "🟡 جاري تحضير مصدر جديد";
    }else if(state === "switched"){
      label = "🟢 تم التبديل بنجاح";
      cls = "ok";
    }else if(state === "error" || state === "failed"){
      label = "🔴 خطأ";
      cls = "bad";
    }

    if(statusEl){
      statusEl.textContent = label + (s.message ? " — " + s.message : "");
      statusEl.className = cls;
    }

    dashSet("dashSource", s.source_name || "—");
    dashSet("dashType", s.source_type || "—");
    dashSet("dashEngine", label, cls);
    dashSet("dashSwitched", formatDashTime(s.switched_at || s.requested_at));
  }catch(err){
    console.error("refreshLiveDashboard:", err);
    if(statusEl){
      statusEl.textContent = "🔴 تعذر الاتصال بمحرك البث";
      statusEl.className = "bad";
    }
    dashSet("dashSource", "—");
    dashSet("dashType", "—");
    dashSet("dashEngine", "غير متصل", "bad");
    dashSet("dashSwitched", "—");
  }
}

window.refreshLiveDashboard = refreshLiveDashboard;

document.addEventListener("DOMContentLoaded", ()=>{
  refreshLiveDashboard();
  // Lightweight polling; does not restart or alter the stream.
  setInterval(refreshLiveDashboard, 10000);
});


// ================= BROADCAST SCHEDULE UI + RAILWAY =================
const HICHRAWI_SCHEDULE_KEY = "hichrawi_tv_broadcast_schedule_v2";
function getLocalSchedule(){try{return JSON.parse(localStorage.getItem(HICHRAWI_SCHEDULE_KEY)||"[]")}catch(e){return[]}}
function saveLocalSchedule(items){localStorage.setItem(HICHRAWI_SCHEDULE_KEY,JSON.stringify(items))}

async function scheduleAuthHeaders(){
  const user=auth.currentUser;
  if(!user) throw new Error("جلسة الإدارة منتهية");
  const idToken=await user.getIdToken();
  return {"Content-Type":"application/json","Authorization":"Bearer "+idToken,"X-Firebase-Api-Key":firebaseConfig.apiKey};
}

function browserTimezoneOffsetMinutes(){
  // HICHRAWI-TV schedule uses Tunisia time (UTC+2).
  // Keep the schedule independent from the PC/browser timezone setting.
  return 120;
}

function scheduleSortKey(item){
  return String(item.date || "9999-12-31") + " " + String(item.time || "99:99");
}

function formatScheduleDate(value){
  if(!value) return "—";
  const parts=String(value).split("-");
  if(parts.length===3) return parts[2]+"/"+parts[1]+"/"+parts[0];
  return value;
}

async function fetchServerSchedule(){
  const r=await fetch(STREAM_ENGINE_URL+"/api/schedule?ts="+Date.now(),{cache:"no-store"});
  if(!r.ok) throw new Error("HTTP "+r.status);
  const data=await r.json();
  const items=Array.isArray(data.items)?data.items:[];

  saveLocalSchedule(items.map(x=>({
    date:x.date||"",
    time:x.time||"",
    name:x.name||x.source?.name||"مصدر",
    type:x.type||x.source?.type||"source",
    sourceId:x.sourceId||"",
    enabled:x.enabled!==false
  })));
  return data;
}

async function syncScheduleToRailway(items, enabled=true){
  const headers=await scheduleAuthHeaders();
  const payload={
    enabled,
    timezone_offset_minutes:browserTimezoneOffsetMinutes(),
    items
  };
  const r=await fetch(STREAM_ENGINE_URL+"/api/schedule",{
    method:"POST",headers,body:JSON.stringify(payload)
  });
  if(!r.ok){const t=await r.text();throw new Error(t||("HTTP "+r.status));}
  return r.json();
}

async function getSourceDefinitionForSchedule(id){
  const snap=await getDoc(doc(db,"broadcastSources",id));
  if(!snap.exists()) throw new Error("المصدر غير موجود");
  const source=snap.data();
  let items=[];

  if(source.type==="videos"){
    if(!source.libraryId) throw new Error("قائمة فيديوهات غير محددة للمصدر");
    const pSnap=await getDoc(doc(db,"playlists",source.libraryId));
    if(!pSnap.exists()) throw new Error("قائمة التشغيل غير موجودة");
    const ids=Array.isArray(pSnap.data().videoIds)?pSnap.data().videoIds:[];
    for(const videoId of ids){
      const vSnap=await getDoc(doc(db,"videos",videoId));
      if(!vSnap.exists()) continue;
      const v=vSnap.data();
      if(v.serverPath) items.push("/videos/"+encodeURIComponent(v.serverPath).replace(/%2F/g,"/"));
      else if(v.url) items.push(v.url);
    }
    if(!items.length) throw new Error("قائمة الفيديوهات فارغة");
  }

  return {
    id,
    name:source.name||"مصدر",
    type:source.type||"iptv",
    url:source.type==="videos"?"":(source.url||""),
    items,
    libraryId:source.libraryId||""
  };
}

async function loadScheduleSources(){
  const select=document.getElementById("scheduleSource"); if(!select)return;
  const previous=select.value;
  select.innerHTML='<option value="">اختر المصدر</option>';
  const snap=await getDocs(collection(db,"broadcastSources"));
  snap.forEach(item=>{
    const d=item.data();
    const o=document.createElement("option");
    o.value=item.id;
    o.textContent=(d.name||item.id)+" — "+(getHichrawiSourceTypeLabel(d.type)||d.type||"");
    select.appendChild(o);
  });
  if(previous)select.value=previous;
}

function renderBroadcastSchedule(items=getLocalSchedule()){
  const body=document.getElementById("scheduleTableBody"); if(!body)return;
  const sorted=[...items].sort((a,b)=>scheduleSortKey(a).localeCompare(scheduleSortKey(b)));

  if(!sorted.length){
    body.innerHTML='<tr><td colspan="6" class="schedule-empty">لا توجد برامج في الجدول بعد.</td></tr>';
    return;
  }

  body.innerHTML="";
  sorted.forEach((item,index)=>{
    const tr=document.createElement("tr");
    tr.innerHTML=`
      <td>${formatScheduleDate(item.date)}</td>
      <td>${item.time||"—"}</td>
      <td>${item.name||item.source?.name||"مصدر"}</td>
      <td>${item.type||item.source?.type||"source"}</td>
      <td>${item.enabled!==false?"🟢 مفعّل":"⚪ متوقف"}</td>
      <td style="min-width:100px!important;width:100px!important;white-space:nowrap!important;overflow:visible!important;writing-mode:horizontal-tb!important;"><button class="btn delete" type="button" onclick="removeBroadcastSchedule(${index})" style="display:inline-block!important;min-width:80px!important;width:auto!important;white-space:nowrap!important;overflow:visible!important;writing-mode:horizontal-tb!important;word-break:keep-all!important;overflow-wrap:normal!important;">🗑️ حذف</button></td>`;
    body.appendChild(tr);
  });
}

async function refreshBroadcastSchedule(){
  const msg=document.getElementById("scheduleMessage");
  try{
    const data=await fetchServerSchedule();
    renderBroadcastSchedule(data.items||[]);
    if(msg)msg.textContent=data.enabled
      ?"🟢 الجدول مربوط بـRailway ويعمل من السيرفر حتى بعد إغلاق الحاسوب."
      :"⚪ الجدول محفوظ لكن التشغيل التلقائي متوقف.";
  }catch(e){
    console.warn("server schedule",e);
    renderBroadcastSchedule();
    if(msg)msg.textContent="⚠️ تعذر قراءة جدول Railway — المعروض محلياً فقط.";
  }
}

async function addBroadcastSchedule(){
  const date=document.getElementById("scheduleDate")?.value;
  const time=document.getElementById("scheduleTime")?.value;
  const sel=document.getElementById("scheduleSource");
  const msg=document.getElementById("scheduleMessage");

  if(!date||!time||!sel?.value){
    if(msg)msg.textContent="⚠️ اختر التاريخ والوقت والمصدر أولاً.";
    return;
  }

  try{
    if(msg)msg.textContent="🟡 جاري إرسال البرنامج إلى Railway...";

    const source=await getSourceDefinitionForSchedule(sel.value);
    const server=await fetchServerSchedule().catch(()=>({items:[]}));
    const items=Array.isArray(server.items)?server.items:[];

    // نفس التاريخ والوقت = تعديل الموعد القديم بدل إنشاء تكرار.
    const clean=items.filter(x=>!(String(x.date||"")===date && String(x.time||"")===time));

    clean.push({
      date,
      time,
      name:source.name,
      type:source.type,
      sourceId:source.id,
      enabled:true,
      source
    });

    await syncScheduleToRailway(clean,true);

    saveLocalSchedule(clean.map(x=>({
      date:x.date||"",
      time:x.time||"",
      name:x.name,
      type:x.type,
      sourceId:x.sourceId,
      enabled:x.enabled!==false
    })));

    renderBroadcastSchedule(clean);

    if(msg)msg.textContent="🟢 تمت إضافة البرنامج بالتاريخ والوقت وربطه بـRailway.";
  }catch(e){
    console.error("addBroadcastSchedule",e);
    if(msg)msg.textContent="🔴 فشلت إضافة البرنامج: "+(e.message||e);
  }
}

async function removeBroadcastSchedule(index){
  try{
    const server=await fetchServerSchedule();
    const items=Array.isArray(server.items)?server.items:[];
    const sorted=[...items].sort((a,b)=>scheduleSortKey(a).localeCompare(scheduleSortKey(b)));

    sorted.splice(index,1);

    await syncScheduleToRailway(sorted,true);

    saveLocalSchedule(sorted.map(x=>({
      date:x.date||"",
      time:x.time||"",
      name:x.name,
      type:x.type,
      sourceId:x.sourceId,
      enabled:x.enabled!==false
    })));

    renderBroadcastSchedule(sorted);
  }catch(e){
    alert("❌ تعذر حذف البرنامج من Railway.\n"+(e.message||e));
  }
}

async function clearBroadcastSchedule(){
  if(!confirm("هل تريد مسح جدول البث من Railway؟"))return;
  try{
    await syncScheduleToRailway([],false);
    localStorage.removeItem(HICHRAWI_SCHEDULE_KEY);
    renderBroadcastSchedule([]);
    const msg=document.getElementById("scheduleMessage");
    if(msg)msg.textContent="🟢 تم مسح جدول البث من Railway.";
  }catch(e){
    alert("❌ تعذر مسح جدول Railway.\n"+(e.message||e));
  }
}

window.addBroadcastSchedule=addBroadcastSchedule;
window.removeBroadcastSchedule=removeBroadcastSchedule;
window.clearBroadcastSchedule=clearBroadcastSchedule;
window.refreshBroadcastSchedule=refreshBroadcastSchedule;

document.addEventListener("DOMContentLoaded",async()=>{
  await loadScheduleSources().catch(()=>{});
  await refreshBroadcastSchedule();
  setTimeout(loadScheduleSources,1500);
});

// ================= ANNOUNCEMENT / BREAKING NEWS =================
window.loadAnnouncement = async function(){
  try{
    const snap = await getDoc(doc(db,"settings","announcement"));
    const d = snap.exists() ? snap.data() : {};
    const ids = ["announcementText","announcementType","announcementSpeed","announcementBg","announcementColor","announcementSize"];
    const vals = [d.text||"", d.type||"breaking", String(d.speed||18), d.bgColor||"#e00000", d.textColor||"#ffffff", d.fontSize||"20px"];
    ids.forEach((id,i)=>{ const el=document.getElementById(id); if(el) el.value=vals[i]; });
    previewAnnouncement();
  }catch(e){ console.error("loadAnnouncement:",e); }
};

window.saveAnnouncement = async function(enabled){
  const data={
    text:document.getElementById("announcementText")?.value.trim()||"",
    type:document.getElementById("announcementType")?.value||"breaking",
    speed:Number(document.getElementById("announcementSpeed")?.value||18),
    bgColor:document.getElementById("announcementBg")?.value||"#e00000",
    textColor:document.getElementById("announcementColor")?.value||"#ffffff",
    fontSize:document.getElementById("announcementSize")?.value||"20px",
    enabled:Boolean(enabled),
    updatedAt:new Date()
  };

  if(enabled && !data.text){
    alert("❌ اكتب نص الإعلان أولاً");
    return;
  }

  const msg=document.getElementById("announcementMessage");
  const setStatus=(text)=>{
    if(msg) msg.textContent=text;
  };

  try{
    const user=auth.currentUser;
    if(!user) throw new Error("انتهت جلسة الإدارة. سجل الدخول من جديد.");

    setStatus(enabled
      ? "🟡 جاري إرسال الإعلان إلى البث..."
      : "🟡 جاري إرسال أمر إيقاف الإعلان إلى البث...");

    const idToken=await user.getIdToken();
    const apiUrl=STREAM_ENGINE_URL + "/api/announcement";

    const payload={
      enabled:data.enabled,
      text:data.text,
      type:data.type,
      speed:data.speed,
      bgColor:data.bgColor,
      textColor:data.textColor,
      fontSize:data.fontSize,
      bg:data.bgColor,
      fg:data.textColor,
      font_size:parseInt(String(data.fontSize).replace("px",""),10)||20
    };

    setStatus(enabled
      ? "🟡 تم إرسال الطلب، جاري تطبيق الإعلان على البث..."
      : "🟡 تم إرسال أمر الإيقاف، جاري تطبيقه على البث...");

    const response=await fetch(apiUrl,{
      method:"POST",
      mode:"cors",
      headers:{
        "Content-Type":"application/json",
        "Authorization":"Bearer "+idToken,
        "X-Firebase-Api-Key":firebaseConfig.apiKey,
        "Accept":"application/json"
      },
      body:JSON.stringify(payload),
      cache:"no-store"
    });

    const responseText=await response.text();
    let result=null;
    try{ result=responseText ? JSON.parse(responseText) : null; }catch(_){}

    if(!response.ok){
      throw new Error(
        (result && (result.error || result.message)) ||
        responseText ||
        ("HTTP "+response.status)
      );
    }

    await setDoc(doc(db,"settings","announcement"),data,{merge:true});

    setStatus(enabled
      ? "🟢 نجحت العملية — تم إرسال الإعلان وتطبيقه على البث."
      : "🟢 نجحت العملية — تم إيقاف الإعلان من البث.");

    previewAnnouncement();
    console.log("[ANNOUNCEMENT] Railway response:",result);

    setTimeout(()=>{
      if(msg){
        msg.textContent=enabled
          ? "🟢 الإعلان مفعّل على البث."
          : "⏹️ الإعلان متوقف.";
      }
    },5000);

  }catch(e){
    console.error("saveAnnouncement:",e);
    setStatus(enabled
      ? "🔴 فشلت العملية — الإعلان لم يُطبّق على البث."
      : "🔴 فشلت العملية — لم يتم إيقاف الإعلان من البث.");
    alert("❌ فشلت العملية\n\n"+(e?.message||e));
  }
};

window.refreshAnnouncementFromEngine = async function(){
  try{
    const r = await fetch(STREAM_ENGINE_URL + "/api/announcement?ts=" + Date.now(), {cache:"no-store"});
    if(!r.ok) return null;
    const state = await r.json();
    const ids = ["announcementText","announcementType","announcementSpeed","announcementBg","announcementColor","announcementSize"];
    const vals = [state.text||"", state.type||"breaking", String(state.speed||18), state.bgColor||"#e00000", state.textColor||"#ffffff", state.fontSize||"20px"];
    ids.forEach((id,i)=>{ const el=document.getElementById(id); if(el) el.value=vals[i]; });
    previewAnnouncement();
    return state;
  }catch(e){
    console.warn("refreshAnnouncementFromEngine:",e);
    return null;
  }
};

window.previewAnnouncement=function(){
  const track=document.getElementById("announcementPreviewTrack"), box=document.getElementById("announcementPreview");
  if(!track||!box)return;
  const labels={breaking:"🔴 عاجل",ad:"📢 إعلان",notice:"🟡 تنبيه",live:"🟢 مباشر"};
  const type=document.getElementById("announcementType")?.value||"breaking";
  const text=document.getElementById("announcementText")?.value.trim()||"HICHRAWI-TV";
  const bg=document.getElementById("announcementBg")?.value||"#e00000";
  const color=document.getElementById("announcementColor")?.value||"#ffffff";
  const size=document.getElementById("announcementSize")?.value||"20px";
  const speed=Number(document.getElementById("announcementSpeed")?.value||18);
  track.textContent=(labels[type]||"🔴 عاجل")+" : "+text;
  box.style.background=bg; track.style.color=color; track.style.fontSize=size; track.style.animationDuration=speed+"s";
};
// ================= FIX BROADCAST SCHEDULE ACTION COLUMN =================
(function fixBroadcastScheduleActionColumn(){

  const style = document.createElement("style");

  style.textContent = `
    #scheduleTableBody{
      overflow: visible !important;
    }

    #scheduleTableBody{
      --schedule-action-width: 110px;
    }

    #scheduleTableBody td:last-child{
      min-width:110px !important;
      width:110px !important;
      max-width:110px !important;
      white-space:nowrap !important;
      overflow:visible !important;
      writing-mode:horizontal-tb !important;
      word-break:keep-all !important;
      overflow-wrap:normal !important;
      text-align:center !important;
    }

    #scheduleTableBody td:last-child button{
      min-width:85px !important;
      width:auto !important;
      white-space:nowrap !important;
      overflow:visible !important;
      writing-mode:horizontal-tb !important;
      word-break:keep-all !important;
      overflow-wrap:normal !important;
    }

    #scheduleTableBody{
      position:relative;
    }

    #scheduleTableBody tr:first-child td:last-child{
      white-space:nowrap !important;
    }

    #scheduleTableBody
      ~ *{
      overflow:visible !important;
    }

    table:has(#scheduleTableBody) th:last-child{
      min-width:110px !important;
      width:110px !important;
      max-width:110px !important;
      white-space:nowrap !important;
      overflow:visible !important;
      writing-mode:horizontal-tb !important;
      word-break:keep-all !important;
      overflow-wrap:normal !important;
      text-align:center !important;
    }
  `;

  document.head.appendChild(style);

})();
