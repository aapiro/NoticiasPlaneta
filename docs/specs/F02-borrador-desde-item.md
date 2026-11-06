# F02 — Creación de borrador desde un elemento de ingesta

**Módulo:** `news` (consume `ingest`) · **Estado:** borrador · **Versión:** 1

## 1. Contexto
Un `IngestItem` se convierte en `Article` en estado `draft`, con su primera revisión
inmutable. Nada de esto es visible al público.

## 2. Actores
- Sistema (por evento `IngestItemReceived.v1`)
- Agente `newsroom-redaccion`

## 3. Precondiciones
- El `IngestItem` existe y está en estado `received`.
- No existe ya un artículo vinculado a ese item.

## 4. Flujo principal
1. Se recibe el evento o la llamada `CreateDraftFromItemUseCase`.
2. Se aplica la política de admisión: sección sugerida, idioma, descarte automático.
3. Se crea `Article` en `draft` con `language = es`, `slug` provisional.
4. Se crea `ArticleRevision` nº1 con el contenido importado y `origin = ingest`.
5. Se vincula la fuente del item como primera `ArticleSource`.
6. Se emite `ArticleDrafted.v1`.

## 5. Alternativos y errores
| Código | Situación | Resultado |
|---|---|---|
| E01 | Ya existe artículo para el item | Idempotente: se devuelve el existente |
| E02 | El item no cumple la política (sección vetada) | Se marca `discarded` con motivo |
| E03 | Título duplicado en las últimas 24 h | Se crea igualmente pero se marca `possible_duplicate` |

## 6. Reglas
- **RN1** Un artículo nace siempre en `draft`. No hay excepciones, ni para agentes ni para admins.
- **RN2** La primera revisión es inmutable.
- **RN3** El slug provisional se normaliza con el título y se hace único por idioma.
- **RN4** El origen (`ingest`, `manual`, `agent`) se guarda siempre.

## 7. Gherkin
```gherkin
@F02 @aceptacion
Escenario: Un item nuevo produce un borrador con su primera revisión
  Dado un IngestItem en estado received
  Cuando se procesa el evento IngestItemReceived.v1
  Entonces existe un Article en estado draft
  Y tiene 1 ArticleRevision con origin=ingest
  Y se emite ArticleDrafted.v1

@F02 @aceptacion
Escenario: Reprocesar el mismo evento no duplica el artículo
  Dado un IngestItem ya convertido en borrador
  Cuando se vuelve a entregar el mismo evento
  Entonces sigue existiendo un único Article para ese item
```

## 8. Datos
`article(id, language, slug, state, section_id, origin, created_at, published_at)`
`article_revision(id, article_id, number, title, body, origin, created_at, author_id)`
`article_source(id, article_id, source_id, url, kind)`
`unique(slug, language)`, `unique(article_id, number)`

## 9. Puertos
- In: `CreateDraftFromItemUseCase`, `IngestItemReceivedListener`
- Out: `ArticleRepository`, `SourceRepository`, `SlugGenerator`, `EventPublisher`
- Adaptadores: listener de eventos (in), JPA (out), `McpCreateDraftTool` (in)

## 10. Eventos
`ArticleDrafted.v1 { articleId, language, sectionId, origin }`

## 11. Tests RED
1. Crea artículo en draft con una revisión.
2. Es idempotente por `ingestItemId`.
3. Slug único: dos títulos iguales generan `titulo` y `titulo-2`.
4. Política de descarte aplica y no emite evento.
5. `origin = agent` se registra y queda auditable.

## 12. NFR
Creación < 100 ms p95. Sin acceso a red en este flujo.
