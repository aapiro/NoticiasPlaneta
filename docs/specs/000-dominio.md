# 000 — Lenguaje ubicuo, agregados y mapa de módulos

## Lenguaje ubicuo

| Término | Definición | NO es |
|---|---|---|
| **Artículo** | Pieza editorial con estado, autor y URL canónica | Una noticia externa |
| **Fuente** | Origen identificable (medio, institución, testigo) que respalda un hecho | Un enlace suelto |
| **Elemento de ingesta** | Item crudo de un RSS/API, antes de ser artículo | Un artículo |
| **Tema** | Hilo editorial con línea temporal propia | Una sección |
| **Sección** | Categoría estable de navegación (España, Economía) | Un tema |
| **Revisión** | Instantánea inmutable del contenido de un artículo | Un borrador |
| **Transición** | Cambio de estado auditado | Una edición |
| **Contraste** | Nº de fuentes independientes que confirman un hecho | Nº de enlaces |

## Agregados y raíces

| Agregado | Raíz | Invariantes |
|---|---|---|
| **Article** | `Article` | Un artículo publicado siempre tiene slug único por idioma y al menos una revisión |
| **IngestItem** | `IngestItem` | Deduplicado por `(sourceId, externalId)` y por huella de contenido |
| **Topic** | `Topic` | La línea temporal se ordena por fecha de evento, no de publicación |
| **MediaAsset** | `MediaAsset` | `altText` obligatorio antes de asociarse a un artículo publicado |
| **Account** | `Account` | Un `AUTHOR` no publica; un `EDITOR` solo en sus secciones asignadas |

## Estados del artículo

```
draft → in_review → approved → scheduled → published → archived
  ↑         │           │
  └─────────┴───────────┘  (rechazo o corrección)
```

Regla dura: **ningún agente puede ejecutar `publish`**. Su techo es `schedule_publish`, que
requiere aprobación humana o una política explícita registrada.

## Eventos de dominio (versionados, `v1`)

| Evento | Cuándo | Consumidores |
|---|---|---|
| `IngestItemReceived.v1` | Llega un item nuevo | news (crear borrador) |
| `ArticleDrafted.v1` | Se crea un borrador | ai (enriquecer) |
| `ArticleEnriched.v1` | Termina el enriquecido | news (estado), search (indexar) |
| `ArticleReadyForReview.v1` | Pasa a revisión | notify, admin |
| `ArticleScheduled.v1` | Se programa | publish |
| `ArticlePublished.v1` | Se publica | search, notify, cdn, sitemap |
| `ArticleUnpublished.v1` | Se retira | search, cdn, notify |
| `MediaUploaded.v1` | Commit de medios | ai (alt text sugerido) |

## Mapa de módulos (monolito modular)

```
com.noticiasplaneta
├── shared/        kernel: ids, errores, eventos base, reloj, paginación
├── ingest/        fuentes, fetch, normalización, deduplicación
├── news/          Article, Revision, estados, publicación
├── taxonomy/      Section, Topic, línea temporal
├── media/         MediaAsset, presign, commit, biblioteca
├── identity/      Account, roles, passkeys, sesiones
├── ai/            enriquecido, resumen, embeddings (puerto, sin SDK en dominio)
├── search/        índice híbrido (tsvector + pgvector), relacionados
├── notify/        brief diario, avisos internos
├── mcp/           adaptador de entrada: herramientas para agentes
└── admin/         backoffice Thymeleaf + htmx
```

Cada módulo: `domain/`, `application/` (`port/in`, `port/out`, `service/`), `adapter/in`, `adapter/out`.

**Regla de frontera (ArchUnit):** un módulo solo puede importar `shared` y las
interfaces de `port/in` de otros módulos. Nunca su `domain` ni su `adapter`.

## Contextos delimitados reales

Solo dos: **Redacción** (ingest, news, taxonomy, media, identity, admin) y
**Descubrimiento** (search, notify). Comparten `news` como proveedor de hechos.
No inventar más: no hay equipos separados que los justifiquen.
