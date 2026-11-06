# F10 — Búsqueda híbrida y artículos relacionados

**Módulo:** `search` · **Estado:** borrador · **Versión:** 1

## 1. Contexto
Buscador que entiende intención y "relacionados" que de verdad lo están, usando solo
PostgreSQL: `tsvector` (léxico) + `pgvector` (semántico), fusionados con RRF.

## 2. Actores
- Lector (anónimo)
- Redactor (buscador interno del backoffice)

## 3. Precondiciones
- El artículo está `published` y tiene `embedding` calculado.
- Existe el índice HNSW y el `tsvector` con `spanish` como configuración.

## 4. Flujo principal
1. Llega la consulta `q`.
2. Se ejecuta la búsqueda léxica: `websearch_to_tsquery('spanish', q)` ordenada por `ts_rank_cd`.
3. Se calcula el embedding de `q` y se ejecuta la búsqueda vectorial por coseno (top 50).
4. Se fusionan ambas listas con **RRF** (`k = 60`).
5. Se filtran: solo `published`, sin archivados, con `publishAt <= now`.
6. Se devuelven los 20 primeros con titular, sección, fecha, URL y extracto.

## 5. Alternativos y errores
| Código | Situación | Resultado |
|---|---|---|
| E01 | Consulta vacía o < 2 caracteres | `400` |
| E02 | El modelo de embeddings no responde | Se degrada a búsqueda léxica sola (nunca error) |
| E03 | Sin resultados | Se devuelven los 3 artículos más recientes de la sección detectada |

## 6. Reglas
- **RN1** Solo se busca en contenido publicado. Jamás en borradores.
- **RN2** La fusión es RRF, no una media ponderada inventada.
- **RN3** El fallo del embedding degrada, no rompe: la búsqueda léxica es el suelo.
- **RN4** Se registra qué consultas no devuelven resultados (alimenta la taxonomía).

## 7. Gherkin
```gherkin
@F10 @aceptacion
Escenario: Una consulta semántica encuentra la noticia aunque no comparta palabras
  Dado un artículo publicado sobre "subida del precio de la electricidad"
  Cuando se busca "por qué me cuesta más la factura"
  Entonces ese artículo aparece en los 5 primeros resultados

@F10 @aceptacion
Escenario: Un borrador nunca aparece en resultados
  Dado un borrador con título idéntico a la consulta
  Cuando se busca ese título
  Entonces el borrador no aparece

@F10 @aceptacion
Escenario: Si falla el embedding se usa la búsqueda léxica
  Dado un proveedor de embeddings que lanza timeout
  Cuando se ejecuta una búsqueda
  Entonces se devuelven resultados léxicos
  Y la respuesta no es un error
```

## 8. Datos
`article.search_vector tsvector` (GIN), `article.embedding vector(1024)` (HNSW, `vector_cosine_ops`)

## 9. Puertos
In: `SearchArticlesUseCase` · Out: `SearchIndexPort`, `EmbeddingPort`

## 10. Eventos
Ninguno de dominio. Métrica: `search.query`, `search.zero_results`.

## 11. Tests RED
1. Léxico encuentra coincidencia exacta.
2. Vectorial encuentra paráfrasis.
3. RRF ordena correctamente con listas de prueba fijas.
4. Borradores excluidos.
5. Fallback ante timeout.
6. p95 bajo carga con 100 000 artículos (Testcontainers).

## 12. NFR
p95 < 150 ms con 100k artículos. Coste de embedding por consulta cacheado 24 h por hash.
