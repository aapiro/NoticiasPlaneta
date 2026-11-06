# ADR-009 — Qué datos salen hacia el proveedor de LLM

**Estado:** aceptada · **Fecha:** 2026-11-06 · **Decisores:** Tony, Dev (pendiente revisión legal)

## Contexto
DeepSeek es el único componente no on-premise. Enviar contenido editorial a un proveedor
en China tiene implicaciones de RGPD y, sobre todo, de secreto profesional periodístico.

## Decisión
1. **Nunca salen borradores.** Solo se envía contenido **ya publicado** o texto de fuentes públicas.
2. **Nunca salen datos de fuentes no publicadas, identidades de testigos ni material embargado.**
3. El payload se **minimiza**: título + cuerpo del artículo publicado; sin autor, sin notas internas.
4. Se registra por artículo si su enriquecido usó proveedor externo o modelo local (`ai_enrichment.provider`).
5. Antes de producción: contrato de encargo de tratamiento y cláusulas contractuales tipo (SCC)
   con evaluación de impacto de la transferencia. **Confirmar con asesoría legal.**
6. Si el contenido es sensible, se enruta a modelo local y se degrada el servicio con aviso.

## Alternativas
| Alternativa | Veredicto |
|---|---|
| Enviar borradores y exclusivas | Riesgo inaceptable: filtración de scoop y de fuentes |
| Autoalojar los pesos de DeepSeek | Los pesos son abiertos, pero el hardware necesario no es viable en un VPS |
| Solo modelo local | Calidad actual insuficiente para titular y resumen en español; se reevalúa |

## Consecuencias
- Positivas: el enriquecido es útil sin exponer el trabajo en curso.
- Negativas: los borradores no se benefician del enriquecido hasta publicarse; hay que
  reprocesar tras publicar (coste bajo y controlado).
