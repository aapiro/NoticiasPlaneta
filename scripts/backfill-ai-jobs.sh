#!/usr/bin/env bash
# Reencola el enriquecido de los artículos publicados.
# Requiere que la app tenga DEEPSEEK_API_KEY y que el stack esté arriba.
set -euo pipefail

cd "$(dirname "$0")/.."
docker compose exec -T db psql -U noticias -d noticias < scripts/backfill-ai-jobs.sql
