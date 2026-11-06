# F13 — Panel editorial

**Módulos:** `admin` (entrada) + `news`, `media`, `identity` · **Estado:** aprobada · **Versión:** 1

## 1. Contexto
Hoy publicar exige curl. El portal tiene 295 borradores esperando revisión y una persona
no puede trabajar así. El panel es la superficie donde un redactor revisa, edita, publica
y retira, sin tocar la API.

## 2. Actores y permisos

| Rol | Puede |
|---|---|
| `AUTHOR` | Ver sus borradores, editar, enviar a revisión |
| `EDITOR` | Todo lo anterior + aprobar/rechazar en sus secciones |
| `CHIEF` | Todo + programar, publicar, retirar, corregir |
| `ADMIN` | Todo + cuentas |

Un agente **no entra al panel**: usa MCP (F12).

## 3. Precondiciones
- Cuenta activa con segundo factor verificado en esta sesión.
- Sesión server-side vigente (idle 30 min).

## 4. Pantallas

### P1 · Acceso `/admin/login`
- Passkey como método principal; **TOTP obligatorio** para `CHIEF` y `ADMIN`.
- Sin contraseñas (ADR-004). La fase 1 admite form login **solo como deuda declarada**
  (ver ADR-010), con fecha de retirada.

### P2 · Bandeja `/admin`
- Lista de artículos por estado, con filtros: estado, sección, origen, texto.
- Orden por `created_at desc`. Paginación de 25.
- Columnas: titular, sección, estado, origen, antigüedad, fuentes.
- Contador por estado en la cabecera.

### P3 · Editor `/admin/articulos/{id}`
- Titular editable, sección, cuerpo (texto plano en fase 1).
- Vista previa con la plantilla pública real.
- Botones de transición **según permisos del actor**, no según lo que pinte la plantilla.
- Historial de auditoría del artículo al pie.
- Aviso visible si el artículo ya está publicado (editar = nueva revisión).

### P4 · Acción de retirada
- Modal con `kind` (RETIRADA / CORRECCION / RECTIFICACION) y motivo obligatorio (≥ 20 caracteres).
- Confirmación explícita: "el artículo dejará de estar visible".

## 5. Flujos alternativos y errores

| Código | Situación | Resultado |
|---|---|---|
| E01 | Sesión expirada | Redirección al login conservando la URL |
| E02 | Sin segundo factor verificado | Solo se permite la pantalla de TOTP |
| E03 | Acción no permitida por rol | 403 con mensaje, sin cambiar estado |
| E04 | Conflicto de edición (otra pestaña guardó) | 409 y aviso; nunca se pisa en silencio |
| E05 | Retirada sin motivo | 422 y no se ejecuta |

## 6. Reglas de negocio
- **RN1** El panel **no** implementa reglas propias: llama a los mismos casos de uso que la API.
  Si una regla no está en el dominio, no existe para el panel tampoco.
- **RN2** Los botones de transición se calculan con `ArticleState.canTransitionTo` y
  `TransitionPolicy`; la plantilla no decide permisos.
- **RN3** Toda acción queda en `audit_log` con el actor humano.
- **RN4** CSRF activo en todos los formularios.
- **RN5** El panel no permite publicar a un agente ni actuar en su nombre.

## 7. Criterios de aceptación (Gherkin)

```gherkin
@F13 @aceptacion
Escenario: Un redactor ve solo lo que puede tocar
  Dado una sesión con rol AUTHOR
  Cuando abre la bandeja
  Entonces ve sus borradores
  Y no ve los de otros autores

@F13 @aceptacion
Escenario: Un autor no puede publicar desde el panel
  Dado un artículo en SCHEDULED y una sesión con rol AUTHOR
  Cuando intenta publicar
  Entonces se rechaza con 403 FORBIDDEN
  Y el estado no cambia

@F13 @aceptacion
Escenario: El editor de un artículo publicado avisa de que se crea revisión
  Dado un artículo PUBLISHED
  Cuando se abre su editor
  Entonces se muestra el aviso de nueva revisión

@F13 @aceptacion
Escenario: Retirar exige motivo
  Dado un artículo publicado
  Cuando se envía la retirada sin motivo
  Entonces se rechaza con 422 REASON_REQUIRED
  Y el artículo sigue publicado

@F13 @aceptacion
Escenario: Sin segundo factor no se entra
  Dado un usuario CHIEF con passkey válida y sin TOTP verificado
  Cuando intenta abrir /admin
  Entonces se le redirige a la pantalla de TOTP
```

## 8. Datos
- `account(id, email, display_name, role, state, created_at)`
- `account_section(account_id, section_id)`
- `admin_session` — no: sesión en memoria del servidor (Spring Session se valora después)
- Reutiliza `audit_log` (sin tabla nueva).

## 9. Puertos y adaptadores
- **Entrada:** `AdminBandejaController`, `AdminArticleController`, `AdminActionController` (Thymeleaf).
- **Salida:** los casos de uso ya existentes (`ListPublishedArticlesUseCase` no basta: hace
  falta `ListArticlesByStateUseCase` y `GetArticleForEditUseCase`).
- **Nuevo en `news`:** `ListArticlesUseCase` (bandeja con filtros), `EditArticleUseCase`.

## 10. Eventos
Los mismos de F05/F07. El panel no inventa eventos.

## 11. Tests TDD (lista RED inicial)
1. Sin sesión, `/admin` redirige a login.
2. AUTHOR ve solo sus borradores.
3. AUTHOR no puede publicar (403) y no cambia estado.
4. CHIEF publica desde el panel y queda auditado.
5. Retirada sin motivo → 422.
6. Editor de artículo publicado muestra aviso de revisión.
7. CSRF: POST sin token → 403.

## 12. No funcional
- p95 < 300 ms en bandeja con 300 artículos.
- Accesible por teclado; foco visible; sin depender de JS para lo esencial.
- Sin JS salvo el necesario para passkeys.

## 13. Riesgos
- **Autenticación**: las passkeys requieren cliente WebAuthn y un autenticador. Mientras no
  estén cerradas, el backoffice NO debe exponerse a Internet.
- Edición concurrente: se resuelve con `revision_count` como token de versión.
