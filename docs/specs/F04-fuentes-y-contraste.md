# F04 — Vinculación de fuentes y cálculo de contraste

**Módulo:** `news` · **Estado:** borrador · **Versión:** 1

## 1. Contexto
Un hecho vale lo que valen sus fuentes. Este flujo cuenta cuántas fuentes **independientes**
respaldan un artículo y expone el número al lector ("37 fuentes contrastadas").

## 2. Actores
- Agente `newsroom-auditor` / `newsroom-redaccion`
- Redactor humano

## 3. Precondiciones
- El artículo existe en `draft` o posterior.
- La fuente referenciada existe o se crea con tipo (`oficial`, `prensa`, `testigo`, `redes`).

## 4. Flujo principal
1. Se añade una `ArticleSource` con URL, tipo y fecha de consulta.
2. Se valida que la URL responde (200/30x) y se guarda el código y `checkedAt`.
3. Se recalcula `independentSourceCount` descontando duplicados por dominio raíz.
4. Se actualiza `article.contrast_level` según umbrales.
5. Se emite `ArticleSourcesChanged.v1`.

## 5. Alternativos y errores
| Código | Situación | Resultado |
|---|---|---|
| E01 | URL caída | La fuente se guarda como `unreachable`, no cuenta para el contraste |
| E02 | Dos URLs del mismo dominio | Cuentan como una sola fuente independiente |
| E03 | Fuente marcada como no fiable | Se guarda pero con peso 0 y queda visible el motivo |

## 6. Reglas
- **RN1** El contraste cuenta **dominios raíz distintos**, no enlaces.
- **RN2** Una fuente `oficial` y una `prensa` que cita a la oficial **no** son independientes:
  se detecta por dominio y por `cites` explícito.
- **RN3** Umbrales: `low` < 2, `medium` 2–3, `high` >= 4 fuentes independientes.
- **RN4** El número mostrado al lector nunca se calcula en la plantilla: viene del dominio.

## 7. Gherkin
```gherkin
@F04 @aceptacion
Escenario: Dos enlaces del mismo medio cuentan como una fuente
  Dado un artículo con 2 URLs de "elpais.com" y 1 de "boe.es"
  Cuando se recalcula el contraste
  Entonces independentSourceCount es 2
  Y contrast_level es medium

@F04 @aceptacion
Escenario: Una fuente caída no suma
  Dado un artículo con una fuente que responde 404
  Cuando se recalcula el contraste
  Entonces esa fuente queda unreachable
  Y no incrementa el contraste
```

## 8. Datos
`article_source(article_id, source_id, url, root_domain, kind, http_status, checked_at, weight)`
`article.independent_source_count int`, `article.contrast_level text`

## 9. Puertos
In: `LinkSourceUseCase`, `RecalculateContrastUseCase` · Out: `UrlChecker`, `ArticleRepository`

## 10. Eventos
`ArticleSourcesChanged.v1 { articleId, independentSourceCount, contrastLevel }`

## 11. Tests RED
1. Mismo dominio dos veces cuenta 1.
2. 404 no cuenta.
3. Umbrales correctos en los bordes (1, 2, 3, 4).
4. Fuente no fiable tiene peso 0.
5. El contraste se persiste, no se calcula al renderizar.

## 12. NFR
Comprobación de URLs fuera del request (cola). Timeout 5 s por URL.
