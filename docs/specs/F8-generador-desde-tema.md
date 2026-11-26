# F8 · Generador de borradores desde un tema o titular

**Estado:** implementado · **Fecha:** 26/11/2026
**Depende de:** F1 (ingesta, ya hecha), F3 (secciones) y F4 (enfoque por sección, ADR-012)
**ADR:** `docs/adr/ADR-016-generador-desde-tema.md`

## Qué resuelve

El editor escribe un tema («sequía en Cataluña», «reforma fiscal») o un titular y
elige sección. El sistema busca material **ya ingerido**, redacta un borrador con lo
que ha encontrado, comprueba que cada párrafo está sostenido por sus fuentes y lo deja
en `DRAFT` marcado como generado con IA, con las fuentes usadas y la versión de prompt.

Lo que **no** hace: rellenar huecos con conocimiento del modelo, publicar por su cuenta
ni fabricar una pieza cuando no hay material.

## Flujo

```
tema + sección
   │
   ├─ 1. Validación      tema (4-300 car.) y sección existente y activa
   ├─ 2. Búsqueda        ingest_item (FTS español + respaldo ILIKE), ventana 30 días
   ├─ 3. Selección       máx. 6 fragmentos, máx. 2 por medio, recientes primero
   ├─ 4. Puerta          < 2 dominios raíz independientes ⇒ ABORTA (sin llamar al modelo)
   ├─ 5. Presupuesto     BudgetPort.reservar(20.000 µUSD)  ⇒ si no hay, ABORTA
   ├─ 6. Redacción       DeepSeek (JSON): titular + párrafos con [refs]
   ├─ 7. Verificación    cifras y citas textuales contra los fragmentos citados
   ├─ 8. Persistencia    DRAFT (origin=AGENT) + article_source + article_generation
   └─ 9. Publicación     solo si sección.autoPublica && capacidad PUBLISH_AUTO
```

## Reglas duras

| Regla | Cómo se cumple | Dónde se prueba |
|---|---|---|
| Solo hechos del material | Prompt con material delimitado + verificación determinista de cifras | `VerificadorTest` |
| Cada párrafo ligado a fuentes | El modelo devuelve `fuentes:[refs]`; sin refs válidas el párrafo se descarta | `VerificadorTest` |
| Un dato sin fuente se elimina | Cifra o cita textual ausente en los fragmentos citados ⇒ párrafo descartado, con motivo | `VerificadorTest` |
| Menos de 2 fuentes independientes ⇒ aborta | Cuenta dominios raíz distintos **antes** de gastar presupuesto | `GenerarBorradorServiceTest` |
| Pasa por `BudgetPort` | `reservar()` antes de la llamada; sin presupuesto no se llama al modelo | `GenerarBorradorServiceTest` |
| Nunca publica sola | `DRAFT` por defecto; requiere `section_config.auto_publica` **y** capacidad `PUBLISH_AUTO` | `GenerarBorradorServiceTest`, `F8GeneradorAcceptanceTest` |
| Trazabilidad | `article_generation`: consulta, fragmentos (medio/URL/fecha), párrafos con citas, descartes, prompt y coste | `F8GeneradorAcceptanceTest` |

## Verificación: qué se comprueba y qué no

Se comprueba, de forma determinista y sin modelo:

1. **Citas**: cada párrafo cita al menos una fuente de las que se le pasaron, y solo refs existentes.
2. **Cifras**: todo número del párrafo (incluidos años y porcentajes) aparece en el texto del
   fragmento citado, tras normalizar separadores (`1.800` ≡ `1800`, `2,29` ≡ `2.29`).
3. **Citas textuales**: lo que va entre comillas aparece literalmente en el fragmento citado.
4. **Titular**: si contiene una cifra o una cita que no está en los párrafos aceptados, se aborta.

No se comprueba (límite conocido, no promesa): afirmaciones cualitativas sin cifra
(«la situación es grave») ni la omisión selectiva de datos. Eso queda en manos de la
revisión humana obligatoria, que es la razón de que la salida sea `DRAFT`.

## Salida

- Artículo en `DRAFT`, `origin=AGENT`, `editorial_bias` de la sección (cuando F4 la persiste).
- `article_source` con una fila por fuente (URL, dominio raíz, contraste recalculado).
- `article_generation` con el detalle completo.
- Cuerpo con marca visible: `Texto generado con IA a partir de N fuentes. Pendiente de revisión editorial.`
- Si se aborta: `ResultadoGeneracion.estado=ABORTADO` con `motivo` y `explicacion` en español,
  sin artículo creado.

## Motivos de aborto

`menos_de_2_fuentes_independientes`, `sin_material`, `presupuesto_agotado`,
`ia_no_configurada`, `ia_error`, `titular_con_dato_sin_fuente`, `sin_material_verificable`,
`seccion_inactiva`, `seccion_desconocida`.

## Entradas

- MCP: `generar_borrador_desde_tema(tema, seccion)` — un agente puede crear el borrador, nunca publicarlo.
- Panel: `GET/POST /admin/generador`.

## Criterios de aceptación

1. Un tema con cobertura real en ≥ 2 medios produce un borrador rastreable: cada párrafo
   tiene citas y cada cifra se puede localizar en la fuente citada.
2. Un tema sin cobertura (0-1 dominio) no crea artículo, no llama al modelo y explica por qué.
3. Un modelo que inventa una cifra no consigue colarla: el párrafo se descarta.
4. Con `auto_publica=false` (por defecto) la pieza queda en `DRAFT`; solo se publica con
   `auto_publica=true` y `PUBLISH_AUTO` encendida.
