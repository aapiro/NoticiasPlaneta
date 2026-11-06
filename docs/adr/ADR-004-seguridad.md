# ADR-004 — Modelo de seguridad

**Estado:** aceptada · **Fecha:** 2026-11-06

## Contexto
Dos superficies muy distintas: lectura pública anónima y backoffice con capacidad de
publicar y despublicar. Tratarlas igual es el error habitual.

## Decisión
1. **Dos cadenas de seguridad** con `securityMatcher`: pública y `/admin/**`.
2. **Passkeys (WebAuthn)** como método principal; **TOTP** obligatorio para `CHIEF` y `ADMIN`.
   Módulo verificado: `spring-security-webauthn` 7.1.2.
3. **Sesión server-side**, cookie `HttpOnly; Secure; SameSite=Lax`. Sin JWT en el navegador.
4. **Autorización en el dominio**, no solo en el controlador (roles + ámbito de sección).
5. **CSRF activo**; CSP con nonce y sin `unsafe-inline`.
6. **Auditoría append-only** de toda escritura, con `actor_type ∈ {human, agent}`.
7. **Sin contraseñas, sin SMS, sin preguntas de seguridad.**

## Alternativas
| Alternativa | Por qué no |
|---|---|
| JWT en localStorage | XSS lo convierte en robo de sesión; no aporta nada en SSR |
| Solo contraseña + TOTP | Peor UX y phishable frente a passkeys (Radar Vol 35: Adopt) |
| OAuth con Google para la redacción | Dependencia externa para el activo más crítico |

## Consecuencias
- Positivas: phishing estructuralmente imposible en el login.
- Negativas: la recuperación de cuenta exige proceso humano con doble aprobación.
