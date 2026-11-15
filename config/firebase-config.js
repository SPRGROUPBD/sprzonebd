import { initializeApp, getApps, getApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import { getAuth } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import { getDatabase } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-database.js";

const _cfg = {
  apiKey: "AIzaSyBmogqpXzirZC-aUHZrFMwutSJ5fmbyDXk",
  authDomain: "e-commarcebd.firebaseapp.com",
  databaseURL: "https://e-commarcebd-default-rtdb.europe-west1.firebasedatabase.app",
  projectId: "e-commarcebd",
  storageBucket: "e-commarcebd.firebasestorage.app",
  messagingSenderId: "799252588942",
  appId: "1:799252588942:web:985396062c3a1823c6dcdf",
  measurementId: "G-FM6BH07VPM"
};

export const ADMIN_EMAIL = "mail.sajjadahmed@gmail.com";

export function getFirebaseClient(options = {}) {
  const app = getApps().some((a) => a.name === "[DEFAULT]") ? getApp() : initializeApp(_cfg);
  const auth = getAuth(app);
  const db = getDatabase(app);

  if (options.withCustomerCreator) {
    const creatorName = "CustomerCreator";
    const creatorApp = getApps().some((a) => a.name === creatorName)
      ? getApp(creatorName)
      : initializeApp(_cfg, creatorName);
    const customerCreatorAuth = getAuth(creatorApp);
    return { app, auth, db, customerCreatorAuth };
  }

  return { app, auth, db };
}
