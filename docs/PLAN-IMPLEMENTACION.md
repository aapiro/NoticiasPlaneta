# Plan de implementación

Estado a 2026-11-06. Orden pensado para que cada fase deje algo desplegable y probado.

## Fase 0 — Cimientos (hecho)

| Entregable | Estado |
|---|---|
| Scaffold Boot 4.1.2, release 21, Maven wrapper | ✅ |
| Flyway V1 (`source`, `ingest_item`) y V2 (`article`, `audit_log`) | ✅ |
| ArchUnit: dominio sin framework, módulos solo por `port/in` | ✅ |
| Testcontainers contra PostgreSQL real | ✅ |

## Fase 1 — Núcleo editorial (hecho)

| Flujo | Entregable | Estado |
|---|---|---|
| **F01** Ingesta | fetch HTTP con ETag/304, dedupe por externalId y huella, degradación tras 5 fallos | ✅ |
| **F02** Borradores | creación idempotente, slug único por idioma | ✅ |
| **F05** Flujo editorial | máquina de estados en dominio, `AGENT_CANNOT_PUBLISH`, auditoría append-only | ✅ |

## Fase 2 — API y despliegue (hecho)

| Entregable | Detalle | Estado |
|---|---|---|
| API REST mínima | `POST /api/v1/sources`, `POST /api/v1/sources/{id}/ingest`, `POST /api/v1/articles`, `POST /api/v1/articles/{id}/transitions` | ✅ |
| Manejo de errores | `DomainException` → código HTTP + código de negocio estable | ✅ |
| Docker Compose | app + PostgreSQL/pgvector + Caddy | ✅ |
| Caddy | TLS automático, cabeceras de seguridad, caché | ✅ |
| CI | GitHub Actions: build + toda la suite | ✅ |
| Smoke test | `scripts/smoke.sh` contra el stack real | ✅ |

## Fase 3 — Medios y seguridad

- F08 medios con `LocalFilesystemStorageAdapter` detrás de `ObjectStoragePort`
- F09 passkeys (spring-security-webauthn) y dos cadenas de seguridad
- F07 despublicación con prioridad de purga

## Fase 4 — Descubrimiento (hecho)

- ✅ F03 enriquecido asíncrono (DeepSeek; embeddings por Ollama/bge-m3)
- ✅ F10 búsqueda híbrida con RRF
- F11 temas y delta "desde tu última visita"

## Fase 5 — Agentes (en curso)

- 🟡 F12 servidor MCP con 3 herramientas (buscar, leer, crear borrador); sin herramienta de publicación
- Skills de Hermes y cron de ingesta

## Fase 6 — Publicación programada (hecho)

- ✅ F06: `POST /api/v1/articles/{id}/schedule` y formulario del panel con fecha/hora.
- ✅ Scheduler cada 30 s, actor `SYSTEM`, outbox transaccional y relay idempotente.
- ✅ `article.scheduled_at` actúa como `publishAt`; el estado y el evento se guardan juntos.
- Pendiente: F04 fuentes y contraste, y la gestión de temas de F11.

## Deuda asumida y consciente

| Deuda | Cuándo se paga |
|---|---|
| Lock de ingesta en memoria (una instancia) | Al escalar a varias réplicas → advisory lock de PostgreSQL |
| Sin autenticación en la API (red interna) | Fase 3 con F09 |
| Sin caché HTTP todavía | Fase 2 con Caddy |
| Sin PIT/mutation testing en CI | Cuando haya más dominio |
