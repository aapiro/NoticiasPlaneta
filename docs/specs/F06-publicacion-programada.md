# F06 — Publicación programada y propagación

**Módulo:** `news` + `publish` · **Estado:** borrador · **Versión:** 1

## 1. Contexto
Publicar es una transición más la propagación de efectos: purga de CDN, índice de búsqueda,
sitemap y avisos. Esa propagación no puede perderse ni duplicarse.

## 2. Actores
- `CHIEF` o humano con permiso
- Scheduler del sistema

## 3. Precondiciones
- Artículo en `approved` con revisión aprobada.
- `publish_at` en zona `Europe/Madrid`.

## 4. Flujo principal
1. Humano programa: `approved → scheduled`, con `publishAt`.
2. El scheduler, en cada tick, busca `scheduled` con `publishAt <= now`.
3. Ejecuta la transición a `published` y fija `publishedAt`.
4. En la **misma transacción** escribe un registro en `outbox`.
5. Un relay lee `outbox` y ejecuta los efectos: purga CDN, indexado, sitemap, aviso.
6. Cada efecto es idempotente y reintentable.
7. Se marca el registro de outbox como completado.

## 5. Alternativos y errores
| Código | Situación | Resultado |
|---|---|---|
| E01 | La purga de caché no responde | Reintento con backoff; el artículo ya está publicado |
| E02 | El índice de búsqueda falla | Reintento; se registra deuda y alerta si supera 15 min |
| E03 | Dos schedulers en paralelo | Lock por artículo; solo uno gana |
| E04 | Se cancela antes de publicar | `scheduled → approved`, no se emite nada |

## 6. Reglas
- **RN1** La transacción guarda estado **y** outbox: nunca uno sin el otro.
- **RN2** Los efectos son *at-least-once*; por eso cada consumidor debe ser idempotente.
- **RN3** Publicar sin purgar la CDN es un fallo visible: el lector vería contenido viejo.
- **RN4** El sitemap se regenera como máximo cada 5 minutos, aunque haya publicaciones.

## 7. Gherkin
```gherkin
@F06 @aceptacion
Escenario: Un artículo programado se publica en su hora
  Dado un artículo en scheduled con publishAt hace 1 minuto
  Cuando el scheduler ejecuta su tick
  Entonces el artículo está en published
  Y existe una entrada de outbox para ArticlePublished.v1

@F06 @aceptacion
Escenario: Si la CDN falla, el artículo sigue publicado y se reintenta
  Dado un artículo publicado y un adaptador de CDN que responde 500
  Cuando el relay procesa el outbox
  Entonces el estado del artículo sigue siendo published
  Y la entrada de outbox queda pendiente para reintento

@F06 @aceptacion
Escenario: Reprocesar el outbox no duplica el aviso
  Dado un outbox de ArticlePublished ya procesado
  Cuando se reprocesa
  Entonces no se envía un segundo aviso
```

## 8. Datos
`outbox(id, aggregate_id, event_type, payload jsonb, created_at, processed_at, attempts, last_error)`
`article.publish_at`, `article.published_at`

## 9. Puertos
In: `SchedulePublishUseCase`, `PublishScheduledJob` · Out: `OutboxRepository`, `CdnPurgePort`, `SearchIndexPort`, `SitemapPort`, `NotificationPort`

## 10. Eventos
`ArticlePublished.v1 { articleId, url, publishedAt, sectionId }`

## 11. Tests RED
1. El tick publica solo lo vencido.
2. Estado y outbox se escriben en la misma transacción (test de integración con rollback).
3. Fallo de CDN no revierte la publicación.
4. Reintento idempotente.
5. Lock impide doble publicación.

## 12. NFR
Latencia de publicación < 2 s desde `publishAt`. Purga de caché < 10 s (en fase 1 es caché local, no CDN).
