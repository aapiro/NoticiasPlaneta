# F11 — Temas, línea temporal y "desde tu última visita"

**Módulo:** `taxonomy` + `news` · **Estado:** borrador · **Versión:** 1

## 1. Contexto
El diferencial frente a un resumen de IA: continuidad editorial. Un tema acumula sus
artículos en una línea temporal y el lector ve qué ha cambiado desde su última visita.

## 2. Actores
- Lector (anónimo, sin cuenta)
- Redactor que asigna artículos a temas

## 3. Precondiciones
- El tema existe y tiene artículos publicados.

## 4. Flujo principal
1. Al publicar, el artículo se vincula a 0..n temas (manual o sugerido por IA y confirmado).
2. La página del tema muestra la línea temporal ordenada por fecha de evento.
3. La cabecera muestra "actualizado hace X" y el delta de las últimas 24 h.
4. En el cliente se guarda `lastVisitAt` (localStorage) — **sin cookies ni PII**.
5. Al volver, se piden los cambios desde `lastVisitAt` y se muestran arriba.

## 5. Alternativos y errores
| Código | Situación | Resultado |
|---|---|---|
| E01 | Primera visita (sin `lastVisitAt`) | Se muestra el resumen del tema sin delta |
| E02 | `lastVisitAt` corrupto o futuro | Se ignora y se trata como primera visita |
| E03 | Sin cambios desde la última visita | Se muestra "sin novedades desde tu última visita" |

## 6. Reglas
- **RN1** El delta se calcula en el servidor a partir de una fecha que envía el cliente; no se guarda PII.
- **RN2** Sin `lastVisitAt` no hay tracking: cero cookies para esta función.
- **RN3** La línea temporal ordena por fecha del hecho, no de publicación.
- **RN4** La fusión de temas es una decisión editorial: la IA sugiere, el humano confirma.

## 7. Gherkin
```gherkin
@F11 @aceptacion
Escenario: El delta muestra solo lo nuevo desde la última visita
  Dado un lector cuyo lastVisitAt fue hace 2 días
  Y 3 artículos publicados en el tema desde entonces
  Cuando carga la página del tema
  Entonces el bloque "desde tu última visita" contiene exactamente 3 artículos

@F11 @aceptacion
Escenario: Sin datos previos no se rompe nada
  Dado un lector sin lastVisitAt
  Cuando carga la página del tema
  Entonces se muestra la línea temporal completa
  Y no se establece ninguna cookie

@F11 @aceptacion
Escenario: Una fecha futura no genera resultados absurdos
  Dado un lastVisitAt en el futuro
  Cuando carga la página
  Entonces se trata como primera visita
```

## 8. Datos
`topic(id, slug, title, state)`, `article_topic(article_id, topic_id, confirmed_by)`
`event_date` en artículo (fecha del hecho, distinta de `published_at`).

## 9. Puertos
In: `GetTopicTimelineUseCase`, `GetDeltaUseCase` · Out: `TopicRepository`, `ArticleRepository`

## 10. Eventos
`ArticleLinkedToTopic.v1`

## 11. Tests RED
1. Delta devuelve exactamente los publicados después de `lastVisitAt`.
2. Sin fecha → línea completa, sin cookie.
3. Fecha futura → primera visita.
4. Orden por `event_date`.
5. Artículo en 2 temas aparece en ambos.

## 12. NFR
La página del tema debe renderizar en SSR: el delta no puede exigir JS para verse.
