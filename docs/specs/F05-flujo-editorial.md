# F05 — Flujo editorial y transiciones de estado

**Módulo:** `news` · **Estado:** borrador · **Versión:** 1

## 1. Contexto
Todo cambio de estado de un artículo es una transición explícita, autorizada y auditada.
No existe el "update" libre del campo `state`.

## 2. Actores
- `AUTHOR`: escribe, envía a revisión.
- `EDITOR`: revisa, aprueba o rechaza, en sus secciones.
- `CHIEF`: aprueba cualquier sección, programa y publica.
- `ADMIN`: todo, más gestión de cuentas.
- Agentes: solo hasta `schedule_publish` y siempre con `agent_id` en auditoría.

## 3. Precondiciones
- Cuenta activa con el rol requerido.
- El artículo está en un estado desde el que la transición es legal.

## 4. Flujo principal
1. Se invoca `TransitionArticleUseCase(articleId, target, actor, reason)`.
2. Se comprueba que la transición existe en la máquina de estados.
3. Se comprueba autorización: rol + ámbito de sección + ownership.
4. Se aplica la transición en una transacción.
5. Se escribe `audit_log` con estado anterior, nuevo, actor y motivo.
6. Se emite el evento correspondiente.
7. Se persiste una nueva `ArticleRevision` si cambió el contenido.

## 5. Alternativos y errores
| Código | Situación | Resultado |
|---|---|---|
| E01 | Transición ilegal (draft → published) | `409 ILLEGAL_TRANSITION` |
| E02 | Rol insuficiente | `403 FORBIDDEN` |
| E03 | EDITOR fuera de su sección | `403 OUT_OF_SCOPE` |
| E04 | Agente intenta `publish` | `403 AGENT_CANNOT_PUBLISH` |
| E05 | Conflicto de versión (edición concurrente) | `409 STALE_REVISION` |

## 6. Reglas
- **RN1** La máquina de estados vive en el dominio (`ArticleState.canTransitionTo`), no en el controlador.
- **RN2** `publish` exige: al menos una revisión aprobada y `contrast_level != low` **o** motivo justificado.
- **RN3** `archived` es terminal.
- **RN4** Toda transición exige `reason` cuando va hacia atrás (rechazo, corrección, despublicación).
- **RN5** Un agente jamás puede ejecutar `publish`. Comprobado en el dominio, no en el controlador.

## 7. Gherkin
```gherkin
@F05 @aceptacion
Escenario: Un autor no puede publicar
  Dado un artículo en estado in_review y una cuenta con rol AUTHOR
  Cuando intenta la transición a published
  Entonces se rechaza con 403 FORBIDDEN
  Y no se escribe ningún audit_log de publicación

@F05 @aceptacion
Escenario: Un agente no puede publicar aunque tenga permisos de escritura
  Dado un actor de tipo agente con scope schedule_publish
  Cuando intenta la transición a published
  Entonces se rechaza con 403 AGENT_CANNOT_PUBLISH

@F05 @aceptacion
Escenario: Una transición ilegal no cambia el estado
  Dado un artículo en draft
  Cuando se pide la transición a published
  Entonces se rechaza con 409 ILLEGAL_TRANSITION
  Y el estado sigue siendo draft
```

## 8. Datos
`article.state`, tabla `article_transition(id, article_id, from_state, to_state, actor_id, actor_type, reason, at)`

## 9. Puertos
In: `TransitionArticleUseCase` · Out: `ArticleRepository`, `AuthorizationService`, `AuditPort`, `EventPublisher`

## 10. Eventos
`ArticleStateChanged.v1`, `ArticlePublished.v1`, `ArticleUnpublished.v1`

## 11. Tests RED
1. Cada transición legal devuelve el estado nuevo.
2. Cada transición ilegal lanza `IllegalTransition`.
3. AUTHOR no publica.
4. EDITOR fuera de sección no aprueba.
5. Agente no publica (regla de dominio, no de controlador).
6. Toda transición escribe auditoría con motivo.
7. Rechazo exige motivo.

## 12. NFR
p95 < 50 ms. Sin llamadas de red dentro de la transacción.
