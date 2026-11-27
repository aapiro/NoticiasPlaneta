#!/usr/bin/env bash
# Crea el backlog de Noticias Planeta en el kanban de Hermes, colgando de
# "Noticias Planeta" (t_dfca1c8f), ORDENADO POR VALOR / ESFUERZO.
#
# IMPORTANTE: las tareas se crean BLOQUEADAS a proposito. La vez anterior se
# crearon en "ready" y el dispatcher lanzo cuatro agentes en paralelo sobre el
# mismo repo. Bloqueadas no las arranca nadie hasta que se desbloqueen a mano.
#
#   ./scripts/kanban-backlog.sh            crea el backlog
#   ./scripts/kanban-backlog.sh --dry-run  solo muestra lo que haria
set -uo pipefail

BOARD="t_dfca1c8f"
K="hermes kanban --board default"
DRY=0
[ "${1:-}" = "--dry-run" ] && DRY=1

crear() {
    local titulo="$1" prioridad="$2"
    if [ "$DRY" -eq 1 ]; then
        printf '[dry] %3s  %s\n' "$prioridad" "$titulo"
        cat > /dev/null
        return 0
    fi
    local id
    id=$($K create "$titulo" --parent "$BOARD" --assignee dev-v1 --created-by dev-v1 \
             --priority "$prioridad" --initial-status blocked --body-file - --json 2>&1 \
         | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 \
         | sed 's/.*"\(t_[a-f0-9]*\)"/\1/')
    printf '%-10s %3s  %s\n' "${id:-FALLO}" "$prioridad" "$titulo"
    return 0
}

crear "1 · SEO técnico: sitemap, robots, Open Graph y JSON-LD" 95 <<'TXT'
Sin esto el portal no existe para Google y los enlaces compartidos salen sin
vista previa. Es el feature con mejor relacion valor/esfuerzo del backlog.

Trabajo:
- /sitemap.xml con las publicadas (y sitemap de noticias con fecha).
- /robots.txt apuntando al sitemap.
- Open Graph y Twitter Card en portada, sección y artículo (og:title,
  og:description, og:image con la del feed, og:url, article:published_time).
- JSON-LD NewsArticle en el detalle: headline, datePublished, dateModified,
  author, publisher, image, mainEntityOfPage.
- canonical en todas las páginas.

Aceptacion: el HTML del detalle valida como NewsArticle; pegar un enlace en
Telegram o WhatsApp genera vista previa con imagen y titular.
TXT

crear "2 · Imágenes propias: descargar y cachear las del feed" 90 <<'TXT'
Hoy la portada hace hotlink a imagenes.elpais.com y s1.abcstatics.com. Si cambian
la URL, bloquean el referer o caduca la firma, la portada se queda sin fotos.

Trabajo:
- Al crear el borrador, descargar la imagen del feed a media_asset (sha256,
  mime, ancho, alto) y servirla desde /media/.
- Limite de tamano y timeout; si falla, se deja sin foto y se registra.
- Reutilizar el MediaServingController que ya existe.
- Tarea de limpieza de huerfanas.

Aceptacion: la portada carga todas las fotos con el servidor de origen caido
(se puede probar bloqueando el dominio del medio).
TXT

crear "3 · Paginación y listado por sección" 85 <<'TXT'
La portada corta en un limite fijo. Con 23 publicadas y creciendo hace falta
paginar, y que cada sección tenga su propia pagina con su enfoque visible.

Trabajo:
- /?pagina=2 con enlaces anterior/siguiente y contador.
- /seccion/{slug} con listado paginado y la etiqueta de enfoque (ADR-012).
- La navegacion del portal ya se genera desde section_config: enlazar ahi.
- No romper el SEO: canonical y rel=prev/next.

Aceptacion: con 25 publicadas, la portada pagina y cada seccion lista solo lo suyo.
TXT

crear "4 · Gestión de fuentes desde el panel" 80 <<'TXT'
Hoy las fuentes se dan de alta por API o por SQL. Ya hubo que anadir seis a mano.

Trabajo:
- /admin/fuentes: listar, anadir (nombre + URL del feed, validando que parsea),
  activar/desactivar, y ver su estado y contador de fallos.
- Probar el feed antes de guardarlo y decir por que falla si falla.
- Un borrado no debe borrar articulos: solo desactiva.

Aceptacion: anadir un feed real desde el panel y ver las noticias en la
siguiente ejecucion, sin tocar SQL.
TXT

crear "5 · RSS de salida del portal" 70 <<'TXT'
Sindicacion propia: que otros agregadores y lectores puedan seguir el portal.

Trabajo:
- /rss.xml con las ultimas publicadas (titular, enlace, fecha, seccion, foto).
- /rss/{seccion}.xml por seccion.
- Etag y Cache-Control para no servir lo mismo siempre.

Aceptacion: un lector de RSS suscribe el feed y ve las 20 ultimas publicadas.
TXT

crear "6 · Programación de publicación" 65 <<'TXT'
article.scheduled_at existe desde el principio y no se usa.

Trabajo:
- Desde el panel, programar un articulo para una fecha y hora.
- Worker que publique lo programado cuando toca (mismo patron que el de IA,
  cada minuto, con registro en audit_log).
- Poder desprogramar y reprogramar.

Aceptacion: programar una pieza a dos minutos vista y ver que se publica sola.
TXT

crear "7 · Alertas de fuente degradada" 60 <<'TXT'
Las ocho fuentes estuvieron en DEGRADED con cinco fallos cada una y nadie se
entero hasta que miramos la base a mano.

Trabajo:
- Aviso visible en el panel (bandeja y estado de edicion) con las fuentes en
  DEGRADED y su ultimo error.
- Aviso por Telegram si esta configurado, con histeresis: una vez por fuente y
  dia, no en cada intento.
- Marcar como DISABLED tras N fallos consecutivos y avisar.

Aceptacion: apagar un feed a proposito y ver el aviso en el panel y en Telegram.
TXT

crear "8 · Copia de seguridad automática de la base" 55 <<'TXT'
4302 articulos y creciendo, sin una sola copia. Un error de migracion y se
pierde todo el trabajo de ingesta.

Trabajo:
- scripts/backup-bd.sh: pg_dump comprimido a backups/, con fecha.
- Rotacion: conservar siete diarias y cuatro semanales.
- Verificar que el dump se puede restaurar (no basta con que exista).
- Documentar en el runbook como restaurar.

Aceptacion: restaurar el dump en una base limpia y que la app arranque con los
articulos.
TXT

crear "9 · CI en GitHub Actions" 50 <<'TXT'
317 tests que hoy se corren a mano. Nadie garantiza que el que sube las rompa.

Trabajo:
- Workflow con Maven + Testcontainers en cada push y PR a master.
- Cache de dependencias para que tarde minutos, no media hora.
- Job separado de arquitectura (HexagonalArchitectureTest) para que un cambio
  de capas falle rapido.
- Badge en el README.

Aceptacion: un push con un test roto se ve en rojo en GitHub sin tocar local.
TXT

crear "10 · Métricas de ingesta en el panel" 45 <<'TXT'
Saber que hace la maquina sin abrir psql.

Trabajo:
- Por fuente: articulos creados hoy, duplicados, rechazados, ultimo error y
  ultima comprobacion.
- Evolucion de los ultimos siete dias.
- Total de articulos, borradores y publicados.

Aceptacion: el panel muestra las cifras y coinciden con la base de datos.
TXT

echo
echo "Backlog creado BLOQUEADO. Para arrancar uno: hermes kanban --board default unblock <id>"
