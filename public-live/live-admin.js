import {initializeApp} from "https://www.gstatic.com/firebasejs/12.16.0/firebase-app.js";
import {getAuth,signInWithEmailAndPassword,onAuthStateChanged,signOut} from "https://www.gstatic.com/firebasejs/12.16.0/firebase-auth.js";
import {getFirestore,collection,getDocs,doc,setDoc,addDoc,deleteDoc,updateDoc,getDoc,query,orderBy,serverTimestamp,arrayRemove,writeBatch} from "https://www.gstatic.com/firebasejs/12.16.0/firebase-firestore.js";
import {firebaseConfig} from "../admin-config.js";

const app=initializeApp(firebaseConfig);
const auth=getAuth(app);
const db=getFirestore(app);
const ADMIN="hichrawi86@gmail.com";

const $=id=>document.getElementById(id);
const randomCode=()=>{
 const chars="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
 let s="";
 for(let i=0;i<8;i++)s+=String(Math.floor(Math.random()*10));
 return s;
};
const sha256=async s=>{
 const b=await crypto.subtle.digest("SHA-256",new TextEncoder().encode(s));
 return [...new Uint8Array(b)].map(x=>x.toString(16).padStart(2,"0")).join("");
};

function adminOnly(){
 const u=auth.currentUser;
 if(!u || (u.email||"").toLowerCase()!==ADMIN)return false;
 return true;
}

$("loginForm").onsubmit=async e=>{
 e.preventDefault();
 $("loginMsg").textContent="";
 try{
  await signInWithEmailAndPassword(auth,$("email").value.trim(),$("password").value);
 }catch(err){$("loginMsg").textContent="❌ البريد الإلكتروني أو كلمة السر غير صحيحة";}
};

$("logout").onclick=()=>signOut(auth);

onAuthStateChanged(auth,async u=>{
 if(u && (u.email||"").toLowerCase()===ADMIN){
  $("login").style.display="none";$("app").style.display="block";await refresh();
 }else{
  if(u)await signOut(auth);
  $("login").style.display="block";$("app").style.display="none";
 }
});

document.querySelectorAll(".nav button").forEach(b=>b.onclick=()=>{
 document.querySelectorAll(".nav button").forEach(x=>x.classList.remove("active"));
 document.querySelectorAll(".section").forEach(x=>x.classList.remove("active"));
 b.classList.add("active");$(b.dataset.tab).classList.add("active");
});

async function refresh(){
 await loadSubscriptions();
 await loadDevices();
 await loadChannels();
 await loadSettings();
}

