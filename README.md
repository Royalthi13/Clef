# 🗝️ Clef
### Gestor de Contraseñas Zero-Knowledge para Android

> *"El servidor guarda un baúl sellado. Solo tú tienes la llave."*

![Android](https://img.shields.io/badge/Android-Java-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Firestore-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![AES-256](https://img.shields.io/badge/Cifrado-AES--256--GCM-00E5FF?style=for-the-badge&logo=letsencrypt&logoColor=white)
![TFG](https://img.shields.io/badge/Proyecto-TFG%202ºDAM-6C63FF?style=for-the-badge)

---

## ¿Qué es Clef?

Clef es un gestor de contraseñas nativo para Android construido sobre un principio fundamental: **el servidor nunca sabe nada.**

A diferencia de los gestores tradicionales donde el proveedor podría (en teoría) acceder a tus datos, Clef implementa una arquitectura **Zero-Knowledge (ZK)** con **Cifrado del Lado del Cliente**. Todo el cifrado y descifrado ocurre exclusivamente en la memoria RAM del teléfono del usuario. Firebase actúa como un disco duro ciego que almacena archivos incomprensibles sin tu Contraseña Maestra.

---

## ✨ Funcionalidades

| Feature | Descripción |
|---|---|
| 🔐 **Bóveda cifrada** | AES-256-GCM. Tus contraseñas son ilegibles para cualquiera excepto tú |
| 🔑 **Generador de contraseñas** | Genera contraseñas fuertes con sliders configurables (longitud, símbolos, números). Vista previa en tiempo real |
| 🆘 **Recuperación con PUK** | Código de emergencia que se regenera tras cada uso — el PUK viejo queda completamente invalidado |
| 📋 **Portapapeles seguro** | Auto-borrado del portapapeles a los 45 segundos |
| ⏱️ **Auto-Lock** | Bloqueo automático configurable (1, 5, 15, 30 min o nunca) al pasar a segundo plano |
| 🔍 **Buscador integrado** | Filtra credenciales en tiempo real por título o usuario |
| 🗂️ **Categorías** | Banco, Redes Sociales, Trabajo, Juegos, Compras, Transporte, Ocio, Deportes, Otro |
| 🔄 **Sincronización cloud** | Opcional. Sube cifrado a Firestore solo cuando el usuario lo activa |
| 👤 **Biometría** | Desbloqueo con huella o rostro. DEK cifrada en Android Keystore por usuario |
| 🌓 **Tema claro/oscuro** | Persiste entre sesiones. Iconos adaptativos al tema |
| 🖼️ **Favicon automático** | Muestra el icono del servicio usando la URL o el nombre |

---

## 🧭 Navegación

Clef organiza sus funciones en **3 secciones** accesibles desde la barra de navegación inferior:

| Sección | Icono | Función |
|---|---|---|
| **Bóveda** | 🔐 | Lista de todas tus credenciales guardadas con búsqueda y filtro por categoría |
| **Generador** | ⚡ | Genera contraseñas con vista previa en tiempo real y configuración persistente |
| **Ajustes** | ⚙️ | Perfil, biometría, auto-lock, tema, sincronización, importar/exportar |

---

## 🚀 Stack Tecnológico

| Capa | Tecnología | Motivo |
|---|---|---|
| Plataforma | Android (Java Nativo) | Control total sobre gestión de memoria |
| UI | Material Design 3 + XML | Estándar moderno de Google |
| Animaciones | Lottie | Animación fluida en Splash screen y Login |
| Auth | Firebase Authentication (Email + Google Sign-In) | Delega 2FA y verificación en Google |
| Base de datos | Firebase Cloud Firestore | Solo almacena blobs cifrados |
| Cifrado | AES-256-GCM (`javax.crypto`) | AEAD: confidencialidad + integridad sin librerías externas |
| KDF | PBKDF2WithHmacSHA256 (230.000 iter.) | Frena ataques de fuerza bruta. Nativo en JCA |
| Entropía | `SecureRandom` | IVs y Salts criptográficamente seguros |
| Serialización | GSON | Convierte `Vault` → JSON → bytes antes de cifrar |
| Imágenes | Glide | Carga de avatares y favicons con caché |

---

## 🧠 Arquitectura de Seguridad

### El modelo de las tres piezas

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

### Cómo encajan las piezas

```
Contraseña Maestra + Salt
         │
         ▼  PBKDF2 · 230.000 iteraciones · ~400ms
       KEK-Master
         │ cifra
         ▼
DEK ──► [ CAJA A ]  ◄── uso diario

DEK ──► [ CAJA B ]  ◄── solo si olvidas la contraseña (cifrada con KEK-PUK)

Vault JSON ──► [ BÓVEDA CIFRADA ]  ◄── vault_<uid>.enc · tus contraseñas
```

### Aislamiento multi-usuario en el mismo dispositivo

Todos los ficheros sensibles se nombran con el UID de Firebase para evitar
que los datos de un usuario sean accesibles para otro en el mismo teléfono:

| Recurso | Nombre en disco |
|---|---|
| Vault local | `vault_<uid>.enc` |
| Caché de claves | `clef_key_cache_<uid>` (SharedPreferences) |
| DEK biométrica | `enc_dek_<uid>` (SharedPreferences) |
| Foto de perfil | `profile/avatar_<uid>.jpg` |

### Código PUK — recuperación de emergencia

Al crear la Contraseña Maestra, Clef genera un **código PUK de un solo uso** de 32 caracteres hex (`XXXX-XXXX-…`). Se muestra una única vez y nunca se almacena en texto plano.

**Tras usarlo para recuperar el acceso**, Clef genera automáticamente un **nuevo PUK** y una **nueva Caja B** que reemplaza a la anterior en Firebase. El PUK original queda criptográficamente invalidado — no solo marcado — porque la Caja B que cifraba ha sido sobreescrita.

---

## 📁 Estructura del Proyecto

```
com.example.clef/
│
├── ClefApp.java                        # Application global. Gestiona el Auto-Lock.
│
├── ui/
│   ├── auth/
│   │   ├── SplashActivity.java         # Pantalla de carga Lottie. Router inicial.
│   │   ├── LoginActivity.java          # Email + Google Sign-In
│   │   ├── RegisterActivity.java       # Registro con email/contraseña + verificación
│   │   └── UnlockActivity.java         # Desbloqueo con contraseña maestra o biometría.
│   │                                     Enlaza a RecoverVaultActivity si el usuario
│   │                                     olvida su contraseña maestra.
│   │
│   ├── setup/
│   │   ├── CreateMasterActivity.java   # Crea la Contraseña Maestra (indicador de fortaleza)
│   │   └── ShowPukActivity.java        # Muestra el PUK UNA SOLA VEZ. También tras recovery.
│   │
│   ├── recovery/
│   │   └── RecoverVaultActivity.java   # Recupera acceso con PUK + genera nuevo PUK
│   │
│   ├── dashboard/
│   │   ├── MainActivity.java           # Contenedor de fragments + BottomNavigationView
│   │   ├── VaultFragment.java          # Lista con búsqueda, filtro por categoría y FAB
│   │   ├── VaultAdapter.java           # RecyclerView con favicon, copiar y eliminar
│   │   ├── AddItemDialog.java          # BottomSheet para añadir/editar credenciales
│   │   └── GeneratorFragment.java      # Generador con vista previa en tiempo real
│   │
│   └── settings/
│       ├── SettingsFragment.java       # Perfil, biometría, tema, auto-lock, sync
│       ├── ProfileEditDialog.java      # Editar nombre y foto de perfil
│       └── ImportExportDialog.java     # Exportar a nube / importar desde nube (con confirmación)
│
├── crypto/
│   ├── CryptoUtils.java                # Motor: AES-256-GCM, PBKDF2, Salt/IV
│   └── KeyManager.java                 # Key Wrapping: Cajas A/B, registro, login, recovery,
│                                         generación de nuevo PUK tras recuperación
│
├── data/
│   ├── model/
│   │   ├── Credential.java             # POJO: título, usuario, contraseña, URL, notas, categoría
│   │   └── Vault.java                  # Lista<Credential>. GSON lo serializa a JSON.
│   │
│   ├── local/
│   │   └── FileManager.java            # Lee/escribe vault_<uid>.enc en memoria interna
│   │
│   ├── remote/
│   │   ├── AuthManager.java            # Google Sign-In + Email Auth + Firebase Auth
│   │   └── FirebaseManager.java        # Firestore: salt, cajaA, cajaB, vault
│   │
│   └── repository/
│       └── VaultRepository.java        # Orquesta local + remoto. updateCajaAyB() atómico.
│
└── utils/
    ├── SessionManager.java             # DEK en memoria, timer auto-lock, thread-safe
    ├── BiometricHelper.java            # Android Keystore + BiometricPrompt, scoped por UID
    ├── PasswordGenerator.java          # Generador criptográfico con SecureRandom
    ├── ClipboardHelper.java            # Copia sensible con auto-borrado a 45s
    └── ThemeManager.java               # Claro / Oscuro / Sistema, persiste en SharedPrefs
```

---

## 🔐 Flujos de Seguridad

### Registro (Primer uso)

```
1. Usuario inventa "Contraseña Maestra" (mín. 8 chars, indicador de fortaleza)
2. SecureRandom genera Salt (32B) y DEK aleatoria (32B)
3. PBKDF2(contraseña, salt, 230.000)  → KEK-Master
4. PBKDF2(puk_generado, salt, 230.000) → KEK-PUK
5. AES-GCM(DEK, KEK-Master) → Caja A  ─┐
6. AES-GCM(DEK, KEK-PUK)   → Caja B  ─┤─► Firestore (una sola escritura)
7. AES-GCM(Vault JSON, DEK) → Bóveda  ─┘
8. PUK se muestra UNA VEZ en ShowPukActivity. Nunca se persiste.
9. KEK-Master, KEK-PUK y DEK son sobrescritas en RAM (Arrays.fill → 0x00).
```

### Login diario

```
1. Intentar caché local (SharedPrefs por UID + vault_<uid>.enc)
   └── Si no existe (dispositivo nuevo): descargar de Firestore
2. Usuario introduce "Contraseña Maestra"
3. PBKDF2(contraseña, salt, 230.000) → KEK-Master  (~400ms, hilo de fondo)
4. AES-GCM-Decrypt(Caja A, KEK-Master) → DEK
   └── Contraseña incorrecta: AEADBadTagException → error controlado
5. AES-GCM-Decrypt(Bóveda, DEK) → JSON → Vault cargado en RAM
6. KEK-Master sobrescrita. DEK permanece en SessionManager hasta Auto-Lock.
```

### Recuperación con PUK

```
1. Firestore descarga: Salt + Caja B + Bóveda
2. Usuario introduce PUK (tolerante a espacios y guiones)
3. PBKDF2(puk, salt, 230.000) → KEK-PUK
4. AES-GCM-Decrypt(Caja B, KEK-PUK) → DEK
5. Usuario elige nueva Contraseña Maestra
6. PBKDF2(nueva_contraseña, salt, 230.000) → nueva KEK-Master
7. AES-GCM(DEK, nueva KEK-Master) → nueva Caja A
8. SecureRandom genera nuevo PUK → PBKDF2(nuevo_puk, salt) → nueva KEK-PUK-2
9. AES-GCM(DEK, KEK-PUK-2) → nueva Caja B
10. Caja A + Caja B se suben ATÓMICAMENTE a Firestore (un solo update)
    El PUK viejo queda criptográficamente invalidado — la Caja B que abría
    ha sido reemplazada.
11. Nuevo PUK se muestra UNA VEZ en ShowPukActivity.
```

### Auto-Lock

```
onStop() → SessionManager.startLockTimer()
               │
        ¿Vuelve antes del timeout?
         ├── SÍ → cancelLockTimer(). Sesión continúa.
         └── NO → lock(): Arrays.fill(dek, 0x00), vault = null
                    └── OnLockListener dispara en hilo principal
                         → UnlockActivity con "Tu sesión ha expirado"
```

---

## ⚙️ Configuración para Desarrolladores

### Prerrequisitos

- Android Studio Ladybug / Koala o superior
- JDK 17+
- Cuenta Google para Firebase

### Pasos

```bash
git clone https://github.com/Royalthi13/Clef.git
cd Clef
```

1. Abre el proyecto en **Android Studio**
2. Crea un proyecto en [Firebase Console](https://console.firebase.google.com)
3. Añade una app Android con el package: `com.example.clef`
4. Descarga `google-services.json` → colócalo en `app/`
5. Activa en Firebase Console:
   - ✅ **Authentication** → Proveedores: Google + Email/contraseña
   - ✅ **Cloud Firestore** → Modo producción
6. Aplica las reglas de Firestore (ver abajo)

```bash
./gradlew assembleDebug
```

### Reglas de Firestore

```
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

> **La Contraseña Maestra no es recuperable por la aplicación.** El código PUK es el único mecanismo de recuperación y también es de un solo uso — tras usarlo se genera uno nuevo automáticamente.

- Los **IVs son únicos** por cada operación de cifrado (generados con `SecureRandom`).
- El **portapapeles se limpia** a los 45 segundos. En Android 13+ el clip se marca como sensible.
- Las **claves en memoria** (`byte[]`, `char[]`) se sobrescriben con ceros (`Arrays.fill(key, (byte) 0x00)`) inmediatamente tras su uso.
- `allowBackup="false"` en el Manifest + `backup_rules.xml` impiden que Android Backup exponga datos.
- El **tráfico HTTP en claro está deshabilitado** (`network_security_config.xml`).
- **Aislamiento multi-usuario**: vault, caché de claves, DEK biométrica y foto de perfil se nombran con el UID de Firebase. Dos cuentas en el mismo dispositivo no comparten ningún fichero.
- **ProGuard/R8** incluye reglas explícitas para `Credential` y `Vault` — sin ellas GSON devuelve objetos vacíos en builds de release.

---

## 📄 Licencia

Proyecto de Fin de Grado (TFG) — 2º DAM  
Desarrollado con fines académicos.

---

<div align="center">
  Construido con 🔐 y mucho café &nbsp;·&nbsp; <i>La llave siempre estuvo contigo</i>
</div>
