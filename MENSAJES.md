Hola, aqui se mostraran los mensajes de firebase, para que todos podamos editarlos y ser conscientes de ellos

NOTA: Firebase usa HTML en sus plantillas. Pegar texto plano hace que aparezcan etiquetas <p> en el correo.
Las propuestas están escritas en HTML listo para copiar y pegar directamente en la consola de Firebase.
El enlace de acción se introduce con el botón "Insertar URL de acción" de Firebase (variable %LINK%).

---

1) Verificación de dirección de correo electrónico

Actualmente:

Nombre del remitente
no proporcionado
De
noreply@clef-efe86.firebaseapp.com
Responder a
noreply
Asunto
Verify your email for %APP_NAME%
Mensaje
Hello %DISPLAY_NAME%,

Follow this link to verify your email address.
https://clef-efe86.firebaseapp.com/__/auth/action?mode=action&oobCode=code
If you didn't ask to verify this address, you can ignore this email.
Thanks,
Your %APP_NAME% team

Propuesta:

   Nombre del remitente
   Clef
   De
   security.clef@gmail.com
   Responder a
   noreply
   Asunto
   Confirma tu dirección de correo electrónico — Clef
   Mensaje (HTML):

<p>Hola <strong>%DISPLAY_NAME%</strong>,</p>

<p>Gracias por registrarte en <strong>Clef</strong>, tu gestor de contraseñas seguro.</p>

<p>Para activar tu cuenta y empezar a proteger tus credenciales, necesitamos verificar que esta dirección de correo te pertenece. Pulsa el botón de abajo para confirmarla:</p>

<p style="text-align:center; margin:32px 0;">
  <a href="%LINK%" style="background-color:#4A90E2; color:#ffffff; padding:14px 28px; text-decoration:none; border-radius:6px; font-weight:bold; font-size:15px;">
    Confirmar correo electrónico
  </a>
</p>

<p>Este enlace caduca en <strong>24 horas</strong>. Si no lo usas en ese plazo, tendrás que solicitar uno nuevo desde la app.</p>

<p style="color:#888888; font-size:13px;">Si no has creado ninguna cuenta en Clef, ignora este correo. Tu dirección no será registrada y no recibirás más mensajes.</p>

<p>Un saludo,<br>El equipo de Clef</p>

---

2) Restablecimiento de contraseña

Actualmente:
   Nombre del remitente
   no proporcionado
   De
   noreply@clef-efe86.firebaseapp.com
   Responder a
   noreply
   Asunto
   Reset your password for %APP_NAME%
   Mensaje
   Hello,

Follow this link to reset your %APP_NAME% password for your %EMAIL% account.
https://clef-efe86.firebaseapp.com/__/auth/action?mode=action&oobCode=code
If you didn't ask to reset your password, you can ignore this email.
Thanks,

Your %APP_NAME% team

Propuesta:

   Nombre del remitente
   Clef
   De
   noreply@clef-efe86.firebaseapp.com
   Responder a
   noreply
   Asunto
   Solicitud de restablecimiento de contraseña — Clef
   Mensaje (HTML):

<p>Hola,</p>

<p>Hemos recibido una solicitud para restablecer la contraseña de acceso a <strong>Clef</strong> asociada a la cuenta <strong>%EMAIL%</strong>.</p>

<p>Si fuiste tú quien lo solicitó, pulsa el botón de abajo para elegir una nueva contraseña:</p>

<p style="text-align:center; margin:32px 0;">
  <a href="%LINK%" style="background-color:#4A90E2; color:#ffffff; padding:14px 28px; text-decoration:none; border-radius:6px; font-weight:bold; font-size:15px;">
    Restablecer contraseña
  </a>
</p>

<p>Este enlace es válido durante <strong>1 hora</strong>. Pasado ese tiempo deberás solicitar uno nuevo desde la pantalla de inicio de sesión.</p>

<p style="background-color:#fff8e1; border-left:4px solid #f5a623; padding:12px 16px; border-radius:4px;">
  ⚠ Si no has solicitado este restablecimiento, ignora este correo. Tu contraseña actual no se verá modificada. Si crees que alguien está intentando acceder a tu cuenta, te recomendamos cambiar tu contraseña lo antes posible.
</p>

<p style="color:#888888; font-size:13px;">Recuerda que Clef nunca te pedirá tu contraseña maestra por correo electrónico ni por ningún otro canal.</p>

<p>Un saludo,<br>El equipo de Clef</p>

---

3) Cambio de correo electrónico

