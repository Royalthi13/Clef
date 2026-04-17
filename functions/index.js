const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.deleteAccount = functions.https.onCall(async (data, context) => {
    let uid;

    if (context.auth) {
        uid = context.auth.uid;
    } else if (data && data.idToken) {
        try {
            const decoded = await admin.auth().verifyIdToken(data.idToken);
            uid = decoded.uid;
        } catch (e) {
            throw new functions.https.HttpsError("unauthenticated", "Token inválido.");
        }
    } else {
        throw new functions.https.HttpsError("unauthenticated", "El usuario no está autenticado.");
    }

    await admin.firestore().collection("users").doc(uid).delete();
    await admin.auth().deleteUser(uid);

    return { success: true };
});

exports.deleteAccountHttp = functions.https.onRequest(async (req, res) => {
    res.set("Access-Control-Allow-Origin", "*");
    if (req.method === "OPTIONS") { res.status(204).send(""); return; }

    const authHeader = req.headers.authorization || "";
    const idToken = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : null;

    if (!idToken) { res.status(401).json({ error: "No token" }); return; }

    try {
        const decoded = await admin.auth().verifyIdToken(idToken);
        const uid = decoded.uid;
        await admin.firestore().collection("users").doc(uid).delete();
        await admin.auth().deleteUser(uid);
        res.status(200).json({ success: true });
    } catch (e) {
        console.error("deleteAccountHttp error:", e);
        res.status(401).json({ error: e.message });
    }
});
