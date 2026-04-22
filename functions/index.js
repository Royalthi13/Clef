const functions = require("firebase-functions");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");

admin.initializeApp();

/**
 * Secreto que almacena la contraseña de aplicación de Gmail usada para enviar
 * emails de alerta. Se guarda en Google Cloud Secret Manager para no exponerla
 * en el código fuente.
 */
const gmailAppPassword = defineSecret("GMAIL_APP_PASSWORD");

/**
 * Cloud Function callable: checkLoginIp
 *
 * Se invoca desde la app Android justo después de autenticar al usuario.
 * Detecta accesos sospechosos comparando el dispositivo y el país de origen
 * con los valores conocidos guardados en Firestore.
 *
 * Lógica de detección:
 *  - Si el documento del usuario no existe → primer login, se registra sin alerta.
 *  - Si el dispositivo NO está en knownDevices → acceso sospechoso.
 *  - Si el país NO está en knownCountries → acceso sospechoso.
 *  - En caso sospechoso: guarda los nuevos valores y envía email de alerta al usuario.
 *
 * Campos que gestiona en Firestore (colección "users"):
 *  - knownDevices  : array de strings con los modelos de dispositivo conocidos.
 *  - knownCountries: array de códigos de país (ISO 3166-1 alpha-2) conocidos.
 */
exports.checkLoginIp = onCall({ secrets: [gmailAppPassword] }, async (request) => {
    if (!request.auth) {
        throw new HttpsError("unauthenticated", "El usuario no está autenticado.");
    }

    const uid = request.auth.uid;
    const userEmail = request.auth.token.email;

    /* Obtener la IP real del cliente. x-forwarded-for puede contener varias IPs
       separadas por coma cuando hay proxies intermedios; la primera es la del cliente. */
    const ip = request.rawRequest.headers["x-forwarded-for"]
        ? request.rawRequest.headers["x-forwarded-for"].split(",")[0].trim()
        : request.rawRequest.ip;

    /* El modelo del dispositivo lo envía la app Android como parámetro. */
    const device = (request.data && request.data.device) ? request.data.device : "Dispositivo desconocido";

    console.log(`[checkLoginIp] uid=${uid} ip=${ip} device=${device}`);

    /* Geolocalización de la IP mediante ip-api.com (gratuito, sin API key).
       Se usa para detectar cambios de país, no para mostrar la IP al usuario. */
    let country = null;
    let city = "Ubicación desconocida";
    try {
        const geoRes = await fetch(`http://ip-api.com/json/${ip}?fields=city,regionName,country,countryCode&lang=es`);
        const geoData = await geoRes.json();
        if (geoData.city) {
            city = `${geoData.city}, ${geoData.regionName}, ${geoData.country}`;
            country = geoData.countryCode;
        }
    } catch (geoError) {
        console.error("[checkLoginIp] Error geolocalización:", geoError);
    }

    const userRef = admin.firestore().collection("users").doc(uid);
    const doc = await userRef.get();

    const data = doc.exists ? doc.data() : {};
    const knownDevices   = data.knownDevices   || [];
    const knownCountries = data.knownCountries || [];

    /* Si el documento no existe es el primer login: se registra sin enviar alerta. */
    const isFirstLogin = !doc.exists;

    const deviceKnown  = knownDevices.some(d => d === device);
    const countryKnown = country === null || knownCountries.includes(country);

    /* Se considera acceso sospechoso si el usuario ya existe y el dispositivo
       o el país no están entre los valores conocidos. */
    const isNew = !isFirstLogin && (!deviceKnown || !countryKnown);

    console.log(`[checkLoginIp] device=${device} deviceKnown=${deviceKnown} country=${country} countryKnown=${countryKnown} isNew=${isNew}`);

    /* Guardar dispositivo y/o país nuevos usando set+merge para no sobreescribir
       otros campos del documento (salt, vault, cajaA, etc.). */
    const updates = {};
    if (!deviceKnown) updates.knownDevices   = admin.firestore.FieldValue.arrayUnion(device);
    if (country && !countryKnown) updates.knownCountries = admin.firestore.FieldValue.arrayUnion(country);
    if (Object.keys(updates).length > 0) {
        await userRef.set(updates, { merge: true });
    }

    /* Enviar email de alerta al usuario si el acceso es sospechoso. */
    if (isNew) {
        const reason = !deviceKnown ? "dispositivo nuevo" : "país nuevo";
        try {
            const transporter = nodemailer.createTransport({
                service: "gmail",
                auth: {
                    user: "security.clef@gmail.com",
                    pass: gmailAppPassword.value(),
                },
            });

            const now = new Date().toLocaleString("es-ES", { timeZone: "Europe/Madrid" });
            await transporter.sendMail({
                from: `"Clef Security" <security.clef@gmail.com>`,
                to: userEmail,
                subject: "Nuevo acceso sospechoso detectado - Clef",
                html: `
                    <h2>Acceso sospechoso detectado</h2>
                    <p>Se ha iniciado sesión en tu cuenta de <strong>Clef</strong> desde una ubicación o dispositivo no reconocido.</p>
                    <ul>
                        <li><strong>Dispositivo:</strong> ${device}</li>
                        <li><strong>Ubicación:</strong> ${city}</li>
                        <li><strong>Motivo:</strong> ${reason}</li>
                        <li><strong>Fecha y hora:</strong> ${now}</li>
                    </ul>
                    <p>Si fuiste tú, puedes ignorar este mensaje.<br>
                    Si no reconoces este acceso, cambia tu contraseña maestra inmediatamente.</p>
                `,
            });
        } catch (emailError) {
            /* El error de email no debe interrumpir el login del usuario. */
            console.error("Error enviando email:", emailError);
        }
    }

    return { isNew };
});

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
