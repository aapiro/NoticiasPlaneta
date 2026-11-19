# ADR-011 — Capacidades de IA activables y entitlements

**Estado:** propuesta · **Fecha:** 2026-11-19
**Relacionado:** ADR-006 (módulo IA), ADR-009 (qué datos salen al LLM), ADR-008 (despliegue on-premise)

> **Restricción heredada del ADR-009, no negociable aquí:** nunca salen borradores de producción
> propia hacia el proveedor externo. Solo se envía **contenido publicado** o **texto de fuentes
> públicas**. Por eso el enriquecido se dispara al publicar y no al ingerir.
>
> Cómo encaja con el backfill: el texto que llega por RSS es **fuente pública**, así que un
> borrador procedente de una fuente externa sí puede enriquecerse antes de publicar. Lo que no
> puede salir nunca es material editorial propio sin publicar (notas, borradores firmados,
> material embargado). La capacidad debe comprobar la **procedencia**, no solo el estado.

## Contexto

El ADR-006 dejó el módulo `ai` con puertos y adaptadores intercambiables, cola y trazabilidad.
Falta decidir **cómo se enciende y se apaga cada cosa**: hoy la única palanca es poner o no la
API key, así que el producto es todo-o-nada. Se quiere poder tener valor **con y sin IA**, y
poder cobrar por la parte que la usa en el futuro.

## Decisión

1. **Se activan capacidades, no proveedores.** El usuario final no elige "DeepSeek" ni "Ollama":
   elige qué hace el producto. El proveedor es una decisión de infraestructura por capacidad.
2. **Cada capacidad tiene un modo**: `off` · `local` · `external` · `hybrid`.
3. **Los planes agrupan capacidades** (entitlements). El gate se evalúa por capacidad.
4. **El proveedor se inyecta por decorador** sobre el puerto existente: no se tocan los casos
   de uso ni los adaptadores actuales.
5. **La degradación es visible**: sin una capacidad, la interfaz no la ofrece; no hay botones
   que fallen.

## Catálogo de capacidades

| Capacidad | Qué da | Coste | Riesgo |
|---|---|---|---|
| `ai.search.semantic` | búsqueda por significado (embeddings) | 0 (local) | bajo |
| `ai.enrich.tags` | etiquetas en español (máx. 8) | bajo | bajo |
| `ai.enrich.entities` | personas, organizaciones, lugares | bajo | bajo |
| `ai.enrich.summary` | resumen de 2 frases | bajo | medio (se muestra al lector) |
| `ai.article.related` | noticias relacionadas | bajo | bajo |
| `ai.publish.auto` | publicar tras enriquecer, con reglas | bajo | **alto** |

Seis capacidades es un número sano. Añadir veinte convierte esto en un panel de configuración
que nadie entiende.

## Modos

```
off       no se ejecuta; la interfaz no lo ofrece
local     Ollama (coste 0, más lento, calidad media)
external  DeepSeek (céntimos, rápido, calidad alta)
hybrid    embeddings locales + generación externa   ← recomendado
```

Los cuatro escenarios que se plantearon se expresan así, sin código nuevo:

| Escenario | `search.semantic` | `enrich.*` | `publish.auto` |
|---|---|---|---|
| Sin IA | off | off | off |
| Solo búsqueda | hybrid | off | off |
| Híbrido | hybrid | hybrid | off |
| Automático | hybrid | hybrid | on |

## Planes (entitlements)

| Plan | Incluye |
|---|---|
| `SIN_IA` | publicación manual, búsqueda por texto |
| `BASICO` | + `ai.search.semantic` |
| `PRO` | + `ai.enrich.*` y `ai.article.related` |
| `AUTO` | + `ai.publish.auto` (con límite diario) |

Hoy hay **un solo inquilino** (noticiasplaneta). El plan vive en configuración; el día que haya
varios, se sustituye la implementación de `EntitlementsPort` sin tocar los casos de uso.

## Implementación

### Puerta única

```java
public enum AiCapability { SEARCH_SEMANTIC, ENRICH_TAGS, ENRICH_ENTITIES, ENRICH_SUMMARY, ARTICLE_RELATED, PUBLISH_AUTO }
public enum AiMode { OFF, LOCAL, EXTERNAL, HYBRID }

public interface AiCapabilities {
    AiMode modeOf(AiCapability capability);
    boolean isOn(AiCapability capability);
}
```

