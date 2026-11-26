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

## Dependencias

```
F1 ──> F2 ──┐
            ├──> F7
F5 ─────────┘
F3 ──> F4 ──> F6
F1 ──────────┘
```

## Estado del kanban

Las siete tareas están preparadas en `scripts/kanban-fases.sh`, que las crea
colgando del tablero «Noticias Planeta» (`t_dfca1c8f`) del kanban de Hermes.

No se han podido crear todavía: el tablero `default` tiene tomado el candado de
escritura porque un worker quedó colgado en la tarea `t_da665e98` (20 minutos sin
escribir en su log). El CLI espera el candado indefinidamente y el gateway
bloquea la escritura directa sobre `kanban.db`, así que la vía es liberar el
candado y ejecutar el script.

## Dudas abiertas

1. **El brief aprobado dice «cero opinión» y las secciones son Clima, Energía,
   Naturaleza y Ciencia-politica.** Este plan introduce Tecnología y Política y un
   enfoque editorial declarado. Hay que decidir si el brief se reescribe o si el
   enfoque es solo criterio de selección y estilo, no opinión.
2. **El bash publica directamente o deja en borrador** para revisión humana.
3. **El enfoque es texto libre o un catálogo** (izquierda / derecha / centro /
   neutral) con matices.
4. **Las secciones actuales** (España, Economía, Mundo, Deportes, General) se
   mantienen junto a las nuevas o se sustituyen.
