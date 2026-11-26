#!/usr/bin/env bash
# Edición diaria de Noticias Planeta (F1).
#
# Ingesta todas las fuentes activas, selecciona piezas con foto de las últimas
# 48 h y las publica. Sin IA: solo reglas.
#
#   ./scripts/edicion-diaria.sh              publica (modo por defecto)
#   ./scripts/edicion-diaria.sh --draft      deja las piezas en borrador
#   ./scripts/edicion-diaria.sh --limit 10   cuantas piezas como maximo
#   ./scripts/edicion-diaria.sh --dry-run    no escribe nada, solo informa
#
# El modo por defecto se puede fijar en .env con EDICION_MODO=draft|publish,
# para no tener que recordar el flag. El flag siempre manda sobre el .env.
set -uo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"

API="${EDICION_API:-https://localhost}"
DB="${EDICION_DB:-noticiasplaneta-db-1}"
APP="${EDICION_APP:-noticiasplaneta-app-1}"
MODO="${EDICION_MODO:-publish}"
LIMITE="${EDICION_LIMITE:-20}"
MAX_POR_FUENTE="${EDICION_MAX_POR_FUENTE:-3}"
MAX_POR_SECCION="${EDICION_MAX_POR_SECCION:-5}"
VENTANA_HORAS="${EDICION_VENTANA_HORAS:-48}"
DRY_RUN=0

while [ $# -gt 0 ]; do
    case "$1" in
        --draft)    MODO="draft" ;;
        --publish)  MODO="publish" ;;
        --limit)    LIMITE="$2"; shift ;;
        --dry-run)  DRY_RUN=1 ;;
        -h|--help)  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Opcion desconocida: $1" >&2; exit 2 ;;
    esac
    shift
done

if [ "$MODO" != "draft" ] && [ "$MODO" != "publish" ]; then
    echo "EDICION_MODO debe ser draft o publish (es '$MODO')" >&2
    exit 2
fi

FECHA="$(date +%Y-%m-%d)"
mkdir -p logs ediciones
LOG="logs/edicion-${FECHA}.log"
EDICION="ediciones/${FECHA}.md"

log() { printf '%s %s\n' "$(date +%H:%M:%S)" "$*" | tee -a "$LOG"; }
psql_q() { docker exec -i "$DB" psql -U noticias -d noticias -tAc "$1"; }
psql_f() { docker exec -i "$DB" psql -U noticias -d noticias -tAF'|' -c "$1"; }

log "=== edicion ${FECHA} · modo=${MODO} · limite=${LIMITE} ==="

# ---------- 1. ingesta ----------
KEY="$(docker exec "$APP" printenv AGENT_API_KEY 2>/dev/null)"
if [ -z "${KEY:-}" ]; then
    log "ERROR: no se pudo leer AGENT_API_KEY de $APP"
    exit 1
fi

FALLOS=0
while IFS='|' read -r ID NOMBRE; do
    [ -z "${ID:-}" ] && continue
    RESPUESTA="$(curl -sk -X POST "${API}/api/v1/sources/${ID}/ingest" \
        -H "X-Api-Key: ${KEY}" --max-time 180 2>/dev/null)"
    if printf '%s' "$RESPUESTA" | grep -q '"created"'; then
        CREADAS="$(printf '%s' "$RESPUESTA" | grep -o '"created":[0-9]*' | cut -d: -f2)"
        log "  fuente ${NOMBRE}: +${CREADAS}"
    else
        log "  fuente ${NOMBRE}: FALLO ${RESPUESTA:0:120}"
        FALLOS=$((FALLOS + 1))
    fi
done < <(psql_f "select id, name from source where state <> 'DISABLED' order by name;")

# ---------- 1.5 de item de ingesta a borrador ----------
# La ingesta guarda ingest_item; el articulo no existe hasta que alguien lo crea.
# Sin este paso el portal se queda con 1889 articulos del primer dia y nada nuevo.
# Clasificacion por palabras clave; lo que no case va a 'general'.
NUEVOS_ANTES="$(psql_q "select count(*) from article;")"
psql_q "
insert into article (id, language, section_id, origin, ingest_item_id, slug, title, body, state, created_at)
select gen_random_uuid(), 'es',
       case
         when i.title ilike '%tecnolog%' or i.title ilike '%inteligencia artificial%'
           or i.title ilike '%cibersegur%' or i.title ilike '%google%' or i.title ilike '%apple%'
           or i.title ilike '%microsoft%' or i.title ilike '%chatgpt%' or i.title ilike '%algoritm%'
           then 'tecnologia'
         when i.title ilike '%gobierno%' or i.title ilike '%congreso%' or i.title ilike '%senado%'
           or i.title ilike '%eleccion%' or i.title ilike '%partido%' or i.title ilike '%sánchez%'
           or i.title ilike '%feijóo%' or i.title ilike '%ayuso%' or i.title ilike '%ministro%'
           or i.title ilike '%parlamento%' or i.title ilike '%ley %'
           then 'politica'
         else 'general'
       end,
       'INGEST', i.id::text,
       -- slug valido: sin guiones de sobra ni al final (el agregado lo rechaza y la
       -- lectura de la pieza daba 500); el sufijo del id garantiza unicidad.
       trim(both '-' from left(
           coalesce(
               nullif(trim(both '-' from regexp_replace(lower(i.title), '[^a-z0-9]+', '-', 'g')), ''),
               'noticia'),
           60)) || '-' || left(i.id::text, 8),
       i.title, left(coalesce(i.body, ''), 20000), 'DRAFT',
       coalesce(i.published_at, i.created_at)
