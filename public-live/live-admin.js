import {initializeApp} from "https://www.gstatic.com/firebasejs/12.16.0/firebase-app.js";
import {getAuth,signInWithEmailAndPassword,onAuthStateChanged,signOut} from "https://www.gstatic.com/firebasejs/12.16.0/firebase-auth.js";
import {getFirestore,collection,getDocs,doc,setDoc,deleteDoc,updateDoc,getDoc,query,orderBy,serverTimestamp,arrayRemove,writeBatch} from "https://www.gstatic.com/firebasejs/12.16.0/firebase-firestore.js";
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
 let html="";

 snap.forEach(x=>{
  const d=x.data();

  html+=`<tr>
   <td>${d.deviceKey||"-"}</td>
   <td>${d.subscriptionId||"-"}</td>
   <td>${d.authUid||"-"}</td>
   <td>${d.activatedAt?.toDate?.()?.toLocaleString("fr-TN")||"-"}</td>
   <td>${d.expiresAt?.toDate?.()?.toLocaleString("fr-TN")||"-"}</td>
   <td>
    <button class="red" data-devdel="${x.id}">🗑 حذف</button>
   </td>
  </tr>`;
 });

 $("devicesTable").innerHTML=html||`<tr><td colspan="6">لا توجد أجهزة مفعلة</td></tr>`;
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

    // إذا كان الاشتراك موجودًا، نحرر الجهاز منه ثم نحذف الترخيص
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
      // الاشتراك غير موجود، لذلك نحذف سجل الترخيص فقط
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
