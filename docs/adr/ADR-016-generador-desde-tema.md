# ADR-016 · Generador de borradores desde un tema

**Estado:** aceptado · **Fecha:** 26/11/2026
**Relacionado:** ADR-006 (módulo IA), ADR-009 (qué sale al proveedor), ADR-011 (capacidades),
ADR-012 (línea editorial por sección), ADR-015 (publicación automática, F6), F8 de `PLAN-FASES.md`

## Contexto

El editor quiere escribir un tema y recibir una pieza montada con lo que hay publicado
en los feeds. El riesgo es evidente: un modelo generativo al que se le pide «una noticia
sobre X» rellena los huecos con lo que sabe, y lo que sabe puede ser falso, viejo o de
otro país. Una sola cifra inventada destruye la credibilidad del medio.

La tentación técnica es pedir «no inventes» en el prompt y confiar. Eso no es un control,
es una súplica: no hay forma de demostrar después qué párrafo se apoyaba en qué fuente.

## Decisión

1. **El generador no publica: redacta un borrador.** Sale en `DRAFT`, con revisión humana
   obligatoria. La única excepción es `PUBLISH_AUTO` (capacidad de ADR-011) **y**
   `section_config.auto_publica` en esa sección; las dos puertas cerradas por defecto.

2. **El material se recopila antes de llamar al modelo y no se amplía después.** Se buscan
   fragmentos en el corpus ya ingerido (`ingest_item`, FTS español) con medio, URL y fecha.
   El modelo ve exactamente esos fragmentos, numerados.

3. **Cada párrafo declara sus fuentes.** El contrato de salida del modelo es
   `{"titular": "...", "parrafos": [{"texto": "...", "fuentes": [1, 4]}]}`. Un párrafo sin
   refs válidas se descarta; no se «arregla» ni se le asignan fuentes a posteriori.

4. **Verificación determinista, no otra llamada al modelo.** Antes de guardar:

   - toda **cifra** del párrafo tiene que aparecer en el texto de las fuentes que cita
     (normalizando separadores de millar y decimal);
   - toda **cita entre comillas** tiene que aparecer literalmente en esas fuentes;
   - si el titular lleva una cifra o cita que no está en los párrafos aceptados, se aborta.

   Es la regla del encargo —«un dato sin fuente se elimina»— aplicada con código que se
   puede leer y probar, no con una instrucción en el prompt.

5. **Sin material, no hay pieza.** Menos de dos dominios raíz independientes: se aborta
   **antes** de gastar presupuesto y se explica. Contar dominios, no enlaces: dos URLs de
   elpais.com son una fuente.

6. **Enfoque heredado, con los hechos intactos.** La sección aporta su `enfoque` (ADR-012)
   y una instrucción corta derivada de él; el prompt la aplica al ángulo y al titular, y
   prohíbe explícitamente omitir datos que contradigan ese ángulo. El enfoque se guarda en
   `article_generation.enfoque` junto con la versión de prompt.

7. **Trazabilidad en tabla propia.** `article_generation` guarda consulta, sección, enfoque,
   versión de prompt, modelo, proveedor, coste, fragmentos (medio/URL/fecha/texto), párrafos
   con sus citas y los descartes con su motivo. Sin esa fila, la pieza no es rastreable.

8. **Presupuesto por delante.** `BudgetPort.reservar(20.000 µUSD)` antes de la llamada; si
   no concede, se aborta. El coste real medido se guarda en `article_generation.coste_micros`.

## Relación con F6 (ADR-015) y por qué hay un segundo camino de publicación

F6 publica sola piezas **ajenas ya ingeridas**, y su dominio lo exige
(`Article.publishAutomatically` solo acepta `origin=INGEST` con `ingest_item_id`). Una pieza
generada no cumple ninguna de las dos condiciones, así que ese camino no puede usarse.

Cuando el editor pulsa «generar» con la sección en `auto_publica` y la capacidad
`PUBLISH_AUTO` encendida, la pieza se publica por la cadena completa de estados con actor
`SYSTEM` (`PublicarSinRevisionUseCase`), que deja auditoría de cada transición más una
entrada `ARTICLE_PUBLISHED_WITHOUT_REVIEW`. Son **tres puertas**: capacidad global, sección
en automático y permiso explícito del llamante (el panel lo pone a `true`; el MCP nunca).
Con cualquiera de las tres cerrada, la pieza se queda en `DRAFT`.

Si el criterio de producto cambia y la IA no debe publicar nunca texto generado, no hay que
tocar código: basta con no encender `PUBLISH_AUTO` (y la sección deja de publicar sola).

## Consecuencias

- El generador **puede** producir un borrador peor que un humano. Es aceptable: el objetivo
  es quitar el trabajo de recopilación, no sustituir la firma.
- El borrador y su traza se escriben en dos pasos (artículo y `article_generation`). Si el
  segundo falla, queda un `DRAFT` **sin traza**, nunca publicado, y el editor lo ve en el panel;
  se prefiere eso a una transacción larga que bloquee la creación del artículo.
- Se pierden párrafos que un humano habría salvado (una cifra derivada, una comparación).
  Es el precio de que ninguna cifra inventada llegue al lector. Documentado como límite.
- `PUBLISH_AUTO` sigue siendo la capacidad peligrosa. Aquí solo se añade una puerta más;
  el interruptor por sección ya existía (F3) y sigue apagado por defecto.
- El texto generado viaja a DeepSeek: son **fragmentos de fuentes públicas**, no borradores
  propios, así que ADR-009 se cumple. La pieza resultante (producción propia) no se envía a
  ningún proveedor.

## Alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| Confiar solo en el prompt («no inventes») | No es verificable; no hay evidencia posterior de qué sostenía cada dato |
| Segunda llamada al modelo para «verificar» | Mismo problema de fondo: otro modelo opinando no es una prueba; y duplica coste |
| Publicar automáticamente si hay ≥ 2 fuentes | Cantidad de fuentes no es calidad de la redacción; la revisión humana es barata aquí |
| Buscar en la web abierta sin restricción | Sin índice propio no hay fecha ni medio fiables, y se dispara el coste; el corpus ingerido ya es la web abierta suscrita |
| Generar la pieza dentro del módulo `ai` | El generador necesita secciones, artículos y fuentes: es un caso de uso editorial, no un adaptador de IA |
