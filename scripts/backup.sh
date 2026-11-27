#!/usr/bin/env bash
#
# Copia de seguridad de NoticiasPlaneta (backlog #8).
#
# Un volcado que nunca se ha restaurado no es una copia de seguridad: es un fichero.
# Por eso, por defecto, este script restaura el volcado en una base temporal y cuenta
# las filas. Si eso falla, el script falla.
#
# Uso:
#   ./scripts/backup.sh                    volcado + verificación + retención
#   ./scripts/backup.sh --sin-verificar    solo volcado (copias grandes o prisa)
#   ./scripts/backup.sh --restaurar F      restaura F sobre la base actual (pide confirmación)
#   ./scripts/backup.sh --listar           lista las copias disponibles
#
# Entorno (con valores por defecto):
#   BACKUP_DIR (backups)  BACKUP_RETENCION_DIAS (14)  DB_CONTAINER  DB_USER  DB_NAME
#
set -euo pipefail

DESTINO="${BACKUP_DIR:-backups}"
RETENCION="${BACKUP_RETENCION_DIAS:-14}"
CONTENEDOR="${DB_CONTAINER:-noticiasplaneta-db-1}"
USUARIO="${DB_USER:-noticias}"
BASE="${DB_NAME:-noticias}"

VERIFICAR=1
RESTAURAR=""

while [ $# -gt 0 ]; do
    case "$1" in
        --sin-verificar) VERIFICAR=0 ;;
        --restaurar) RESTAURAR="${2:-}"; shift ;;
        --listar) ls -lh "$DESTINO"/*.dump 2>/dev/null || echo "No hay copias en $DESTINO"; exit 0 ;;
        -h|--help) sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Opción desconocida: $1" >&2; exit 2 ;;
    esac
    shift
done

log() { printf '  %s\n' "$*"; }
fallo() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

psql_en() { docker exec -i "$CONTENEDOR" psql -U "$USUARIO" -tAc "$1" 2>&1; }

[ -n "$(docker ps -q -f "name=^${CONTENEDOR}$")" ] || fallo "el contenedor $CONTENEDOR no está corriendo"

# --- Restauración manual -----------------------------------------------------
if [ -n "$RESTAURAR" ]; then
    [ -f "$RESTAURAR" ] || fallo "no existe el fichero $RESTAURAR"
    # Antes de pedir confirmación: si el fichero no es un dump legible, no hay nada
    # que confirmar. Preguntar "¿seguro?" por un fichero roto es una trampa.
    docker exec -i "$CONTENEDOR" pg_restore -l < "$RESTAURAR" >/dev/null 2>&1 \
        || fallo "$RESTAURAR no es un volcado legible con pg_restore"
    echo "Vas a reemplazar el contenido de la base '$BASE' con $RESTAURAR"
    printf "Escribe RESTAURAR para continuar: "
    read -r respuesta
    [ "$respuesta" = "RESTAURAR" ] || fallo "cancelado"

    log "limpiando esquema público"
    psql_en "drop schema public cascade; create schema public;" >/dev/null
    log "restaurando (esto tarda)"
    docker exec -i "$CONTENEDOR" pg_restore -U "$USUARIO" -d "$BASE" --no-owner --no-privileges < "$RESTAURAR" \
        || fallo "pg_restore devolvió error"
    log "artículos restaurados: $(psql_en "select count(*) from article")"
    exit 0
fi

# --- Volcado -----------------------------------------------------------------
mkdir -p "$DESTINO"
SELLO="$(date +%Y%m%d-%H%M%S)"
FICHERO="$DESTINO/noticias-$SELLO.dump"

log "volcando $BASE desde $CONTENEDOR"
docker exec -i "$CONTENEDOR" pg_dump -U "$USUARIO" -d "$BASE" -Fc --no-owner --no-privileges > "$FICHERO"

[ -s "$FICHERO" ] || fallo "el volcado salió vacío"
TAMANO="$(ls -lh "$FICHERO" | awk '{print $5}')"
log "volcado: $FICHERO ($TAMANO)"

# Un fichero con cabecera válida no garantiza que se pueda restaurar.
docker exec -i "$CONTENEDOR" pg_restore -l < "$FICHERO" >/dev/null 2>&1 \
    || fallo "el volcado no se puede leer con pg_restore"

# --- Verificación: restaurar en una base temporal ----------------------------
if [ "$VERIFICAR" -eq 1 ]; then
    TEMPORAL="${BASE}_verificacion"
    log "verificando: restaurando en $TEMPORAL"

    psql_en "drop database if exists $TEMPORAL" >/dev/null
    psql_en "create database $TEMPORAL" >/dev/null || fallo "no se pudo crear la base temporal"

    # Se limpia siempre, aunque la restauración falle a medias.
    limpiar() { psql_en "drop database if exists $TEMPORAL" >/dev/null 2>&1 || true; }
    trap limpiar EXIT

    docker exec -i "$CONTENEDOR" pg_restore -U "$USUARIO" -d "$TEMPORAL" --no-owner --no-privileges \
        < "$FICHERO" >/dev/null 2>&1 || log "aviso: pg_restore reportó incidencias (permisos, roles); se valida por conteo"

    ORIGINAL="$(psql_en "select count(*) from article")"
    RESTAURADO="$(docker exec -i "$CONTENEDOR" psql -U "$USUARIO" -d "$TEMPORAL" -tAc "select count(*) from article" 2>&1)"

    if ! [[ "$RESTAURADO" =~ ^[0-9]+$ ]]; then
        fallo "la restauración no dejó una tabla article legible: $RESTAURADO"
    fi
    [ "$ORIGINAL" = "$RESTAURADO" ] \
        || fallo "conteo distinto: origen $ORIGINAL, restaurado $RESTAURADO"

    log "verificado: $RESTAURADO artículos restaurados, idénticos al origen"
fi

# --- Retención ---------------------------------------------------------------
BORRADAS=0
while IFS= read -r viejo; do
    [ -n "$viejo" ] || continue
    rm -f "$viejo"
    BORRADAS=$((BORRADAS + 1))
done < <(find "$DESTINO" -name 'noticias-*.dump' -type f -mtime "+$RETENCION" 2>/dev/null)

[ "$BORRADAS" -gt 0 ] && log "retención: $BORRADAS copia(s) de más de $RETENCION días eliminadas"

TOTAL="$(find "$DESTINO" -name 'noticias-*.dump' -type f | wc -l | tr -d ' ')"
log "copias guardadas: $TOTAL"
echo "OK $FICHERO"
