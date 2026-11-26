#!/usr/bin/env bash
# Crea en el kanban de Hermes las fases de Noticias Planeta, colgando del
# tablero "Noticias Planeta" (t_dfca1c8f).
#
# Se ejecuta cuando el board 'default' esté libre. Si el gateway tiene tomado
# el candado de escritura, el CLI se queda esperando; no es un fallo del
# script.
#
#   ./scripts/kanban-fases.sh            crea todas
#   ./scripts/kanban-fases.sh --dry-run  solo muestra lo que haría
set -uo pipefail

BOARD="t_dfca1c8f"
K="hermes kanban --board default"
DRY=""
[ "${1:-}" = "--dry-run" ] && DRY="echo [dry]"

crear() {
    local titulo="$1" ; shift
    if [ -n "$DRY" ]; then
        echo "[dry] $titulo"
        return 0
    fi
    local id
    id=$($K create "$titulo" --parent "$BOARD" --assignee dev-v1 --created-by dev-v1 \
             --priority 1 --body-file - --json 2>&1 | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"\(t_[a-f0-9]*\)"/\1/')
    echo "${id:-FALLO}  $titulo"
    echo "${id:-}" >> /tmp/kanban-fases-ids.txt
}

crear "F1 · Script de edición diaria manual (bash)" <<'TXT'
Objetivo: un comando que llene el portal de noticias de una vez, lanzado a mano, sin IA.

Trabajo:
- scripts/edicion-diaria.sh: recorre las fuentes activas, fuerza la ingesta por la API
  (X-Api-Key) y deja el recuento por fuente.
- Selección determinista, sin modelo: piezas con foto, de las últimas 48 h, máximo 2 por
  fuente y máximo N por sección (N configurable, por defecto 20).
- Publica lo seleccionado y escribe ediciones/YYYY-MM-DD.md con titular y enlace.
- Idempotente: dos ejecuciones seguidas no duplican.
- Log en logs/edicion-YYYY-MM-DD.log; salida != 0 si una fuente falla.

Criterio de aceptación: una ejecución deja la portada con >= 10 noticias con foto y su
fichero de edición; la segunda no cambia nada. Depende de: nada.
TXT

crear "F2 · Botón «Ejecutar edición» en el panel administrativo" <<'TXT'
Objetivo: lanzar la edición desde el panel, sin terminal.

Trabajo:
- POST /admin/edicion/ejecutar bajo /admin/** (sesión + CSRF) con confirmación.
- No se usa Runtime.exec sobre un script del repo: se invocan los casos de uso de ingesta
  y publicación existentes y se guarda el informe.
- Resultado en la misma página: fuentes consultadas, creadas, publicadas, errores.
- audit_log con quién lo lanzó. Candado para que no se solapen dos ejecuciones.

Criterio de aceptación: con sesión de CHIEF el botón publica y deja rastro; sin sesión 302
y sin CSRF 403. Depende de: F1.
TXT

crear "F3 · Secciones informativas configurables (Tecnología y Política primero)" <<'TXT'
Objetivo: que las secciones no sean texto libre en article.section_id, sino una tabla.

Trabajo:
- Migración V19: section_config (slug PK, nombre, descripcion, activa, orden,
  max_piezas_por_edicion, enfoque, auto_publica, created_at, updated_at).
- Semilla: tecnologia y politica, más las existentes (espana, economia, mundo, deportes,
  general) para no romper lo publicado.
- Adaptador cacheado, mismo patrón que DatabaseAiCapabilities (ADR-011).
- Navegación del portal y desplegable del panel generados desde la tabla.
- Validación: el section_id de un artículo debe existir en section_config.

Criterio de aceptación: añadir una sección en la tabla la hace aparecer en la navegación
sin recompilar ni reiniciar. Depende de: nada.
TXT

crear "F4 · Enfoque editorial por sección, configurable por el editor humano" <<'TXT'
Objetivo: que el editor defina, por sección, el ángulo con el que se busca y redacta.

Trabajo:
- Campo enfoque (texto libre con plantilla y ejemplos) en section_config, editable desde
  /admin/secciones.
- Se inyecta en el prompt de enriquecido y redacción. Se guarda con cada artículo qué
  enfoque y qué versión de prompt se usaron, para poder auditar la decisión.
- Etiqueta visible en el portal: cada sección declara su enfoque editorial. Sin esa
  transparencia no se implementa.
- Validación, longitud máxima y audit_log de cada cambio.

Criterio de aceptación: cambiar el enfoque de tecnologia cambia el texto de la siguiente
pieza de esa sección y el artículo guarda con qué enfoque se escribió. Depende de: F3.
TXT

crear "F5 · Publicación por lotes en el panel administrativo" <<'TXT'
Objetivo: publicar 20 noticias en un clic, no en veinte.

Trabajo:
- Casilla por artículo en la bandeja, «seleccionar todo de la página» y
  POST /admin/articulos/publicar-lote (sesión + CSRF).
- Solo transiciones válidas por la máquina de estados existente.
- Resumen del resultado: publicados, omitidos y por qué.
- audit_log por lote con los ids afectados.

Criterio de aceptación: seleccionar 20 borradores y publicarlos deja los 20 en PUBLISHED,
la portada los muestra y audit_log tiene 20 entradas con el mismo actor. Depende de: nada.
TXT

crear "F6 · Sección automatizada con IA (buscar, seleccionar y redactar según el enfoque)" <<'TXT'
Objetivo: que una sección se llene sola respetando el enfoque que definió el editor.

Trabajo:
- PUBLISH_AUTO solo en las secciones marcadas auto_publica, no global.
- Reglas duras antes de publicar: fuente de confianza, sección permitida, límite diario,
  no repetir pieza, ventana de 48 h, y que el texto no salga de lo que dice la fuente
  (ADR-009: nunca se envía un borrador propio al proveedor externo).
- El prompt compone: reglas del brief + enfoque de la sección + texto de la fuente.
- Interruptor de emergencia por sección y global, efectivo en la siguiente ejecución sin
  reiniciar (reutiliza AiCapabilities).
- Toda pieza automática marcada como tal en el artículo y en audit_log.

Criterio de aceptación: con tecnologia en automático, la ejecución diaria publica piezas
con ese ángulo y su marca; al apagar el interruptor no se publica ninguna más.
Depende de: F1, F4.
TXT

crear "F7 · Panel: estado editorial y control de la edición" <<'TXT'
Objetivo: una página /admin/edicion que cuente qué ha pasado y permita corregirlo.

Trabajo:
- Última ejecución: cuándo, quién, fuentes consultadas, creadas, publicadas, errores.
- Estado por sección: activa, automática o manual, enfoque, piezas hoy, cupo restante.
- Histórico de las últimas ejecuciones y enlace a la edición generada.
- Acciones: ejecutar ahora (F2) y activar/desactivar automático por sección (F6).
- Reutiliza el sistema visual del panel; enlace en la navegación.

Criterio de aceptación: tras dos ejecuciones la página muestra ambas con sus cifras y el
estado de cada sección coincide con la BD. Depende de: F2, F5.
TXT

crear "F8 · Generador de noticias desde un tema o titular (agente IA)" <<'TXT'
Objetivo: dado un tema o un titular, armar una noticia con lo que digan las fuentes.

Flujo:
- Búsqueda de los términos contra lo ya ingestado y, opcionalmente, contra feeds
  adicionales y búsqueda semántica (SEARCH_SEMANTIC).
- Recopilación de fragmentos con medio, URL y fecha.
- Redacción: el modelo solo puede afirmar lo que esté en los fragmentos. Hereda el
  enfoque de la sección elegida (ADR-012).
- Verificación: cada párrafo ligado a las fuentes que lo sostienen; un dato que no
  aparece en ninguna fuente se elimina, no se suaviza.
- Salida: DRAFT marcado como generado con IA, con fuentes usadas y prompt aplicado.

Reglas duras:
- Nunca publica sola por defecto; solo si PUBLISH_AUTO está activo en esa sección.
- Menos de 2 fuentes independientes: se aborta y se explica por qué.
- Marca visible de generado con IA y número de fuentes.
- Pasa por BudgetPort (coste acotado).
- Se guardan fuentes, fragmentos, enfoque y versión de prompt.

Criterio de aceptación: un tema con cobertura real produce un borrador rastreable a
las fuentes listadas; un tema sin cobertura no inventa y dice que no hay material.
Depende de: F1, F3, F4.
TXT

echo
echo "Listo. Ids en /tmp/kanban-fases-ids.txt"
