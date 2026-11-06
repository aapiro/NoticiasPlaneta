# ADR-006 — IA como módulo con puerto, fuera de la ruta crítica

**Estado:** aceptada · **Fecha:** 2026-11-06

## Decisión
1. Módulo `ai` con **puerto** (`EnrichmentPort`, `EmbeddingPort`) y adaptador intercambiable
   (Spring AI 2.0.2, API propia o modelo local).
2. **Nunca en el request**: todo por cola (`ai_job`) alimentada desde el outbox.
3. **Presupuesto** por periodo con corte automático.
4. **Trazabilidad**: `model`, `prompt_version`, `cost` en cada resultado.
5. **La IA no publica y no reescribe el texto editorial**: solo añade metadatos.
6. Todo contenido generado se **etiqueta** como tal y es editable por un humano.

## Proveedor (decidido 2026-11-06)

- **Generación: DeepSeek API** (`https://api.deepseek.com`, compatible OpenAI) vía
  `spring-ai-starter-model-deepseek` 2.0.2. Modelos: `deepseek-flash` (por defecto) y
  `deepseek-v4-pro` (solo para tareas de calidad). **No fijar** `deepseek-chat` ni
  `deepseek-reasoner`: fueron retirados el 2026-07-24 y sobreviven como alias no documentados.
- **Embeddings: locales** con `bge-m3` servido por Ollama (1024 dimensiones, multilingüe).
  **DeepSeek no ofrece endpoint de embeddings** — verificado en su documentación y en su
  página de precios: solo hay modelos de chat. El puerto `EmbeddingPort` es independiente
  del puerto de generación precisamente por esto.

## Alternativas
| Alternativa | Por qué no |
|---|---|
| OpenAI `text-embedding-3` | Rompe la premisa on-premise y añade un segundo proveedor externo |
| Pedir embeddings a DeepSeek | No existe el endpoint; no es una opción |
| Llamadas directas al SDK en los servicios | Acopla el dominio al proveedor y no se puede auditar |
| Enriquecer en la ruta del request | Un timeout del modelo degrada la publicación |
| Artículos generados y publicados en masa | Riesgo legal y reputacional; el producto es la credibilidad |

## Consecuencias
- Positivas: cambiar de modelo es cambiar un adaptador; coste acotado y medido.
- Negativas: el enriquecido es eventual (segundos o minutos), no instantáneo.