### Decorador sobre el puerto existente

Encaja con el `@ConditionalOnMissingBean(EnrichmentPort.class)` que ya hay:

```java
class GatedEnrichmentPort implements EnrichmentPort {
    private final AiCapabilities capabilities;
    private final Map<AiMode, EnrichmentPort> adapters; // off, local, external

    public EnrichmentResult enrich(String title, String body) {
        var mode = capabilities.modeOf(AiCapability.ENRICH_TAGS);
        if (mode == AiMode.OFF) return EnrichmentResult.empty();
        return adapters.get(mode).enrich(title, body);
    }
}
```

Ningún caso de uso cambia. `UnconfiguredEnrichmentAdapter` pasa a ser el modo `off`.

### Fuente de verdad

1. `application.yml` → valores por defecto del despliegue.
2. Tabla `feature_flag` (`capability`, `mode`, `updated_by`, `updated_at`) → **manda sobre el yml**.
3. Caché en memoria con recarga al escribir (no en cada request).

Sin LaunchDarkly ni servicios externos: 100 líneas y control total.

### Medición (para poder cobrar)

Extender `article_enrichment` / `ai_job` con `provider`, `capability`, `tokens_in`, `tokens_out`,
`cost_usd`. El ADR-006 ya pedía `model`, `prompt_version` y `cost`; esto lo hace **por capacidad**,
que es la unidad que se factura.

## Visibilidad en el producto

**Panel editorial → página "Capacidades"**: cada capacidad con su modo, su estado (activa/inactiva),
el proveedor efectivo y el coste acumulado del periodo.

**Portal público degradado:**

- Sin `ai.search.semantic`: el buscador funciona por texto, sin etiqueta.
- Con ella: aparece "búsqueda semántica" y el orden por similitud.
- Sin `ai.enrich.summary`: el artículo no muestra resumen automático.
- Con él: resumen visible **con insignia de contenido generado por IA** (obligación de
  transparencia y coherencia con el ADR-006).

## Fases y criterios de aceptación

| Fase | Qué | Criterio |
|---|---|---|
| F0 | enum + config + decorador | con `off` **no se hace ninguna llamada** al proveedor (test con espía) |
| F1 | tabla `feature_flag` + página de capacidades | cambiar el modo desde el panel surte efecto sin reiniciar |
| F2 | degradación en el portal | sin embeddings, `/buscar` responde igual; sin resumen, el artículo no cambia |
| F3 | medición de tokens y coste | el panel muestra coste por capacidad del periodo |
| F4 | entitlements por plan | solo cuando exista un segundo inquilino |

## Regla de procedencia (obligatoria antes de enriquecer un borrador)

```
artículo PUBLICADO                    → enriquecible
artículo DRAFT con origen externo     → enriquecible (texto de fuente pública, ADR-009)
artículo DRAFT de producción propia   → NUNCA al proveedor externo
```

Con modo `local` la restricción desaparece (no sale nada del servidor), lo que da una razón
más para ofrecer el modo local a quien tenga material sensible.

## Riesgos

- **`ai.publish.auto` es la capacidad peligrosa.** Requiere reglas explícitas (fuente de confianza,
  sección permitida, límite diario) y registro en `audit_log`. No se activa por defecto nunca.
- **Doble fuente de verdad** (yml y tabla): la tabla manda; el yml solo es el valor inicial.
- **Sobre-ingeniería**: si al final solo se usan dos capacidades, sobra el resto del catálogo.
  Se pueden dejar declaradas y apagadas sin coste.
- **La IA no publica texto**: solo metadatos. Se mantiene lo decidido en ADR-006.

## Alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| Un flag booleano por proveedor (`deepseek.enabled`) | Acopla el producto al proveedor y no permite degradar la interfaz |
| Variables de entorno y redeploy para cambiar | No se puede probar ni vender por feature |
| Servicio de feature flags externo | Dependencia y coste para un monolito con un inquilino |
| Multi-tenant desde el principio | Complejidad sin clientes; basta con la interfaz `EntitlementsPort` |
