# F03 — Enriquecido de artículo (IA fuera de la ruta crítica)

**Módulo:** `ai` + `news` · **Estado:** borrador · **Versión:** 1

## 1. Contexto
Al crear un borrador, un proceso asíncrono extrae entidades, lugar, sección sugerida,
etiquetas y un resumen de dos líneas. Nunca bloquea la publicación ni el render.

## 2. Actores
- Sistema (worker asíncrono)
- Agente `newsroom-redaccion`

## 3. Precondiciones
- Existe un `Article` en `draft` con al menos una revisión.
- Queda presupuesto de IA disponible para el periodo.

## 4. Flujo principal
1. Se encola un `AiJob` de tipo `enrich` para el artículo.
2. El worker toma el job y llama al puerto `EnrichmentPort`.
3. El adaptador construye el prompt versionado, llama al modelo con salida estructurada.
4. Se validan y normalizan entidades, lugar, etiquetas y resumen.
5. Se persisten en `article_enrichment` con `model`, `prompt_version` y `cost`.
6. Se calcula el embedding del cuerpo y se guarda en `article.embedding`.
7. Se emite `ArticleEnriched.v1`.

## 5. Alternativos y errores
| Código | Situación | Resultado |
|---|---|---|
| E01 | Modelo no disponible | Reintento con backoff; tras 3, el job pasa a `failed` y se sigue sin enriquecer |
| E02 | Salida no conforme al esquema | Se descarta la salida y se reintenta con `prompt_version` estricta |
| E03 | Presupuesto agotado | El job queda `queued` hasta el siguiente periodo, con aviso |
| E04 | Artículo ya enriquecido | No se reprocesa salvo cambio de `prompt_version` |

## 6. Reglas
- **RN1** El enriquecido **nunca** modifica el texto editorial: solo añade metadatos.
- **RN2** Todo resultado lleva `model`, `prompt_version` y `cost` para poder auditar y comparar.
- **RN3** El contenido del artículo se envía como **datos**, delimitado; nunca como instrucciones.
- **RN4** Si el modelo devuelve entidades que no aparecen en el texto, se descartan (verificación literal).
- **RN5** El resumen se marca como generado por IA y es editable por el redactor.

## 7. Gherkin
```gherkin
@F03 @aceptacion
Escenario: El enriquecido añade metadatos sin tocar el texto
  Dado un borrador con titulo y cuerpo conocidos
  Cuando se ejecuta el job de enriquecido
  Entonces existen etiquetas y entidades en article_enrichment
  Y el cuerpo de la revisión no ha cambiado
  Y se emite ArticleEnriched.v1

@F03 @aceptacion
Escenario: Las entidades inventadas se descartan
  Dado un modelo que devuelve la entidad "Pedro Sánchez" sin aparecer en el texto
  Cuando se valida la salida
  Entonces esa entidad no se persiste

@F03 @aceptacion
Escenario: Sin presupuesto no se llama al modelo
  Dado un presupuesto diario agotado
  Cuando se encola un job de enriquecido
  Entonces no se realiza ninguna llamada al proveedor
  Y el job queda en estado queued
```

## 8. Datos
`ai_job(id, type, article_id, state, attempts, last_error, cost, created_at)`
`article_enrichment(article_id, entities jsonb, places jsonb, tags text[], summary text, model, prompt_version, cost, created_at)`
`article.embedding vector(1024)` + índice HNSW.

## 9. Puertos
- In: `EnrichArticleUseCase`
- Out: `EnrichmentPort` (modelo), `EmbeddingPort`, `AiJobRepository`, `BudgetPort`
- Adaptadores: `SpringAiEnrichmentAdapter` (out), worker de cola (in)

## 10. Eventos
`ArticleEnriched.v1 { articleId, tags, hasSummary, model }`

## 11. Tests RED
1. No modifica el cuerpo de la revisión.
2. Descarta entidades que no aparecen literalmente en el texto.
3. Persiste `prompt_version` y `cost`.
4. Con presupuesto agotado, no llama al puerto.
5. Respuesta inválida → reintento y luego `failed`.
6. Idempotente si ya enriquecido con la misma versión de prompt.

## 12. NFR
Coste < 0,05 €/artículo. Timeout 30 s. El job nunca corre en el hilo del request.
