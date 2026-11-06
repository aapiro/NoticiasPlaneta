# Plan del panel editorial (F13)

Referencia: `docs/specs/F13-panel-editorial.md`.

## Decisión previa: cómo se autentica una persona

Es el punto que bloquea todo lo demás, así que se decide antes de escribir una línea de vista.

| Opción | Pros | Contras | Veredicto |
|---|---|---|---|
| **Passkeys (WebAuthn)** | Lo que dice ADR-004; phishing imposible | Necesita cliente JS y un autenticador real para probar; arranque en frío (¿quién registra el primer admin?) | Objetivo final |
| **Form login + TOTP** | Funciona hoy, verificable con curl, TOTP ya implementado (RFC 6238) | Contraseñas: contradice ADR-004 | **Provisional, con fecha de retirada (ADR-010)** |
| Solo API key | Trivial | No sirve para personas; sin sesión no hay UI | Descartado |

**Decisión:** fase 1 con form login + **TOTP obligatorio**, panel accesible **solo desde la red
interna**, y passkeys como fase 2 antes de exponer el backoffice a Internet. Queda escrito en
ADR-010 para que nadie lo olvide.

## Fase 1 — Esqueleto del panel (esta entrega)

| # | Entregable | Criterio de hecho |
|---|---|---|
| 1.1 | `Account` + `account_section` + bootstrap del primer ADMIN por variables de entorno | migración V10; sin credenciales en el repo |
| 1.2 | Form login en `/admin/login` con BCrypt + TOTP obligatorio | test: sin TOTP no hay sesión |
| 1.3 | Bandeja `/admin` con filtros y contadores | test: AUTHOR solo ve lo suyo |
| 1.4 | Editor `/admin/articulos/{id}` con acciones por permiso | test: AUTHOR no publica (403) |
| 1.5 | Retirada con motivo desde el panel | test: sin motivo → 422 |
| 1.6 | Auditoría visible por artículo | se ve en el editor |

## Fase 2 — Passkeys reales

- Página de login con `navigator.credentials.get`.
- Registro de passkey para usuarios ya autenticados.
- TOTP se mantiene como segundo factor de los roles altos.
- Retirada del form login (cumplir ADR-010).

## Fase 3 — Editor decente

- Cuerpo con formato (Markdown o editor ligero), no solo texto plano.
- Adjuntar medios al artículo (tabla `article_media`, hoy inexistente).
- Autosave cada 10 s y recuperación local.
- Programación de publicación con selector de fecha.

## Fase 4 — Operación

- Vista de salud: ingesta por fuente, jobs de IA, presupuesto consumido.
- Kill switch global visible.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Panel expuesto sin passkeys | No se publica en Internet hasta fase 2; en compose sigue sin puerto propio |
| Contraseñas en el repositorio | Solo hash BCrypt desde variable de entorno; `.env` fuera de git |
| Edición concurrente | `revision_count` como versión; 409 en conflicto |
| El panel duplica reglas del dominio | RN1: llama a los mismos casos de uso; ArchUnit lo vigila |

## Orden de trabajo (TDD)

Cada punto: test de aceptación rojo → unitarios del caso de uso → dominio → adaptador → plantilla.
