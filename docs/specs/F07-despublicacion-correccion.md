# F07 — Despublicación, corrección y rectificación

**Módulo:** `news` · **Estado:** borrador · **Versión:** 1

## 1. Contexto
El flujo más importante del sistema y el que casi nadie diseña. Cuando algo está mal, la
velocidad para retirarlo vale más que cualquier feature.

## 2. Actores
- `CHIEF` / `ADMIN` — despublicar en un clic.
- Agente `newsroom-ops` — puede despublicar **solo** si se cumple una regla automática
  (más de N reportes verificados) y siempre avisando.

## 3. Precondiciones
- El artículo está `published` o `scheduled`.

## 4. Flujo principal
1. Se invoca `UnpublishArticleUseCase(articleId, reason, type)` con `type ∈ {retirada, correccion, rectificacion}`.
2. Se exige `reason` no vacío (mínimo 20 caracteres).
3. Transición a `archived` (retirada) o se mantiene publicado con nota de corrección.
4. Se escribe `audit_log` y se conserva la revisión original.
5. Se emite `ArticleUnpublished.v1` → purga de caché **inmediata** + desindexado + sitemap.
6. Si es corrección, se publica nota visible con fecha y qué se corrigió.

## 5. Alternativos y errores
| Código | Situación | Resultado |
|---|---|---|
| E01 | Sin motivo | `422 REASON_REQUIRED` |
| E02 | La purga de caché falla | Se reintenta agresivamente (1 s, 5 s, 15 s) y se alerta al humano |
| E03 | Ya archivado | Idempotente |
| E04 | Se republica después | Nueva revisión, `republished_at`, la nota de corrección se mantiene |

## 6. Reglas
- **RN1** El contenido retirado **no se borra jamás**: se archiva. Derecho a rectificar y obligación de conservar.
- **RN2** La purga de caché tiene prioridad sobre cualquier otra tarea en cola.
- **RN3** Toda corrección deja nota pública visible (`correction_note`).
- **RN4** La URL retirada responde 410 Gone (no 404) durante 90 días, con nota.
- **RN5** Un agente solo puede despublicar con regla automática explícita y aviso inmediato.

## 7. Gherkin
```gherkin
@F07 @aceptacion
Escenario: Despublicar exige motivo
  Dado un artículo publicado
  Cuando se solicita despublicar sin motivo
  Entonces se rechaza con 422 REASON_REQUIRED
  Y el artículo sigue publicado

@F07 @aceptacion
Escenario: Despublicar purga y desindexa
  Dado un artículo publicado
  Cuando se despublica con motivo válido
  Entonces el artículo queda en archived
  Y se solicita purga de caché
  Y se solicita desindexado

@F07 @aceptacion
Escenario: La URL retirada responde 410 y conserva la nota
  Dado un artículo archivado por error grave
  Cuando un lector visita su URL canónica
  Entonces la respuesta HTTP es 410
  Y se muestra la nota de retirada
```

## 8. Datos
`article.correction_note`, `article.unpublished_at`, `article.unpublish_reason`, `article.republished_at`

## 9. Puertos
In: `UnpublishArticleUseCase`, `McpUnpublishTool` · Out: `CdnPurgePort` (prioridad alta), `SearchIndexPort`, `AuditPort`

## 10. Eventos
`ArticleUnpublished.v1 { articleId, url, reasonType, at }`

## 11. Tests RED
1. Sin motivo → error.
2. Con motivo → archived + eventos.
3. Idempotente.
4. La purga va con prioridad máxima.
5. 410 durante 90 días y luego 404.
6. Republish conserva la nota.

## 12. NFR
Retirada efectiva en caché < 60 s desde la acción. Es el SLO más crítico del sistema.
