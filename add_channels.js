const admin = require("firebase-admin");
const serviceAccount = require("./firebase-adminsdk.json");

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

const channels = [
  ["HichrawiSport1","http://line.irav25.biz:8080/2ea386aea797/5f2a4e2d94ec/317",1],
  ["HichrawiSport2","http://line.irav25.biz:8080/2ea386aea797/5f2a4e2d94ec/318",2],
  ["HichrawiSport3","http://line.irav25.biz:8080/2ea386aea797/5f2a4e2d94ec/319",3],
  ["HichrawiSport4","http://line.irav25.biz:8080/2ea386aea797/5f2a4e2d94ec/320",4],
  ["HichrawiSport5","http://line.irav25.biz:8080/2ea386aea797/5f2a4e2d94ec/321",5],
  ["HichrawiSport6","http://line.irav25.biz:8080/2ea386aea797/5f2a4e2d94ec/322",6],
  ["HichrawiSport7","http://line.irav25.biz:8080/2ea386aea797/5f2a4e2d94ec/323",7],
  ["HichrawiSport8","http://line.irav25.biz:8080/2ea386aea797/5f2a4e2d94ec/324",8]
];

async function run(){
 for (const [name,streamUrl,sortOrder] of channels){
  await db.collection("channels").doc(name.toLowerCase()).set({
    name,
    streamUrl,
    logo:"",
    channelId:sortOrder,
    sortOrder,
    enabled:true
  });
 }
 console.log("DONE");
}

run();
