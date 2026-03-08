<div align="center">

# 🗝️ Clef
### Gestor de Contraseñas Zero-Knowledge para Android

*"El servidor guarda un baúl sellado. Solo tú tienes la llave."*

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Language-Java-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28?style=flat-square&logo=firebase&logoColor=black)
![AES-256](https://img.shields.io/badge/Encryption-AES--256--GCM-1A56DB?style=flat-square)
![TFG](https://img.shields.io/badge/Proyecto-TFG%202%C2%BA%20DAM-8B5CF6?style=flat-square)

</div>

---

## ¿Qué es Clef?

Clef es un gestor de contraseñas nativo para Android construido sobre un principio fundamental: **el servidor nunca sabe nada**.

A diferencia de los gestores tradicionales donde el proveedor podría (en teoría) acceder a tus datos, Clef implementa una arquitectura **Zero-Knowledge (ZK)** con **Cifrado del Lado del Cliente**. Todo el cifrado y descifrado ocurre exclusivamente en la memoria RAM del teléfono del usuario. Firebase actúa como un disco duro ciego que almacena archivos incomprensibles sin tu Contraseña Maestra.

---

## 🚀 Stack Tecnológico

| Capa | Tecnología | Motivo |
|---|---|---|
| Plataforma | Android (Java Nativo) | Control total sobre gestión de memoria |
| UI | Material Design 3 + XML | Estándar moderno de Google |
| Auth | Firebase Authentication (Google Sign-In) | Delega 2FA y verificación de email en Google |
| Base de datos | Firebase Cloud Firestore | Solo almacena blobs cifrados |
| Cifrado | AES-256-GCM (`javax.crypto`) | AEAD: confidencialidad + integridad sin librerías externas |
| KDF | PBKDF2WithHmacSHA256 (230.000 iter.) | Frena ataques de fuerza bruta. Nativo en JCA |
| Entropía | `SecureRandom` | IVs y Salts criptográficamente seguros |
| Serialización | GSON | Convierte `Vault` → JSON → bytes antes de cifrar |

---

## 🧠 Arquitectura de Seguridad

### El modelo de las tres piezas

La arquitectura ZK de Clef separa la **llave** de la **cerradura** en tres elementos independientes que viven en Firestore:

```
                    ┌─────────────────────────────────────────────┐
                    │            LO QUE VIVE EN FIRESTORE         │
                    │                                              │
                    │  Salt ──────────────────────────────────┐   │
                    │                                         │   │
                    │  [ CAJA A ]  DEK cifrada con KEK-Master │   │
                    │             (se abre con tu contraseña) │   │
                    │                                         │   │
                    │  [ CAJA B ]  DEK cifrada con KEK-PUK    │   │
                    │             (se abre SOLO con el PUK)   │   │
                    │                                         │   │
                    │  [ BÓVEDA ]  Credenciales cifradas      │   │
                    │             con la DEK                  │   │
                    └─────────────────────────────────────────┘   │
                                                                   │
              Firebase ve 4 blobs cifrados.  Firebase sabe: nada. ┘
```

> **Distinción clave:** Las Cajas A y B solo contienen **la llave (DEK)**. La Bóveda contiene **los datos reales**. Nadie puede abrir la Bóveda sin pasar primero por una de las dos cajas.

### Cómo encajan las piezas

```
Contraseña Maestra + Salt
         │
         ▼  PBKDF2 · 230.000 iteraciones · ~400ms
       KEK-Master
         │
         │ cifra
         ▼
DEK ──► [ CAJA A ]  ◄── uso diario
 │
 │  (al registrarse, también se cifra con KEK-PUK)
 ▼
DEK ──► [ CAJA B ]  ◄── solo si olvidas la contraseña
 │
 │ cifra los datos reales
 ▼
Vault JSON ──► [ BÓVEDA CIFRADA ]  ◄── vault.enc · tus contraseñas
```

---

### Código PUK — recuperación de emergencia

Al crear la Contraseña Maestra, Clef genera un **código PUK de un solo uso** (similar al PUK de una SIM). Este código deriva su propia `KEK-PUK` que se usa para cifrar la DEK en la **Caja B**, una segunda copia de seguridad de la llave. Se muestra **una única vez** en pantalla (`ShowPukActivity`) y nunca se almacena en texto plano en ningún servidor.

### Auto-Lock de 60 segundos

Cuando Clef pasa a segundo plano, `ClefApp` (clase `Application`) inicia un cronómetro. Si el usuario no regresa en 60 segundos, la DEK es sobrescrita en memoria y la bóveda se bloquea automáticamente. Al volver, se solicita la Contraseña Maestra o huella dactilar.

---

## 📁 Estructura del Proyecto

```
com.tuempresa.clef/
│
├── ClefApp.java                       # Application global. Gestiona el Auto-Lock de 60s.
│
├── ui/
│   ├── auth/
│   │   ├── SplashActivity.java        # Pantalla de carga. Router: ¿a qué pantalla ir?
│   │   └── LoginActivity.java         # Google Sign-In + solicitud de Contraseña Maestra
│   │
│   ├── setup/                         # Flujo de primer uso
│   │   ├── CreateMasterActivity.java  # El usuario crea su Contraseña Maestra
│   │   └── ShowPukActivity.java       # Muestra el PUK UNA SOLA VEZ. Pantalla de advertencia.
│   │
│   ├── recovery/                      # Flujo de recuperación de emergencia
│   │   └── RecoverVaultActivity.java  # El usuario introduce el PUK para recuperar acceso
│   │
│   ├── dashboard/                     # Pantalla principal (día a día)
│   │   ├── MainActivity.java          # Lista de credenciales con RecyclerView
│   │   ├── VaultAdapter.java          # Adapter que pinta cada credencial en la lista
│   │   └── AddItemDialog.java         # Diálogo para añadir/editar una credencial
│   │
│   └── settings/
│       └── SettingsActivity.java      # Cerrar sesión, cambiar tiempo de bloqueo, etc.
│
├── crypto/
│   ├── CryptoUtils.java               # Motor: AES-256-GCM, PBKDF2, generación de Salt/IV
│   └── KeyManager.java                # Lógica de Key Wrapping: envuelve y desenvuelve la DEK
│
├── data/
│   ├── model/
│   │   ├── Credential.java            # POJO: título, usuario, contraseña, URL, notas
│   │   └── Vault.java                 # Contiene List<Credential>. GSON lo serializa a JSON
│   │
│   ├── local/
│   │   └── FileManager.java           # Lee y escribe vault.enc en la memoria interna
│   │
│   └── remote/
│       └── FirebaseManager.java       # Sube y baja Caja A, Caja B, Salt y Bóveda a Firestore
│
└── utils/
    ├── SessionManager.java            # Cronómetro de sesión para el Auto-Lock de 60s
    ├── PasswordGenerator.java         # Genera contraseñas fuertes aleatorias
    └── ClipboardHelper.java           # Copia al portapapeles y programa su borrado a los 45s
```

---

## 🔐 Flujos de Seguridad

### Registro (Primer uso)

```
1. Usuario inventa "Contraseña Maestra"
2. SecureRandom genera Salt (32B) y DEK aleatoria (32B)
3. PBKDF2(contraseña, salt, 230.000)  → KEK-Master
4. PBKDF2(puk_generado, salt, 230.000) → KEK-PUK
5. AES-GCM(DEK, KEK-Master) → Caja A  ─┐
6. AES-GCM(DEK, KEK-PUK)   → Caja B  ─┤─► Firestore
7. AES-GCM(Vault JSON, DEK) → Bóveda  ─┘
8. PUK se muestra UNA VEZ en pantalla. Nunca se persiste.
9. KEK-Master, KEK-PUK y DEK son sobrescritas en RAM.
```

### Login diario

```
1. Firestore descarga: Salt + Caja A + Bóveda Cifrada
2. Usuario introduce "Contraseña Maestra"
3. PBKDF2(contraseña, salt, 230.000) → KEK-Master  (~400ms)
4. AES-GCM-Decrypt(Caja A, KEK-Master) → DEK
   └── Si la contraseña es incorrecta: AEADBadTagException → Error controlado
5. AES-GCM-Decrypt(Bóveda, DEK) → JSON → Vault cargado en RAM
6. KEK-Master sobrescrita en RAM. DEK permanece en SessionManager hasta Auto-Lock.
```

> ℹ️ **La Caja B no interviene en el login diario.** Solo se descarga en el flujo de recuperación con PUK.

### Recuperación con PUK

```
1. Firestore descarga: Salt + Caja B
2. Usuario introduce su código PUK de 24 caracteres
3. PBKDF2(puk, salt, 230.000) → KEK-PUK
4. AES-GCM-Decrypt(Caja B, KEK-PUK) → DEK
   └── Si el PUK es incorrecto: AEADBadTagException → Error controlado
5. El usuario establece una nueva Contraseña Maestra
6. PBKDF2(nueva_contraseña, salt, 230.000) → nueva KEK-Master
7. AES-GCM(DEK, nueva KEK-Master) → nueva Caja A sobreescribe la anterior
8. El PUK queda invalidado. KEK-PUK sobrescrita en RAM.
```

### Auto-Lock

```
onStop() → ClefApp inicia Handler(60s)
               │
        ¿Vuelve el usuario?
         ├── SÍ (< 60s) → Handler cancelado. Sesión continúa.
         └── NO (≥ 60s) → SessionManager.clearDek()  [sobrescribe byte[] con ceros]
                              └── Al abrir: solicita Contraseña Maestra / huella
```

---

## ⚙️ Configuración para Desarrolladores

### Prerrequisitos

- Android Studio Ladybug / Koala o superior
- JDK 17+
- Cuenta Google para Firebase

### Pasos

```bash
# 1. Clonar
git clone https://github.com/tu-usuario/clef.git
cd clef
```

2. Abre el proyecto en **Android Studio**
3. Crea un proyecto en [Firebase Console](https://console.firebase.google.com)
4. Añade una app Android con el package: `com.tuempresa.clef`
5. Descarga `google-services.json` → colócalo en `app/`
6. Activa en Firebase Console:
   - ✅ **Authentication** → Proveedor Google
   - ✅ **Cloud Firestore** → Modo producción
7. Aplica las reglas de Firestore del archivo `firestore.rules`

```bash
# 8. Compilar en debug
./gradlew assembleDebug
```

### Reglas de Firestore

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null
                         && request.auth.uid == userId;
    }
  }
}
```

---

## ⚠️ Consideraciones de Seguridad

> **La Contraseña Maestra no es recuperable por la aplicación.** Esto es una característica de diseño, no un fallo. El código PUK es el único mecanismo de recuperación y también es de un solo uso.

- Los IVs son únicos por cada operación de cifrado y nunca se reutilizan.
- El portapapeles se limpia automáticamente a los **45 segundos** tras copiar una contraseña.
- Las claves en memoria (`byte[]`, `char[]`) se sobrescriben con ceros (`Arrays.fill(key, (byte) 0x00)`) inmediatamente tras su uso.
- `allowBackup="false"` en el Manifest impide que Android Backup exponga datos cifrados.
- El tráfico HTTP en claro está deshabilitado mediante `network_security_config.xml`.

---

---

## 📄 Licencia

Proyecto de Fin de Grado (TFG) — 2º DAM  
Desarrollado con fines académicos.

---

<div align="center">
  <sub>Construido con 🔐 y mucho café · La llave siempre estuvo contigo</sub>
</div>