Nuestra app no cuenta con esto, y la verdad no lo meteria, pero el mensaje para este caso actualmente es:
   Nombre del remitente
   no proporcionado
   De
   noreply@clef-efe86.firebaseapp.com
   Responder a
   noreply
   Asunto
   Your sign-in email was changed for %APP_NAME%
   Mensaje
   Hello %DISPLAY_NAME%,

Your sign-in email for %APP_NAME% was changed to %NEW_EMAIL%.

If you didn't ask to change your email, follow this link to reset your sign-in email.

https://clef-efe86.firebaseapp.com/__/auth/action?mode=action&oobCode=code

Thanks,

Your %APP_NAME% team

Propuesta:

   Nombre del remitente
   Clef
   De
   noreply@clef-efe86.firebaseapp.com
   Responder a
   noreply
   Asunto
   Tu correo de acceso a Clef ha sido modificado
   Mensaje (HTML):

<p>Hola <strong>%DISPLAY_NAME%</strong>,</p>

<p>Te informamos de que el correo electrónico de acceso a tu cuenta de <strong>Clef</strong> ha sido actualizado a <strong>%NEW_EMAIL%</strong>.</p>

<p>Si fuiste tú quien realizó este cambio, no es necesario que hagas nada más. El cambio quedará activo en cuanto confirmes la nueva dirección desde el enlace de verificación que habrás recibido en <strong>%NEW_EMAIL%</strong>.</p>

<p style="background-color:#fdecea; border-left:4px solid #e53935; padding:12px 16px; border-radius:4px;">
  ⚠ <strong>Si no has solicitado este cambio, actúa de inmediato:</strong> alguien podría haber accedido a tu cuenta sin tu autorización. Pulsa el botón de abajo para cancelar la modificación y recuperar tu dirección original:
</p>

<p style="text-align:center; margin:32px 0;">
  <a href="%LINK%" style="background-color:#e53935; color:#ffffff; padding:14px 28px; text-decoration:none; border-radius:6px; font-weight:bold; font-size:15px;">
    Cancelar cambio de correo
  </a>
</p>

<p style="color:#888888; font-size:13px;">Si tienes cualquier duda o necesitas ayuda, contacta con nuestro equipo de soporte.</p>

<p>Un saludo,<br>El equipo de Clef</p>

---

4) Notificacion sobre la Inscripcion:

   Nombre del remitente
   no proporcionado
   De
   noreply@clef-efe86.firebaseapp.com
   Responder a
   noreply
   Asunto
   You've added 2 step verification to your %APP_NAME% account.
   Mensaje
   Hello %DISPLAY_NAME%,

Your account in %APP_NAME% has been updated with %SECOND_FACTOR% for 2-step verification.

If you didn't add this 2-step verification, click the link below to remove it.

https://clef-efe86.firebaseapp.com/__/auth/action?mode=action&oobCode=code

Thanks,

Your %APP_NAME% team

Propuesta:

   Nombre del remitente
   Clef
   De
   noreply@clef-efe86.firebaseapp.com
   Responder a
   noreply
   Asunto
   Verificación en dos pasos activada en tu cuenta — Clef
   Mensaje (HTML):

<p>Hola <strong>%DISPLAY_NAME%</strong>,</p>

<p>Te confirmamos que la verificación en dos pasos (<strong>%SECOND_FACTOR%</strong>) ha sido activada correctamente en tu cuenta de <strong>Clef</strong>. A partir de ahora, cada vez que inicies sesión se te pedirá un paso adicional de verificación para proteger mejor tu acceso.</p>

<p>Esta medida añade una capa extra de seguridad a tu bóveda de contraseñas. Te recomendamos guardar tus códigos de recuperación en un lugar seguro por si en algún momento no tienes acceso a tu método de verificación.</p>

<p style="background-color:#fdecea; border-left:4px solid #e53935; padding:12px 16px; border-radius:4px;">
  ⚠ <strong>Si no has sido tú quien ha activado esta opción</strong>, alguien podría haber accedido a tu cuenta. Pulsa el botón de abajo para eliminar este método de verificación y proteger tu cuenta:
</p>

<p style="text-align:center; margin:32px 0;">
  <a href="%LINK%" style="background-color:#e53935; color:#ffffff; padding:14px 28px; text-decoration:none; border-radius:6px; font-weight:bold; font-size:15px;">
    Eliminar verificación en dos pasos
  </a>
</p>

<p style="color:#888888; font-size:13px;">Si necesitas ayuda, no dudes en contactar con nuestro equipo de soporte.</p>

<p>Un saludo,<br>El equipo de Clef</p>
