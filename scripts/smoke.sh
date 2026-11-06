#!/usr/bin/env bash
# Smoke test de extremo a extremo contra el stack de Docker Compose.
# Uso: ./scripts/smoke.sh [base_url]
set -euo pipefail

BASE="${1:-https://localhost}"
CURL="curl -sk"
API_KEY="${AGENT_API_KEY:-clave-de-desarrollo}"
AUTH=(-H "X-Api-Key: $API_KEY")

echo "== health =="
$CURL -f "$BASE/actuator/health" || { echo "FALLO: la app no responde"; exit 1; }
echo

echo "== crear fuente =="
SOURCE_JSON=$($CURL -f -X POST "$BASE/api/v1/sources" "${AUTH[@]}" \
  -H 'Content-Type: application/json' \
  -d '{"name":"El País","feedUrl":"https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada"}')
echo "$SOURCE_JSON"
SOURCE_ID=$(echo "$SOURCE_JSON" | sed -E 's/.*"id":"([^"]+)".*/\1/')

echo "== ingesta =="
$CURL -f -X POST "$BASE/api/v1/sources/$SOURCE_ID/ingest" "${AUTH[@]}" -H 'Content-Type: application/json'
echo

echo "== crear borrador =="
DRAFT_JSON=$($CURL -f -X POST "$BASE/api/v1/articles" "${AUTH[@]}" \
  -H 'Content-Type: application/json' \
  -d '{"sectionId":"economia","title":"Prueba de humo","body":"Cuerpo","origin":"MANUAL"}')
echo "$DRAFT_JSON"
ARTICLE_ID=$(echo "$DRAFT_JSON" | sed -E 's/.*"id":"([^"]+)".*/\1/')

echo "== transición IN_REVIEW =="
$CURL -f -X POST "$BASE/api/v1/articles/$ARTICLE_ID/transitions" "${AUTH[@]}" \
  -H 'Content-Type: application/json' \
  -d '{"target":"IN_REVIEW","actorId":"u1","actorType":"HUMAN","roles":["AUTHOR"],"sections":["economia"]}'
echo

echo "== intento ilegal DRAFT→PUBLISHED debe dar 409 =="
CODE=$($CURL -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/articles/$ARTICLE_ID/transitions" "${AUTH[@]}" \
  -H 'Content-Type: application/json' \
  -d '{"target":"PUBLISHED","actorId":"u1","actorType":"HUMAN","roles":["CHIEF"],"sections":["economia"]}')
echo "HTTP $CODE"
[ "$CODE" = "409" ] || { echo "FALLO: se esperaba 409"; exit 1; }

echo "== agente intentando publicar debe dar 403 =="
for target in APPROVED SCHEDULED; do
  $CURL -f -X POST "$BASE/api/v1/articles/$ARTICLE_ID/transitions" "${AUTH[@]}" \
    -H 'Content-Type: application/json' \
    -d "{\"target\":\"$target\",\"actorId\":\"u1\",\"actorType\":\"HUMAN\",\"roles\":[\"CHIEF\"],\"sections\":[\"economia\"]}" > /dev/null
done
CODE=$($CURL -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/articles/$ARTICLE_ID/transitions" "${AUTH[@]}" \
  -H 'Content-Type: application/json' \
  -d '{"target":"PUBLISHED","actorId":"newsroom-redaccion","actorType":"AGENT","roles":["CHIEF"]}')
echo "HTTP $CODE"
[ "$CODE" = "403" ] || { echo "FALLO: un agente no debería poder publicar"; exit 1; }

echo "== publicar como CHIEF humano debe dar 200 =="
$CURL -f -X POST "$BASE/api/v1/articles/$ARTICLE_ID/transitions" "${AUTH[@]}" \
  -H 'Content-Type: application/json' \
  -d '{"target":"PUBLISHED","actorId":"u-chief","actorType":"HUMAN","roles":["CHIEF"],"sections":["economia"]}'
echo

echo "OK: smoke test completo"
