# ADR-013 · Publicación por lotes desde la bandeja (F5)

**Estado:** aceptado e implementado
**Fecha:** 2026-11-26
**Contexto:** F5 del plan de fases. Con 3230 borradores y 19 publicados, publicar de
uno en uno es el cuello de botella real del portal: no falta IA, falta poder pulsar
«publicar» veinte veces con un clic.

## El problema

La máquina de estados (`ArticleState`) no tiene la arista `DRAFT → PUBLISHED`. El
camino editorial es:

```
DRAFT → IN_REVIEW → APPROVED → SCHEDULED → PUBLISHED
```

Publicar en lote tiene, por tanto, dos tentaciones malas:

1. `update article set state = 'PUBLISHED' where id = any(...)`. Es lo que hace
   `scripts/edicion-diaria.sh` como atajo manual, y no sirve aquí: no pasa por
   `TransitionPolicy`, no fija `published_at`, no emite `ArticleStateChanged` y no
   escribe auditoría. El outbox y el enriquecido no se enteran.
2. Añadir `DRAFT>PUBLISHED` a `TRANSITIONS`. Se carga el flujo de revisión y
   aprobación para todo el mundo, también para el que sí lo usa.

## Decisión

**El agregado recorre la máquina; la acción editorial es una.**

`Article.publishNow(actor, now)`:

1. autoriza con `TransitionPolicy.assertCanPublishNow` (agente no publica, solo
   `CHIEF`/`ADMIN`, estado no terminal y alcanzable);
2. calcula el camino con `ArticleState.pathTo(PUBLISHED)` —BFS sobre las aristas
   que la máquina ya admite— y aplica **cada paso** con `transitionTo`, que vuelve
   a autorizar en `TransitionPolicy`;
3. deja `scheduled_at` a `null`: se publica ahora, no queda una programación
   residual que el job de F06 pudiera volver a procesar.

Si mañana se quita una arista del flujo editorial, el lote deja de publicar (se
informa del motivo) en vez de saltársela. No hay ninguna arista nueva.

### Auditoría: una entrada por artículo, no por paso interno

`audit_log` recibe **una fila por artículo**, con `action = ARTICLE_BATCH_PUBLISHED`,
`entity = Article`, `entity_id` del artículo, `before_state` real (normalmente
`DRAFT`) y `after_state = PUBLISHED`. Es lo que pide la aceptación de F5 (20
borradores → 20 entradas) y lo que sirve para reconstruir qué se publicó: los
pasos intermedios son un detalle de implementación, no decisiones editoriales
distintas.

Consecuencia asumida: una publicación por lotes y una publicación paso a paso
dejan rastros distintos (1 fila frente a 4). Se distinguen por `action`; si algún
día hace falta reconstruir el flujo paso a paso de un artículo concreto, el camino
existe en su historial de estados (`article_revision` no lo guarda, pero el evento
sí lo llevó al log).

### El lote no es una transacción única

Un artículo bloqueado (ya publicado, archivado, id inexistente) **no arrastra al
resto**. Cada artículo se guarda con la transacción de `ArticleRepository.save`,
igual que una transición individual, y el resultado vuelve con la forma:

```java
BatchPublishResult(int requested, List<Published> published, List<Skipped> skipped)
```

La bandeja pinta «N publicados · M omitidos de T seleccionados» y la lista de
omitidos con su motivo en castellano. Si el actor no puede publicar en absoluto
(no es `CHIEF`/`ADMIN`), el lote se rechaza **entero** con 403 antes de tocar
nada: no tiene sentido devolver 20 «omitidos por permiso».

## Interfaz

```
GET  /admin                        bandeja con casilla por artículo
POST /admin/articulos/publicar-lote   ids=... | todos=pagina + filtros (estado, seccion, q)
```

Sesión y CSRF obligatorios: la ruta cae en la cadena `adminChain` de
`SecurityConfiguration` (`/admin/**`).

- **Casilla por artículo** en cada fila.
- **«Seleccionar todo de la página»**: con JavaScript marca las 40 casillas; sin
  JavaScript, el propio checkbox viaja como `todos=pagina` y el servidor recalcula
  esa página con los mismos filtros. El lote funciona sin JS, que es la norma del
  panel (Thymeleaf, sin runtime).
- **Tope**: 100 ids por petición (`PublishBatchUseCase.MAX_IDS`). Más que eso es un
  POST que nadie ha revisado; se rechaza con 422 en vez de truncar en silencio.
- Los ids se deduplican: marcar la casilla maestra y una fila a la vez no publica
  dos veces.

## Lo que este ADR no resuelve

- **Outbox.** El lote no encola `ArticlePublished.v1`, igual que una transición
  individual desde el panel (`TransitionArticleService` tampoco lo hace). Hoy el
  relay solo sirve al camino programado y `SearchIndexPort` es un no-op, así que no
  hay efecto perdido; si la indexación pasa a ser real, el sitio para encolar es
  `PublishBatchService` y el de la transición individual a la vez, no solo uno.
- **Correlación del lote.** `audit_log` no tiene columna de lote: las 20 filas se
  agrupan por `actor_id` + `at`. Añadir un `correlation_id` obliga a tocar
  `AuditEntry` y todos sus llamantes; se hará cuando exista una segunda acción por
  lotes (por ejemplo archivar en masa) y compense el cambio.
- **Imágenes y calidad.** El lote publica lo que se le marca, sin comprobar foto ni
  sección activa: esa selección la hace el editor en la bandeja. La edición
  automática (F6) sí tiene sus propias reglas duras.

## Verificación

```
./mvnw -o test     # 232 tests, 0 fallos (2 omitidos), incluida F5 aceptación
```

`PanelLoteAcceptanceTest` (Postgres real con Testcontainers, login real por el
filtro del panel):

| Escenario | Comprobado |
|---|---|
| 20 borradores → 20 publicados | 20 en `PUBLISHED`, portada con las piezas, **20** filas en `audit_log` con el mismo actor y 20 `entity_id` distintos |
| Resumen | publicados y omitidos con motivo («ya está publicado», «no existe», «identificador no válido») |
| «Toda la página» | publica exactamente lo que la bandeja muestra con esos filtros |
| Autor (AUTHOR) | 403 y **ni un** artículo cambia de estado |
| Sin sesión / sin CSRF | 302 al login / 403, sin cambios |

Unitarios: `PublicacionPorLoteTest` (camino, estados terminales, agente, editor) y
`PublishBatchServiceTest` (20 de golpe, deduplicación, motivos, tope, rechazo por
rol).
