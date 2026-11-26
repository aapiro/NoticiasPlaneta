# ADR-015 · Publicación automática por sección (F6)

- **Estado:** aceptado e implementado (26/11/2026)
- **Contexto:** F1 (edición manual), F3 (secciones configurables), F4 (enfoque en el
  prompt) y ADR-009 (datos y proveedor LLM), ADR-011 (capacidades) y ADR-012 (línea
  editorial por sección).

## Problema

Publicar sin que nadie mire es la función que convierte el portal en un producto y, a la
vez, la que puede arruinarlo: un modelo puede publicar una pieza vieja, repetida, de una
fuente que no toca, o directamente inventarse el texto. Además, la clasificación actual
del script (`ilike` sobre el titular) manda casi todo a `general` y deja Tecnología vacía,
así que "publicar automáticamente" hoy publicaría basura sin sección.

## Decisión

### 1. Dos interruptores, ambos en base de datos, ambos sin reiniciar

| Interruptor | Dónde | Alcance |
|---|---|---|
| Global | `feature_flag` → capacidad `PUBLISH_AUTO` (ADR-011) | apaga clasificación y publicación |
| Por sección | `section_config.auto_publica` + `auto_limite_diario` | esa sección deja de publicar |

Se leen en cada pasada. No hay caché de configuración que invalide ni variable de
entorno que exija reiniciar. El panel (`/admin/autopublicacion`) escribe los dos; el de
sección también está en `/admin/secciones` como hasta ahora.

**Con el interruptor global apagado no se clasifica nada**: no se gasta presupuesto ni
se llama al proveedor. Es una decisión deliberada de coste y de datos (ADR-009).

### 2. Reglas duras, en SQL y en el dominio

| Regla | Dónde se aplica |
|---|---|
| Ventana de 48 h sobre `coalesce(published_at, created_at)` | consulta de candidatos + `AutoPublishRules.withinWindow` |
| Fuente de confianza (`source.confianza`) y no `DISABLED` | consulta + `AutoPublishRules.trustedSource` |
| Sección activa y en automático | consulta de publicación |
| Cupo diario por sección (`auto_limite_diario`, día natural de Madrid) | `AutoPublishRunService` + `AutoPublishRules.quotaAvailable` |
| Sin repetir titular ya publicado (7 días, titular normalizado) | consulta de publicación |
| **Solo piezas clasificadas por el modelo** (fila en `article_auto_classification`) | consulta de publicación |
| Solo piezas de origen `INGEST` con `ingest_item_id` y cuerpo ≥ 80 caracteres | consulta + `Article.publishAutomatically` (ADR-009) |
| Sin reescribir titular ni cuerpo | el camino automático no edita el agregado |
| Máximo de piezas por pasada (`autopublish.batch-size`, 10) | `AutoPublishRunService` |

`source.confianza` nace **false**: hasta que un editor marca una fuente, la
automatización no publica nada de ella. El fallo por defecto es no publicar.

### 3. La pasada son dos fases separadas

1. **Clasificación.** El modelo ve el **titular** (texto público de la fuente) y el
   catálogo cerrado de secciones activas; devuelve `{seccion, confianza}`. Se guarda en
   `article_auto_classification` (modelo, versión de prompt, confianza) y se mueve el
   borrador a esa sección. Una sola vez por pieza: no se vuelve a pagar en cada pasada.
   Si la respuesta está fuera del catálogo o con confianza < 0,55, cae a `general` y no
   se publica sola.
2. **Publicación.** Sin modelo: solo reglas. Se publica lo clasificado que cae en una
   sección en automático y cabe en el cupo.

Separarlas permite que un fallo del proveedor no bloquee lo ya clasificado, y que apagar
el interruptor no deje trabajo a medias.

### 4. Marca de automática, en el artículo y en `audit_log`

- `article_auto_publicacion` (una fila por pieza): sección, **enfoque** con el que salió,
  modelo, versión de prompt y lista de reglas aplicadas. Es la marca que se pinta en el
  portal (`Publicación automática`) y la que se puede auditar.
- `article.editorial_bias` se rellena con el enfoque de la sección (ADR-012) si el editor
  no lo había fijado antes. Lo fijado por una persona no se pisa.
- `audit_log`: `ARTICLE_AUTO_CLASSIFIED` (sección anterior → nueva) y
  `ARTICLE_AUTO_PUBLISHED` (DRAFT → `PUBLISHED|automatica|seccion=…|enfoque=…|modelo=…|reglas=…`).
