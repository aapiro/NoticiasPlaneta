# Plan de fases — Noticias Planeta

Decisiones de producto de esta versión: el portal deja de ser solo un agregador
y pasa a tener secciones configurables con enfoque editorial declarado, y una
vía de publicación diaria que primero es manual y después automática.

Orden acordado: **primero publicar sin IA, después automatizar**. Un portal con
1889 noticias y 11 publicadas no necesita un modelo, necesita que alguien pulse
publicar.

## Fases

### F1 · Script de edición diaria manual (bash)

Un comando que llene el portal de una vez, lanzado a mano, sin IA.

- `scripts/edicion-diaria.sh`: ingesta de las fuentes activas y recuento por fuente.
- Selección determinista: piezas con foto, últimas 48 h, máx. 2 por fuente y N por
  sección (N configurable, 20 por defecto).
- Publica y escribe `ediciones/YYYY-MM-DD.md`.
- Idempotente y con log.

**Aceptación:** una ejecución deja ≥ 10 noticias con foto en portada y su fichero de
edición; la segunda no duplica nada.

### F2 · Botón «Ejecutar edición» en el panel

Lanzar F1 desde el panel, con sesión y CSRF, sin `Runtime.exec` sobre un script.

**Aceptación:** con sesión de CHIEF publica y deja rastro en `audit_log`; sin sesión
302 y sin CSRF 403.

### F3 · Secciones configurables (Tecnología y Política primero)

`section_config` en base de datos: slug, nombre, descripción, activa, orden, cupo,
enfoque y `auto_publica`. La navegación se genera desde la tabla.

**Aceptación:** añadir una sección la hace aparecer sin recompilar ni reiniciar.

### F4 · Enfoque editorial por sección

El editor define por sección el ángulo de búsqueda y redacción. Se inyecta en el
prompt y se guarda con cada artículo qué enfoque se aplicó.

**Regla de transparencia:** la sección declara su enfoque en el portal. Sin esa
etiqueta visible, la función no se implementa.

**Aceptación:** cambiar el enfoque cambia el texto de la siguiente pieza y queda
registrado con qué enfoque se escribió.

### F5 · Publicación por lotes

Selección múltiple en la bandeja y publicar en una acción, por la máquina de
estados existente.

**Aceptación:** 20 borradores → 20 publicados, 20 entradas en `audit_log`.

### F6 · Sección automatizada con IA

`PUBLISH_AUTO` solo en las secciones marcadas `auto_publica`, con reglas duras:
fuente de confianza, sección permitida, límite diario, sin repetir pieza, ventana
de 48 h y sin salirse de lo que dice la fuente (ADR-009). Interruptor de
emergencia por sección y global.

**Aceptación:** publica con el enfoque de la sección y su marca de automática; al
apagar el interruptor no publica más.

### F7 · Panel: estado editorial y control

`/admin/edicion`: última ejecución, estado por sección, histórico y acciones.

**Aceptación:** tras dos ejecuciones muestra ambas con sus cifras.

### F8 · Generador de noticias a partir de un tema o titular

El editor escribe un tema o un titular y el sistema **arma una noticia** con lo que
encuentre en los feeds y en fuentes abiertas.

Flujo:

1. **Búsqueda.** Términos del tema/titular contra lo ya ingestado y, opcionalmente,
   contra feeds adicionales y búsqueda semántica (`SEARCH_SEMANTIC`).
2. **Recopilación.** Fragmentos con su medio, URL y fecha. Sin fragmentos
   suficientes no hay pieza.
3. **Redacción.** El modelo solo puede afirmar lo que esté en los fragmentos
   recopilados. Hereda el enfoque de la sección elegida (ADR-012).
4. **Verificación.** Cada párrafo queda ligado a las fuentes que lo sostienen. Un
   dato que no aparece en ninguna fuente se elimina, no se suaviza.
5. **Salida.** Borrador en `DRAFT`, marcado como generado con IA, con la lista de
   fuentes usadas y el prompt aplicado.

Reglas duras:

- **Nunca publica sola por defecto.** Sale en `DRAFT` para revisión humana. Solo
  puede publicar sin revisión si `PUBLISH_AUTO` está activo en esa sección.
- **Sin material, no hay pieza.** Menos de 2 fuentes independientes: se aborta y se
  dice por qué.
- **Marca visible.** La pieza indica que se generó con IA y cuántas fuentes usó.
- **Presupuesto.** Pasa por `BudgetPort`: una generación, un coste acotado.
- **Trazabilidad.** Se guardan fuentes, fragmentos, enfoque y versión de prompt.

**Aceptación:** dado un tema con cobertura real, produce un borrador cuyos datos se
pueden rastrear a las fuentes listadas; dado un tema sin cobertura, no inventa y
explica que no hay material. Depende de: F1, F3, F4.

## Dependencias

```
F1 ──> F2 ──┐
            ├──> F7
F5 ─────────┘
F3 ──> F4 ──> F6
F1 ──────────┘
F1 ──┐
F3 ──┼──> F8
F4 ──┘
```

## Estado del kanban

Las siete tareas están preparadas en `scripts/kanban-fases.sh`, que las crea
colgando del tablero «Noticias Planeta» (`t_dfca1c8f`) del kanban de Hermes.

No se han podido crear todavía: el tablero `default` tiene tomado el candado de
escritura porque un worker quedó colgado en la tarea `t_da665e98` (20 minutos sin
escribir en su log). El CLI espera el candado indefinidamente y el gateway
bloquea la escritura directa sobre `kanban.db`, así que la vía es liberar el
candado y ejecutar el script.

## Decisiones cerradas (26/11/2026)

1. **Línea editorial:** el portal declara enfoque por sección y los artículos lo
   reflejan, con etiqueta visible. El brief v1.0 se corrige con el ADR-012.
2. **El bash publica directamente**, con switch `--draft` y `EDICION_MODO=draft`
   para volver al modo revisión cuando se quiera.
3. **Catálogo cerrado de enfoque:** IZQUIERDA, DERECHA, CENTRO, NEUTRAL.
4. **Secciones:** se mantienen las actuales y se añaden Tecnología y Política, con
   checkbox de activa/desactivada por sección.
