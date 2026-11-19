#!/usr/bin/env bash
# Confía (o deja de confiar) en la CA local de Caddy para que
# https://localhost cargue en Chrome, Safari y curl sin avisos.
#
#   ./scripts/trust-caddy-local.sh status     (por defecto)
#   ./scripts/trust-caddy-local.sh install    (pide autorización de macOS)
#   ./scripts/trust-caddy-local.sh remove     (deshace)
#
# No se toca nada de producción: solo el llavero de login de tu usuario.
set -euo pipefail

CONTAINER="noticiasplaneta-caddy-1"
COMMON_NAME="Caddy Local Authority - 2026 ECC Root"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CERT="$ROOT/.certs/caddy-root.crt"
KEYCHAIN="$HOME/Library/Keychains/login.keychain-db"

extraer() {
    if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
        echo "El contenedor $CONTAINER no está corriendo. Arráncalo primero:" >&2
        echo "  docker compose up -d" >&2
        exit 1
    fi
    mkdir -p "$(dirname "$CERT")"
    docker exec "$CONTAINER" cat /data/caddy/pki/authorities/local/root.crt > "$CERT"
    echo "CA extraída en $CERT ($(wc -c < "$CERT" | tr -d ' ') bytes)"
}

case "${1:-status}" in
    status)
        # Ojo: "security find-certificate -a" devuelve exit 0 aunque no encuentre nada.
        # La única comprobación fiable es mirar la salida y probar curl de verdad.
        if [ -n "$(security find-certificate -a -c "$COMMON_NAME" 2>/dev/null)" ]; then
            echo "certificado en el llavero : sí"
        else
            echo "certificado en el llavero : NO"
        fi

        if curl -sS -o /dev/null --max-time 5 https://localhost/ 2>/dev/null; then
            echo "curl estricto a localhost : OK (https://localhost carga sin avisos)"
        else
            echo "curl estricto a localhost : falla (el navegador lo rechazará)"
            echo "  instala con: $0 install"
        fi
        ;;

    install)
        extraer
        security add-trusted-cert -r trustRoot -k "$KEYCHAIN" "$CERT"
        echo "Listo. Reinicia el navegador y abre https://localhost"
        echo "Ojo: el certificado solo cubre DNS:localhost, no 127.0.0.1 ni la IP."
        ;;

    remove)
        security delete-certificate -c "$COMMON_NAME" "$KEYCHAIN"
        echo "CA retirada del llavero de login."
        ;;

    *)
        echo "Uso: $0 [status|install|remove]" >&2
        exit 2
        ;;
esac
