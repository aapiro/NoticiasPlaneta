# F09 — Identidad, acceso y autorización del backoffice

**Módulo:** `identity` · **Estado:** borrador · **Versión:** 1

## 1. Contexto
El backoffice es el activo más sensible. Sin contraseñas: passkeys (WebAuthn) con TOTP
como segundo factor para los roles altos.

## 2. Actores
- Redactores, editores, jefes de sección, administradores.
- Agentes (identidades de servicio, sin sesión de navegador).

## 3. Precondiciones
- Cuenta creada por un `ADMIN` con rol asignado.
- Dispositivo compatible con passkeys.

## 4. Flujo principal (alta y acceso)
1. `ADMIN` invita a una cuenta con rol y secciones.
2. El usuario registra una passkey (WebAuthn `navigator.credentials.create`).
3. El backend valida el attestation y guarda la credencial pública.
4. Login: `navigator.credentials.get` → el backend valida la firma y el challenge.
5. Si el rol es `CHIEF` o `ADMIN`, se exige TOTP además.
6. Se crea sesión server-side (`HttpOnly; Secure; SameSite=Lax`), idle 30 min.
7. Toda acción sensible escribe `audit_log`.

## 5. Alternativos y errores
| Código | Situación | Resultado |
|---|---|---|
| E01 | Passkey desconocida | `401`, se ofrece recuperación por `ADMIN` (no por email) |
| E02 | TOTP incorrecto | `401` + retardo progresivo; 5 fallos → bloqueo temporal |
| E03 | Sesión expirada | `401`, redirección al login conservando la URL |
| E04 | Agente intenta login interactivo | `403` (los agentes usan token, no sesión) |

## 6. Reglas
- **RN1** Sin contraseñas. Sin SMS. Sin preguntas de seguridad.
- **RN2** La recuperación de cuenta la hace un `ADMIN` presencial o con doble aprobación.
- **RN3** El token de agente lleva scopes y `agent_id`; no puede abrir sesión de navegador.
- **RN4** Rotación de id de sesión al elevar privilegios.
- **RN5** Autorización por ámbito: `EDITOR` solo en sus secciones (`account_section`).

## 7. Gherkin
```gherkin
@F09 @aceptacion
Escenario: Un jefe de sección necesita segundo factor
  Dado una cuenta con rol CHIEF y passkey registrada
  Cuando inicia sesión correctamente con la passkey
  Entonces el sistema solicita TOTP antes de crear la sesión

@F09 @aceptacion
Escenario: Un token de agente no abre sesión web
  Dado un token válido de agente
  Cuando se usa contra /admin/login
  Entonces se rechaza con 403

@F09 @aceptacion
Escenario: Un editor no actúa fuera de sus secciones
  Dado un EDITOR asignado a "Economía"
  Cuando intenta aprobar un artículo de "Deportes"
  Entonces se rechaza con 403 OUT_OF_SCOPE
```

## 8. Datos
`account(id, email, display_name, role, state, created_at)`
`webauthn_credential(id, account_id, credential_id, public_key, sign_count, created_at)`
`account_section(account_id, section_id)`
`audit_log(...)` (ver F12)

## 9. Puertos
In: `RegisterPasskeyUseCase`, `AuthenticateUseCase`
Out: `AccountRepository`, `WebAuthnPort`, `TotpPort`, `SessionPort`

## 10. Eventos
`AccountCreated.v1`, `PasskeyRegistered.v1`, `LoginFailed.v1` (para alertas)

## 11. Tests RED
1. Registro de passkey guarda credencial y no la clave privada.
2. Login válido crea sesión.
3. CHIEF sin TOTP no entra.
4. 5 TOTP fallidos bloquean.
5. Token de agente no autentica en /admin.
6. EDITOR fuera de sección rechazado.

## 12. NFR
Login completo < 3 s percibidos. Rate limit en login y WebAuthn (bucket4j).
