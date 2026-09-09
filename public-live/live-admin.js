import {initializeApp} from "https://www.gstatic.com/firebasejs/12.16.0/firebase-app.js";
import {getAuth,signInWithEmailAndPassword,onAuthStateChanged,signOut} from "https://www.gstatic.com/firebasejs/12.16.0/firebase-auth.js";
import {getFirestore,collection,getDocs,doc,setDoc,deleteDoc,updateDoc,getDoc,query,orderBy,serverTimestamp} from "https://www.gstatic.com/firebasejs/12.16.0/firebase-firestore.js";
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
  html+=`<tr>
   <td><span class="code">${d.code||x.id}</span></td>
   <td>${d.durationDays||"-"} يوم</td>
   <td>${devices}/${d.maxDevices||1}</td>
   <td>${status}</td>
   <td>
    ${d.active?`<button class="gray" data-disable="${x.id}">تعطيل</button>`:`<button class="green" data-enable="${x.id}">تفعيل</button>`}
    <button class="red" data-delete="${x.id}">حذف</button>
   </td>
  </tr>`;
 });
 $("subsTable").innerHTML=html||`<tr><td colspan="5">لا توجد أكواد بعد</td></tr>`;
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
  html+=`<tr><td>${d.deviceKey||"-"}</td><td>${d.subscriptionId||"-"}</td><td>${d.authUid||"-"}</td><td>${d.activatedAt?.toDate?.()?.toLocaleString("fr-TN")||"-"}</td><td>${d.expiresAt?.toDate?.()?.toLocaleString("fr-TN")||"-"}</td></tr>`;
 });
 $("devicesTable").innerHTML=html||`<tr><td colspan="5">لا توجد أجهزة مفعلة</td></tr>`;
 $("countDevices").textContent=snap.size;
}

async function loadChannels(){
 const snap=await getDocs(collection(db,"channels"));
 let html="";
 snap.forEach(x=>{
  const d=x.data();
  html+=`<tr>
   <td>${d.name||"-"}</td>
   <td dir="ltr">${d.stream||""}</td>
   <td>
    <button class="gold" data-chedit="${x.id}">تعديل</button>
    <button class="red" data-chdel="${x.id}">حذف</button>
   </td>
  </tr>`;
 });
 $("channelsTable").innerHTML=html||`<tr><td colspan="3">لا توجد قنوات</td></tr>`;
 $("countChannels").textContent=snap.size;

 document.querySelectorAll("[data-chdel]").forEach(b=>b.onclick=async()=>{
  if(confirm("حذف القناة من التطبيق؟")){
   await deleteDoc(doc(db,"channels",b.dataset.chdel));
   await loadChannels();
  }
 });

 document.querySelectorAll("[data-chedit]").forEach(b=>b.onclick=async()=>{
  if(!adminOnly())return;

  const channelRef=doc(db,"channels",b.dataset.chedit);
  const snap=await getDoc(channelRef);

  if(!snap.exists()){
   alert("❌ القناة غير موجودة");
   return;
  }

  const d=snap.data();

  const name=prompt("اسم القناة:",d.name||"");
  if(name===null)return;

  const stream=prompt("رابط البث:",d.stream||"");
  if(stream===null)return;

  if(!name.trim() || !stream.trim()){
   alert("❌ اسم القناة ورابط البث مطلوبان");
   return;
  }

  const logo=prompt("رابط الشعار (يمكن تركه فارغًا):",d.logo||"");
  if(logo===null)return;

  await updateDoc(channelRef,{
   name:name.trim(),
   stream:stream.trim(),
   logo:logo.trim(),
   updatedAt:serverTimestamp()
  });

  $("channelMsg").textContent="✅ تم تعديل القناة دون حذفها";
  await loadChannels();
 });
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
 }
}
$("saveSettings").onclick=async()=>{
 await setDoc(doc(db,"settings","app"),{
  appName:$("appName").value.trim()||"HICHRAWI LIVE",
  welcome:$("welcome").value.trim(),
  updatedAt:serverTimestamp()
 },{merge:true});
 $("settingsMsg").textContent="✅ تم حفظ الإعدادات";
};
