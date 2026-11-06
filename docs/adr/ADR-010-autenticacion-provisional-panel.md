# ADR-010 — Autenticación provisional del panel editorial

**Estado:** aceptada con fecha de retirada · **Fecha:** 2026-11-06 · **Decisores:** Tony, Dev

## Contexto

ADR-004 dice: sin contraseñas, passkeys y TOTP. Eso sigue siendo el objetivo. Pero el portal
tiene 295 borradores y ninguna forma de revisarlos sin curl, y las passkeys necesitan un
cliente WebAuthn y un autenticador real para poder verificarse de verdad.

## Decisión

**Fase 1 del backoffice:** `form login` con contraseña **BCrypt** (hash en base de datos,
nunca en claro) **+ TOTP obligatorio** para todos los roles que publican.

Condiciones que hacen esto aceptable de forma temporal:

1. El panel **no se expone a Internet**: solo accesible desde la red interna de Compose.
2. El usuario inicial se crea por **variables de entorno** (`ADMIN_EMAIL`, `ADMIN_PASSWORD_HASH`,
   `ADMIN_TOTP_SECRET`); no hay credenciales en el repositorio ni valores por defecto.
3. **TOTP no es opcional**: una contraseña filtrada no basta para entrar.
4. Fecha de retirada: **antes de exponer el backoffice a Internet**. Se sustituye por passkeys.

## Alternativas

| Alternativa | Por qué no ahora |
|---|---|
| Passkeys desde el primer día | Requiere cliente JS y autenticador; retrasa el panel sin necesidad inmediata |
| Solo TOTP sin contraseña | El TOTP es un segundo factor; como único factor es phishable |
| Dejar el panel fuera de la app (Basic Auth en Caddy) | Sin sesión no hay UI, ni auditoría por usuario, ni permisos por rol |

## Consecuencias

- Positivas: el panel existe hoy; cada acción sigue auditada con su actor; TOTP ya está probado
  contra los vectores de la RFC 6238.
- Negativas: existe una credencial reutilizable (la contraseña). Se mitiga con hash fuerte,
  bloqueo tras intentos fallidos, TOTP y no exponer el panel.
- **Deuda con vencimiento**: si el backoffice sale a Internet sin haber migrado a passkeys,
  este ADR está incumplido.