- Estado, marca, auditoría y outbox se escriben en la **misma transacción**: no existe
  una pieza publicada sin su marca.

### 5. Sin proveedor de IA no se publica

La publicación automática exige que la pieza tenga una **clasificación del modelo**
registrada en `article_auto_classification`. Si el proveedor no está configurado,
`classify` devuelve `AI_NOT_CONFIGURED` y la pieza se queda en `DRAFT`, aunque el
`ilike` del script la hubiera dejado ya en Tecnología. No hay fallback a `ilike` ni a "la
sección que ya tenía", porque eso es exactamente el defecto que esta fase corrige.

Comprobado en el test de aceptación (`sinProveedorNoPublica`): con el modelo devolviendo
`AI_NOT_CONFIGURED`, una pieza que el `ilike` dejó en Tecnología **no** se publica. En la
primera pasada en vivo sin clave apareció justo el fallo contrario —una pieza colocada por
el `ilike` se publicó con `modelo=sin-clasificar`— y de ahí nace esta regla.

## Consecuencias

- Activar la automatización exige tres actos explícitos: encender `PUBLISH_AUTO`, marcar
  `auto_publica` en la sección y marcar fuentes de confianza. Ninguno está encendido de
  fábrica.
- El tope real por día es `auto_limite_diario` por sección (3 por defecto). Subirlo tiene
  coste editorial, no técnico.
- El cupo se cuenta con `article_auto_publicacion`, no con `article.published_at`: una
  publicación manual no consume cupo automático.

## Pendiente / límites conocidos

- **Defecto encontrado en vivo y corregido aquí:** 141 artículos tenían slugs inválidos
  (`...-inmobiliario-de--a070`) porque `scripts/edicion-diaria.sh` truncaba el titular sin
  recortar el guion final. Al rehidratar el agregado, esos artículos lanzaban
  `SLUG_INVALID` y **cualquier** lectura fallaba con 500 — no solo la automática. Se
  arregla en tres sitios: `Slug.persisted` (leer la base no valida formato, solo que no
  esté vacío), V23 (repara los borradores; lo publicado conserva su URL por ADR-001) y el
  script (deja de generarlos). 6 de los 141 ya estaban publicados y conservan su URL.

- **F4 (enfoque en el prompt)** va por otro camino: aquí el enfoque se *registra* y se
  usa como etiqueta; reescribir el texto con el ángulo de la sección es F4/F8. La
  automatización no reescribe nada.
- Sin `DEEPSEEK_API_KEY` no hay clasificación real: los tests de aceptación dobles
  (`@MockitoBean`) cubren el mecanismo y la llamada HTTP tiene su test con un servidor
  local. La calidad del prompt solo se puede medir con clave.
- `deepseek-chat` es el modelo que usa el clasificador. El adaptador de enriquecido usa
  `deepseek-flash`, que no está en el catálogo público de DeepSeek; no se toca aquí
  porque es de otra fase.
- La caché de capacidades (`DatabaseAiCapabilities`) se recarga cuando **el panel** escribe
  el modo, no cuando alguien cambia `feature_flag` por SQL a mano. Con una sola instancia
  es lo que se espera; en multi-instancia haría falta TTL o notificación (ADR-011 ya lo
  dejaba abierto). Los interruptores de sección y la marca de confianza de fuentes **sí**
  se leen de base en cada pasada.
- **Defecto preexistente que afecta al coste (no corregido aquí):** `InMemoryBudgetAdapter`
  llama "diario" a un presupuesto que **no se reinicia** al cambiar el día: el contador
  solo vuelve a cero al reiniciar la app. Con `AI_DAILY_BUDGET_MICROS=5.000.000` y la
  estimación de 20.000 micros por clasificación, la automatización deja de clasificar tras
  ~250 piezas hasta el siguiente reinicio. Está anotado como tarjeta aparte.

## Cómo se verifica

```bash
./mvnw -o test -Dtest='AutoPublish*,ClassifyArticle*,DeepSeekClassification*'
```

Aceptación (`AutoPublishAcceptanceTest`, Testcontainers + MockMvc + doble del modelo):
publica en Tecnología con enfoque, marca, auditoría y outbox; y **no** publica con el
interruptor global apagado, con la sección apagada, fuera de la ventana, con titular
repetido, con fuente sin confianza ni por encima del cupo diario.