from ingest_item i
where i.image_url is not null
  and not exists (select 1 from article a where a.ingest_item_id = i.id::text)
  and coalesce(i.published_at, i.created_at) > now() - interval '${VENTANA_HORAS} hours'
order by coalesce(i.published_at, i.created_at) desc
limit 200;" >/dev/null
NUEVOS_DESPUES="$(psql_q "select count(*) from article;")"
log "borradores nuevos: $((NUEVOS_DESPUES - NUEVOS_ANTES)) (total ${NUEVOS_DESPUES})"

# ---------- 2. seleccion ----------
# Reglas: borrador, con foto, dentro de la ventana, seccion activa, y tope por
# fuente y por seccion. Determinista: no hay modelo que elegir aqui.
SELECCION="$(psql_f "
with candidatos as (
    select a.id, a.title, a.section_id, sc.nombre as seccion, s.name as fuente,
           i.canonical_url, i.image_url,
           coalesce(i.published_at, i.created_at) as fecha,
           row_number() over (partition by i.source_id
                              order by coalesce(i.published_at, i.created_at) desc) as rn_fuente,
           row_number() over (partition by a.section_id
                              order by coalesce(i.published_at, i.created_at) desc) as rn_seccion
    from article a
    join ingest_item i on i.id::text = a.ingest_item_id
    join source s on s.id = i.source_id
    join section_config sc on sc.slug = a.section_id and sc.activa
    where a.state = 'DRAFT'
      and i.image_url is not null
      and coalesce(i.published_at, i.created_at) > now() - interval '${VENTANA_HORAS} hours'
)
select id, title, section_id, seccion, fuente, canonical_url, image_url
from candidatos
where rn_fuente <= ${MAX_POR_FUENTE} and rn_seccion <= ${MAX_POR_SECCION}
order by fecha desc
limit ${LIMITE};")"

TOTAL="$(printf '%s\n' "$SELECCION" | grep -c '|' || true)"
log "seleccionadas: ${TOTAL} piezas (tope ${MAX_POR_FUENTE}/fuente, ${MAX_POR_SECCION}/seccion, ventana ${VENTANA_HORAS}h)"

if [ "$TOTAL" -eq 0 ]; then
    log "nada que publicar: no hay borradores con foto dentro de la ventana"
    [ "$FALLOS" -gt 0 ] && exit 1
    exit 0
fi

if [ "$DRY_RUN" -eq 1 ]; then
    printf '%s\n' "$SELECCION" | while IFS='|' read -r _ TITULO SECC _NOMBRE FUENTE _URL _IMG; do
        printf '  [%s] %s — %s\n' "$SECC" "${TITULO:0:60}" "$FUENTE"
    done
    log "dry-run: no se ha escrito nada"
    exit 0
fi

# ---------- 3. publicar o dejar en borrador ----------
IDS="$(printf '%s\n' "$SELECCION" | cut -d'|' -f1 | sed "s/.*/'&'/" | paste -sd, -)"
if [ "$MODO" = "publish" ]; then
    psql_q "update article set state = 'PUBLISHED', published_at = now()
            where id in (${IDS}) and state = 'DRAFT';" >/dev/null
    log "publicadas ${TOTAL} piezas"
else
    log "modo borrador: ${TOTAL} piezas se quedan en DRAFT (revisar y publicar desde el panel)"
fi

# ---------- 4. edicion en markdown ----------
{
    echo "# Noticias Planeta — ${FECHA}"
    echo
    echo "Edición ${MODO} · ${TOTAL} piezas · ventana ${VENTANA_HORAS} h · sin IA"
    echo
    printf '%s\n' "$SELECCION" | while IFS='|' read -r _ TITULO SECC NOMBRE FUENTE URL _IMG; do
        echo "## ${NOMBRE}"
        echo
        echo "### ${TITULO}"
        echo
        echo "[Fuente: ${FUENTE}](${URL})"
        echo
    done
} > "$EDICION"
log "edicion escrita en ${EDICION}"

if [ "$FALLOS" -gt 0 ]; then
    log "ATENCION: ${FALLOS} fuentes fallaron"
    exit 1
fi
log "=== fin ==="
