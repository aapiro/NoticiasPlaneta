# F01 — Ingesta de fuentes

**Módulo:** `ingest` · **Estado:** borrador · **Versión:** 1

## 1. Contexto
Un agente o un cron pide al sistema que lea las fuentes activas y convierta lo nuevo en
elementos de ingesta deduplicados. Es la puerta de entrada del contenido externo.

## 2. Actores
- **Sistema** (cron cada 15 min) — actor principal.
- **Agente `newsroom-ingesta`** — puede dispararlo fuera de horario.
- **Redactor** — puede añadir una fuente a mano.

## 3. Precondiciones
- La fuente existe, está activa y tiene `feedUrl` válido.
- No hay una ejecución en curso para esa misma fuente (lock por `sourceId`).

## 4. Flujo principal
1. Se cargan las fuentes activas (`enabled = true`).
2. Para cada fuente se descarga el feed con timeout de 10 s y ETag/Last-Modified.
3. Se parsea y se normaliza cada item (título, cuerpo, enlace canónico, fecha, autor, medio).
4. Se calcula la huella `sha256(tituloNormalizado + cuerpoNormalizado)`.
5. Se descarta si ya existe `(sourceId, externalId)` o la misma huella.
6. Se persiste lo nuevo como `IngestItem` en estado `received`.
7. Se emite `IngestItemReceived.v1` por cada item aceptado.
8. Se registra el resultado: leídos, nuevos, duplicados, errores.

## 5. Flujos alternativos y errores
| Código | Situación | Resultado |
|---|---|---|
| E01 | Fuente responde 304 | 0 items, sin error, se actualiza `lastCheckedAt` |
| E02 | Timeout o 5xx | La fuente se marca `degraded`, **no** se desactiva; se reintenta con backoff |
| E03 | Feed malformado | Se registra `parse_error`, se mantiene el último contenido válido |
| E04 | Item sin título o sin enlace | Se descarta y se cuenta en `rejectedItems` |
| E05 | Ejecución concurrente sobre la misma fuente | La segunda sale sin hacer nada (`409`) |

## 6. Reglas de negocio
- **RN1** Una fuente que falla 5 veces seguidas pasa a `degraded` y genera aviso, no se borra.
- **RN2** La deduplicación es por `(sourceId, externalId)` **y** por huella de contenido: el
  mismo texto publicado por dos medios distintos son dos items (dos fuentes), no un duplicado.
- **RN3** Nunca se ejecuta el contenido de un feed; se trata como texto plano.
- **RN4** El tamaño de cuerpo se trunca a 100 000 caracteres.

## 7. Criterios de aceptación (Gherkin)
```gherkin
@F01 @aceptacion
Escenario: Se importan solo los items nuevos
  Dado una fuente activa con 3 items, 2 ya importados
  Cuando se ejecuta la ingesta de esa fuente
  Entonces se crea exactamente 1 IngestItem nuevo
  Y se emite 1 evento IngestItemReceived.v1

@F01 @aceptacion
Escenario: Una fuente caída no se desactiva sola
  Dado una fuente activa que responde 503
  Cuando se ejecuta la ingesta 5 veces
  Entonces la fuente queda en estado degraded
  Y se ha generado 1 aviso al perfil de operación

@F01 @aceptacion
Escenario: El mismo texto en dos medios son dos fuentes distintas
  Dado dos fuentes distintas que publican un texto idéntico
  Cuando se ejecuta la ingesta
  Entonces se crean 2 IngestItem
  Y ambos comparten la misma huella de contenido
```

## 8. Datos
`source(id, name, feed_url, enabled, state, failure_count, last_checked_at, etag, last_modified)`
`ingest_item(id, source_id, external_id, content_hash, title, body, canonical_url, published_at, state)`
Índices: `unique(source_id, external_id)`, `index(content_hash)`, `index(state, created_at)`.

## 9. Puertos y adaptadores
- **Entrada:** `IngestSourceUseCase.run(sourceId)`, `AddSourceUseCase`
- **Salida:** `SourceRepository`, `IngestItemRepository`, `FeedFetcher` (HTTP), `Clock`, `EventPublisher`
- **Adaptadores:** `HttpFeedFetcher` (out), `CronIngestJob` y `McpFetchSourceTool` (in)

## 10. Eventos
`IngestItemReceived.v1 { itemId, sourceId, canonicalUrl, publishedAt, contentHash }`

## 11. Tests TDD (lista RED inicial)
1. Item ya importado no se duplica (por externalId).
2. Item con misma huella y misma fuente no se duplica.
3. La misma huella en otra fuente sí se importa.
4. 304 no cuenta como error.
5. Timeout incrementa `failure_count`.
6. 5 fallos consecutivos → `degraded` + aviso.
7. Item sin título se descarta y se contabiliza.
8. Lock por fuente impide ejecución concurrente.

## 12. No funcional
Ingesta de 50 fuentes < 60 s. Timeout por feed 10 s. Sin escrituras fuera de transacción.
Corte de cron de Hermes: 3 min → si se excede, se divide en lotes de 10 fuentes.

## 13. Riesgos
- Feeds que cambian de formato sin avisar → necesidad de alerta por tasa de `parse_error`.
- Prompt injection en títulos/cuerpo → RN3 y saneado en el adaptador.