async function loadSubscriptions(){
 const snap=await getDocs(collection(db,"subscriptions"));
 let html="",active=0;
 snap.forEach(x=>{
  const d=x.data();
  if(d.active)active++;
  const devices=Array.isArray(d.deviceIds)?d.deviceIds.length:0;
  const status=d.status||"ready";

  const formatDate=(value)=>{
   try{
    const date=value?.toDate?.();
    if(!date) return "-";
    const pad=n=>String(n).padStart(2,"0");
    return `${pad(date.getDate())}/${pad(date.getMonth()+1)}/${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
   }catch(_){return "-";}
  };

  const createdAt=formatDate(d.createdAt);
  const activatedDate=d.activatedAt?.toDate?.();

  let activatedAt=activatedDate
   ? formatDate(d.activatedAt)
   : "لم يُفعّل بعد";

  let expiresAt="-";
  if(activatedDate && Number(d.durationDays)>0){
   const end=new Date(activatedDate.getTime()+Number(d.durationDays)*24*60*60*1000);
   expiresAt=formatDate({toDate:()=>end});
  }

  html+=`<tr>
   <td><span class="code">${d.code||x.id}</span></td>
   <td>${d.durationDays||"-"} يوم</td>
   <td>${devices}/${d.maxDevices||1}</td>
   <td>${status}</td>
   <td>${createdAt}</td>
   <td>${activatedAt}</td>
   <td>${expiresAt}</td>
   <td>
    ${d.active?`<button class="gray" data-disable="${x.id}">تعطيل</button>`:`<button class="green" data-enable="${x.id}">تفعيل</button>`}
    <button class="red" data-delete="${x.id}">حذف</button>
   </td>
  </tr>`;
 });
 $("subsTable").innerHTML=html||`<tr><td colspan="8">لا توجد أكواد بعد</td></tr>`;
 $("countSubs").textContent=snap.size;
 $("countActive").textContent=active;
 document.querySelectorAll("[data-delete]").forEach(b=>b.onclick=async()=>{
  if(confirm("حذف هذا الكود؟")){await deleteDoc(doc(db,"subscriptions",b.dataset.delete));await refresh();}
 });
 document.querySelectorAll("[data-disable]").forEach(b=>b.onclick=async()=>{
  await updateDoc(doc(db,"subscriptions",b.dataset.disable),{active:false,status:"disabled"});await refresh();
 });
 document.querySelectorAll("[data-enable]").forEach(b=>b.onclick=async()=>{
  await updateDoc(doc(db,"subscriptions",b.dataset.enable),{active:true,status:"ready"});await refresh();
 });
}

$("createCode").onclick=async()=>{
 if(!adminOnly())return;
 const code=randomCode(), id=await sha256(code);
 const durationDays=Number($("duration").value),maxDevices=Number($("maxDevices").value);
 await setDoc(doc(db,"subscriptions",id),{
  code,durationDays,maxDevices,status:"ready",active:false,deviceIds:[],createdAt:serverTimestamp()
 });
 $("newCode").innerHTML=`✅ تم إنشاء الكود:<br><span class="code">${code}</span>`;
 await loadSubscriptions();
};

async function loadDevices(){
 const snap=await getDocs(collection(db,"licenses"));
 const presenceSnap=await getDocs(collection(db,"devices"));

 const presenceByKey=new Map();
 presenceSnap.forEach(x=>{
  const d=x.data();
  presenceByKey.set(x.id,d);
 });

 let html="";

 snap.forEach(x=>{
  const d=x.data();
  const deviceKey=d.deviceKey||"-";
  const p=presenceByKey.get(deviceKey)||{};

  let online=false;
  let lastSeen="-";

  try{
   const date=p.lastSeen?.toDate?.();
   if(date){
    lastSeen=date.toLocaleString("fr-TN");
    online=(Date.now()-date.getTime()) <= 90*1000;
   }
  }catch(_){}

  const status=online
    ? '<span style="color:#35d68a;font-weight:bold">🟢 متصل</span>'
    : '<span style="color:#aaa">⚪ غير متصل</span>';

  const channel=online
    ? (p.channelName||"غير معروف")
    : (p.channelName||"آخر قناة");

  const packageName=p.packageName||"-";

  html+=`<tr>
   <td>${deviceKey}</td>
   <td>${d.subscriptionId||"-"}</td>
   <td>${d.authUid||"-"}</td>
   <td>${status}</td>
   <td>${packageName}</td>
   <td>${channel}</td>
   <td>${lastSeen}</td>
   <td>${d.activatedAt?.toDate?.()?.toLocaleString("fr-TN")||"-"}</td>
   <td>${d.expiresAt?.toDate?.()?.toLocaleString("fr-TN")||"-"}</td>
   <td>
    <button class="red" data-devdel="${x.id}">🗑 حذف</button>
   </td>
  </tr>`;
 });

 $("devicesTable").innerHTML=html||`<tr><td colspan="10">لا توجد أجهزة مفعلة</td></tr>`;
 $("countDevices").textContent=snap.size;

 document.querySelectorAll("[data-devdel]").forEach(b=>{
  b.onclick=async()=>{
   if(!adminOnly())return;

   if(!confirm("⚠️ حذف هذا الجهاز من التفعيل؟"))return;

   try{
    const licenseRef=doc(db,"licenses",b.dataset.devdel);
    const licenseSnap=await getDoc(licenseRef);

    if(!licenseSnap.exists()){
     alert("❌ الجهاز غير موجود");
     await loadDevices();
     return;
    }

    const d=licenseSnap.data();
    const subscriptionId=d.subscriptionId;
    const deviceKey=d.deviceKey;

    if(subscriptionId && deviceKey){
     const subscriptionRef=doc(db,"subscriptions",subscriptionId);
     const subscriptionSnap=await getDoc(subscriptionRef);

     if(subscriptionSnap.exists()){
      const batch=writeBatch(db);

      batch.update(subscriptionRef,{
       deviceIds:arrayRemove(deviceKey)
      });

      batch.delete(licenseRef);

      await batch.commit();

      alert("✅ تم حذف الجهاز وتحرير مكانه من الاشتراك");
     }else{
      await deleteDoc(licenseRef);
      alert("✅ تم حذف الجهاز");
     }
    }else{
     await deleteDoc(licenseRef);
     alert("✅ تم حذف الجهاز");
    }

    try{
     await refresh();
    }catch(refreshErr){
     console.error("REFRESH AFTER DELETE:", refreshErr);
     await loadDevices();
    }

   }catch(err){
    console.error("DELETE DEVICE ERROR:", err);
    alert("❌ تعذر حذف الجهاز\\n\\nالكود: " + (err?.code || "unknown") + "\\nالسبب: " + (err?.message || err));
   }
  };
 });
}


async function loadChannels(){
 const snap=await getDocs(collection(db,"channels"));
 let channels=[];

 snap.forEach(x=>{
  const d=x.data();
  channels.push({
   id:x.id,
   name:d.name||"",
   stream:d.stream||"",
   logo:d.logo||"",
   status:d.status||"مباشر"
  });
 });

 // ترتيب رقمي حسب الرقم الموجود في اسم القناة
 channels.sort((a,b)=>{
  const na=(a.name.match(/(\d+)\s*$/)||[])[1];
  const nb=(b.name.match(/(\d+)\s*$/)||[])[1];
  if(na && nb) return Number(na)-Number(nb);
  if(na) return -1;
  if(nb) return 1;
  return a.name.localeCompare(b.name,"ar");
 });

 $("countChannels").textContent=channels.length;

 const oldTable=$("channelsTable");
 const host=oldTable.closest('div[style*="overflow"]') || oldTable.closest(".panel");

 // إنشاء واجهة القنوات الجديدة مرة واحدة فقط
 if(!$("channelSearch")){
  const search=document.createElement("input");
  search.id="channelSearch";
  search.type="search";
  search.placeholder="🔎 ابحث عن قناة...";
  search.style.marginBottom="14px";
  oldTable.closest(".panel").insertBefore(search,host);

  host.innerHTML='<div id="channelsList" class="channels-manager-list"></div>';

  if(!document.getElementById("channelsManagerStyle")){
   const style=document.createElement("style");
   style.id="channelsManagerStyle";
   style.textContent=`
    .channels-manager-list{
      display:grid;
      grid-template-columns:repeat(auto-fill,minmax(310px,1fr));
      gap:12px;
      max-height:650px;
      overflow-y:auto;
      padding:3px;
    }
    .channel-manager-card{
      background:#111;
      border:1px solid #303030;
      border-radius:12px;
      padding:12px;
      display:flex;
      align-items:center;
      gap:12px;
      min-height:105px;
    }
    .channel-manager-logo{
      width:78px;
      height:58px;
      object-fit:contain;
      border-radius:8px;
      background:#090909;
      flex-shrink:0;
    }
    .channel-manager-info{
      min-width:0;
      flex:1;
    }
    .channel-manager-name{
      font-weight:bold;
      font-size:17px;
      color:#fff;
      margin-bottom:5px;
      overflow:hidden;
      text-overflow:ellipsis;
      white-space:nowrap;
    }
    .channel-manager-status{
      color:#35d68a;
      font-size:12px;
      margin-bottom:8px;
    }
    .channel-manager-actions{
      display:flex;
      gap:5px;
    }
    .channel-manager-actions button{
      padding:7px 10px;
      font-size:12px;
    }
    .channel-number{
      color:#f0b900;
      font-weight:bold;
      margin-left:5px;
    }
    @media(max-width:700px){
      .channels-manager-list{
       grid-template-columns:1fr;
      }
    }
   `;
   document.head.appendChild(style);
  }
 }

 const search=$("channelSearch");
 const list=$("channelsList");

 const render=()=>{
  const term=(search.value||"").trim().toLowerCase();

  const filtered=channels.filter(ch=>
   ch.name.toLowerCase().includes(term)
  );

  list.innerHTML=filtered.length ? filtered.map((ch,index)=>{
   const number=(ch.name.match(/(\d+)\s*$/)||[])[1] || (index+1);

   return `
    <div class="channel-manager-card">
      ${
       ch.logo
       ? `<img class="channel-manager-logo"
              src="${ch.logo}"
              alt=""
              onerror="this.style.visibility='hidden'">`
       : `<div class="channel-manager-logo"></div>`
      }

      <div class="channel-manager-info">
        <div class="channel-manager-name">
          <span class="channel-number">#${number}</span>
          ${ch.name}
        </div>

        <div class="channel-manager-status">● ${ch.status}</div>

        <div class="channel-manager-actions">
          <button class="gold" data-chedit="${ch.id}">✏️ تعديل</button>
          <button class="red" data-chdel="${ch.id}">🗑 حذف</button>
        </div>
      </div>
    </div>
   `;
  }).join("") :
  `<div style="padding:25px;text-align:center;color:#aaa;grid-column:1/-1">
    لا توجد قنوات مطابقة
   </div>`;

  document.querySelectorAll("[data-chdel]").forEach(b=>{
   b.onclick=async()=>{
    if(confirm("حذف القناة من التطبيق؟")){
     await deleteDoc(doc(db,"channels",b.dataset.chdel));
     await loadChannels();
    }
   };
  });

  document.querySelectorAll("[data-chedit]").forEach(b=>{
   b.onclick=async()=>{
    if(!adminOnly())return;

    const channelRef=doc(db,"channels",b.dataset.chedit);
    const channelSnap=await getDoc(channelRef);

    if(!channelSnap.exists()){
     alert("❌ القناة غير موجودة");
     return;
    }

    const d=channelSnap.data();

    const name=prompt("اسم القناة:",d.name||"");
    if(name===null)return;

    const stream=prompt("رابط البث:",d.stream||"");
    if(stream===null)return;

    if(!name.trim() || !stream.trim()){
     alert("❌ اسم القناة ورابط البث مطلوبان");
     return;
    }

    const logo=prompt("رابط الشعار (يمكن تركه كما هو):",d.logo||"");
    if(logo===null)return;

    await updateDoc(channelRef,{
     name:name.trim(),
     stream:stream.trim(),
     logo:logo.trim(),
     updatedAt:serverTimestamp()
    });

    $("channelMsg").textContent="✅ تم تعديل القناة دون تغيير مصدرها";
    await loadChannels();
   };
  });
 };

 search.oninput=render;
 render();
}

$("addChannel").onclick=async()=>{
 const name=$("channelName").value.trim(),stream=$("channelStream").value.trim(),logo=$("channelLogo").value.trim();
 if(!name||!stream){$("channelMsg").textContent="❌ أدخل اسم القناة ورابط البث";return;}
 await setDoc(doc(collection(db,"channels")),{
  name,stream,logo,status:"مباشر",createdAt:serverTimestamp()
 });
 $("channelName").value="";$("channelStream").value="";$("channelLogo").value="";
 $("channelMsg").textContent="✅ تمت إضافة القناة";await loadChannels();
};


async function loadSettings(){
 const s=await getDoc(doc(db,"settings","app"));
 if(s.exists()){
  const d=s.data();
  $("appName").value=d.appName||"HICHRAWI LIVE";
  $("welcome").value=d.welcome||"";
  $("facebook").value=d.facebook||"";
  $("tiktok").value=d.tiktok||"";
  $("instagram").value=d.instagram||"";
  $("website").value=d.website||"";
  $("telegram").value=d.telegram||"";
  $("whatsapp").value=d.whatsapp||"";
  $("facebookEnabled").value=d.facebookEnabled===false ? "false" : "true";
  $("tiktokEnabled").value=d.tiktokEnabled===false ? "false" : "true";
  $("instagramEnabled").value=d.instagramEnabled===false ? "false" : "true";
  $("websiteEnabled").value=d.websiteEnabled===false ? "false" : "true";
  $("telegramEnabled").value=d.telegramEnabled===false ? "false" : "true";
  $("whatsappEnabled").value=d.whatsappEnabled===false ? "false" : "true";
 }
}
$("saveSettings").onclick=async()=>{
 await setDoc(doc(db,"settings","app"),{
  appName:$("appName").value.trim()||"HICHRAWI LIVE",
  welcome:$("welcome").value.trim(),
  facebook:$("facebook").value.trim(),
  tiktok:$("tiktok").value.trim(),
  instagram:$("instagram").value.trim(),
  website:$("website").value.trim(),
  telegram:$("telegram").value.trim(),
  whatsapp:$("whatsapp").value.trim(),
  facebookEnabled:$("facebookEnabled").value === "true",
  tiktokEnabled:$("tiktokEnabled").value === "true",
  instagramEnabled:$("instagramEnabled").value === "true",
  websiteEnabled:$("websiteEnabled").value === "true",
  telegramEnabled:$("telegramEnabled").value === "true",
  whatsappEnabled:$("whatsappEnabled").value === "true",
  updatedAt:serverTimestamp()
 },{merge:true});
 $("settingsMsg").textContent="✅ تم حفظ إعدادات التطبيق وروابط التواصل";
};


// ============================================================
// HICHRAWI LIVE - PACKAGE MANAGER
// ============================================================

let hichrawiPackageChannels = [];

let packageChannelsDraft = [];

function renderPackageChannels(){
 const box = $("packageChannels");
 if(!box) return;

 if(!packageChannelsDraft.length){
  box.innerHTML = `<div style="color:#888;padding:12px;text-align:center;">
    لا توجد قنوات مضافة لهذه الباقة بعد
  </div>`;
  return;
 }

 const sorted = [...packageChannelsDraft].sort(
  (a,b)=>(Number(a.sortOrder)||0)-(Number(b.sortOrder)||0)
 );

 box.innerHTML = sorted.map((ch,index)=>{
  const logo = ch.logoUrl
   ? `<img src="${escapeHtmlPackage(ch.logoUrl)}"
       style="width:48px;height:48px;object-fit:contain;border-radius:8px;background:#222;">`
   : `<div style="width:48px;height:48px;border-radius:8px;background:#222;">
     </div>`;

  return `
   <div style="display:flex;align-items:center;gap:10px;padding:10px;
        margin-bottom:8px;background:#181818;border-radius:10px;">
    ${logo}
    <div style="flex:1;min-width:0;">
     <strong>${escapeHtmlPackage(ch.name)}</strong>
     <div style="color:#888;font-size:12px;margin-top:4px;">
      ترتيب: ${Number(ch.sortOrder)||index+1}
     </div>
    </div>
    <button class="gray" type="button"
      data-package-channel-edit="${escapeHtmlPackage(ch.id)}">
      ✏️
    </button>
    <button class="red" type="button"
      data-package-channel-delete="${escapeHtmlPackage(ch.id)}">
      🗑
    </button>
   </div>
  `;
 }).join("");

 document.querySelectorAll("[data-package-channel-edit]").forEach(b=>{
  b.onclick=()=>{
   const id=b.dataset.packageChannelEdit;
   const ch=packageChannelsDraft.find(x=>String(x.id)===String(id));
   if(!ch) return;

   $("packageChannelEditId").value=ch.id;
   $("packageChannelName").value=ch.name||"";
   $("packageChannelStream").value=ch.streamUrl||"";
   $("packageChannelLogo").value=ch.logoUrl||"";
   $("packageChannelOrder").value=Number(ch.sortOrder)||1;

   $("addPackageChannel").textContent="💾 حفظ تعديل القناة";
   $("cancelPackageChannelEdit").style.display="inline-block";
  };
 });

 document.querySelectorAll("[data-package-channel-delete]").forEach(b=>{
  b.onclick=()=>{
   const id=b.dataset.packageChannelDelete;

   if(!confirm("هل تريد حذف هذه القناة من هذه الباقة؟")) return;

   packageChannelsDraft=packageChannelsDraft.filter(
    x=>String(x.id)!==String(id)
   );

   renderPackageChannels();
  };
 });
}


function escapeHtmlPackage(value){
 return String(value)
  .replace(/&/g,"&amp;")
  .replace(/</g,"&lt;")
  .replace(/>/g,"&gt;")
  .replace(/"/g,"&quot;")
  .replace(/'/g,"&#039;");
}


function resetPackageChannelEditor(){
 $("packageChannelEditId").value="";
 $("packageChannelName").value="";
 $("packageChannelStream").value="";
 $("packageChannelLogo").value="";
 $("packageChannelOrder").value="1";

 $("addPackageChannel").textContent="➕ إضافة القناة";
 $("cancelPackageChannelEdit").style.display="none";
}


$("addPackageChannel")?.addEventListener("click",()=>{
 const name=$("packageChannelName").value.trim();
 const streamUrl=$("packageChannelStream").value.trim();
 const logoUrl=$("packageChannelLogo").value.trim();
 const sortOrder=Math.max(1,Number($("packageChannelOrder").value)||1);
 const editId=$("packageChannelEditId").value.trim();

 if(!name){
  alert("❌ اكتب اسم القناة");
  return;
 }

 if(!streamUrl){
  alert("❌ اكتب رابط البث");
  return;
 }

 if(editId){
  const index=packageChannelsDraft.findIndex(
   x=>String(x.id)===String(editId)
  );

  if(index>=0){
   packageChannelsDraft[index]={
    ...packageChannelsDraft[index],
    name,
    streamUrl,
    logoUrl,
    sortOrder,
    enabled:true
   };
  }
 }else{
  packageChannelsDraft.push({
   id:"pc_"+Date.now()+"_"+Math.random().toString(36).slice(2,8),
   name,
   streamUrl,
   logoUrl,
   sortOrder,
   enabled:true
  });
 }

 resetPackageChannelEditor();
 renderPackageChannels();
});


$("cancelPackageChannelEdit")?.addEventListener("click",()=>{
 resetPackageChannelEditor();
});


function selectedPackageChannels(){
 return packageChannelsDraft;
}


function clearPackageForm(){
 $("packageDocId").value="";
 $("packageName").value="";
 $("packageLogo").value="";

 packageChannelsDraft=[];
 resetPackageChannelEditor();
 renderPackageChannels();

 $("packageMsg").textContent="";
}


async function loadPackages(){

 const list=$("packagesList");
 if(!list) return;

 try{

  const snap=await getDocs(collection(db,"packages"));

  if(snap.empty){
   list.innerHTML=`<div class="msg">لا توجد باقات محفوظة بعد.</div>`;
   return;
  }

  const packages=snap.docs.map(x=>{
   const d=x.data();

   const legacyIds=Array.isArray(d.channelIds)
    ? d.channelIds.map(Number).filter(Number.isFinite)
    : [];

   const channels=Array.isArray(d.channels)
    ? d.channels.filter(ch=>ch && typeof ch==="object").map((ch,index)=>({
       id:String(ch.id || ("pc_"+index)),
       name:String(ch.name || "Channel"),
       streamUrl:String(ch.streamUrl || ch.stream || ch.url || ""),
       logoUrl:String(ch.logoUrl || ch.logo || ""),
       sortOrder:Number(ch.sortOrder || ch.sort_order || index+1) || index+1,
       enabled:ch.enabled !== false
      }))
    : [];

   return {
    docId:x.id,
    packageId:Number(d.packageId ?? d.id ?? 0),
    name:String(d.name || "الباقة"),
    logoUrl:String(d.logoUrl || d.logo || ""),
    channelIds:legacyIds,
    channels:channels
   };
  }).sort((a,b)=>a.packageId-b.packageId);

  list.innerHTML=packages.map(pkg=>{

   const totalChannels=pkg.channelIds.length + pkg.channels.length;

   const logo=pkg.logoUrl
    ? `<img src="${escapeHtmlPackage(pkg.logoUrl)}"
       style="width:58px;height:58px;object-fit:contain;border-radius:50%;background:#222;">`
    : `<div style="width:58px;height:58px;border-radius:50%;
       display:flex;align-items:center;justify-content:center;
       background:#222;font-weight:bold;font-size:20px;">
       ${escapeHtmlPackage(pkg.name.slice(0,2))}
      </div>`;

   return `
    <div style="display:flex;align-items:center;gap:12px;padding:12px;
      margin-bottom:8px;background:#181818;border-radius:10px;">
      ${logo}
      <div style="flex:1">
       <strong>${escapeHtmlPackage(pkg.name)}</strong>
       <div style="color:#888;margin-top:4px">
        ${totalChannels} قناة
       </div>
      </div>
      <button class="gray" data-package-edit="${escapeHtmlPackage(pkg.docId)}">
       ✏️ تعديل
      </button>
      <button class="red" data-package-delete="${escapeHtmlPackage(pkg.docId)}">
       🗑 حذف
      </button>
    </div>
   `;
  }).join("");

  document.querySelectorAll("[data-package-edit]").forEach(b=>{
   b.onclick=async()=>{
    const id=b.dataset.packageEdit;
    const snap=await getDoc(doc(db,"packages",id));

    if(!snap.exists()){
     alert("❌ الباقة غير موجودة");
     return;
    }

    const d=snap.data();

    $("packageDocId").value=id;
    $("packageName").value=d.name || "";
    $("packageLogo").value=d.logoUrl || d.logo || "";

    packageChannelsDraft=Array.isArray(d.channels)
     ? d.channels.filter(ch=>ch && typeof ch==="object").map((ch,index)=>({
        id:String(ch.id || ("pc_"+Date.now()+"_"+index)),
        name:String(ch.name || "Channel"),
        streamUrl:String(ch.streamUrl || ch.stream || ch.url || ""),
        logoUrl:String(ch.logoUrl || ch.logo || ""),
        sortOrder:Number(ch.sortOrder || ch.sort_order || index+1) || index+1,
        enabled:ch.enabled !== false
       }))
     : [];

    resetPackageChannelEditor();
    renderPackageChannels();

    $("packages").scrollIntoView({
     behavior:"smooth",
     block:"start"
    });
   };
  });

  document.querySelectorAll("[data-package-delete]").forEach(b=>{
   b.onclick=async()=>{
    if(!adminOnly()) return;
    if(!confirm("هل تريد حذف هذه الباقة؟")) return;

    try{
     await deleteDoc(doc(db,"packages",b.dataset.packageDelete));
     await loadPackages();
     $("packageMsg").textContent="✅ تم حذف الباقة";
    }catch(e){
     console.error("deletePackage",e);
     alert("❌ فشل حذف الباقة\n\n"+(e.message || e));
    }
   };
  });

 }catch(e){
  console.error("loadPackages",e);
  list.innerHTML="❌ تعذر تحميل الباقات. تحقق من Firestore.";
 }
}


$("savePackage")?.addEventListener("click",async()=>{

 if(!adminOnly()) return;

 const name=$("packageName").value.trim();
 const logo=$("packageLogo").value.trim();
 const existingId=$("packageDocId").value.trim();
 const channels=selectedPackageChannels();

 if(!name){
  alert("❌ اكتب اسم الباقة");
  return;
 }

 try{

  $("packageMsg").textContent="🟡 جاري الحفظ...";

  const data={
   name:name,
   logoUrl:logo,
   channels:channels,
   updatedAt:serverTimestamp()
  };

  if(existingId){

   await updateDoc(
    doc(db,"packages",existingId),
    data
   );

  }else{

   const snap=await getDocs(collection(db,"packages"));

   const ids=snap.docs
    .map(x=>Number(x.data()?.packageId ?? x.data()?.id))
    .filter(Number.isFinite);

   const nextId=ids.length ? Math.max(...ids)+1 : 1;

   await addDoc(
    collection(db,"packages"),
    {
     packageId:nextId,
     ...data,
     channelIds:[],
     createdAt:serverTimestamp()
    }
   );
  }

  $("packageMsg").textContent="✅ تم حفظ الباقة";
  clearPackageForm();
  await loadPackages();

 }catch(e){

  console.error("savePackage",e);

  $("packageMsg").textContent=
   "❌ فشل الحفظ: "+(e.message || e);
 }
});

$("clearPackage")?.addEventListener(
 "click",
 clearPackageForm
);


// Load package UI when admin is ready
renderPackageChannels();
loadPackages();



setInterval(()=>{
 if(auth.currentUser && adminOnly()){
  loadDevices().catch(()=>{});
 }
},15000);
