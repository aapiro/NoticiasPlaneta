# ADR-008 — Despliegue self-hosted desde el día 1 (sin CDN ni SaaS)

**Estado:** aceptada · **Fecha:** 2026-11-06 · **Decisores:** Tony, Dev

## Contexto
Se quiere arrancar **full on-premise** y dejar los servicios externos para más adelante.
Cloudflare, R2 y cualquier SaaS quedan fuera de la fase 1.

## Decisión
Todo el sistema corre en máquinas propias, sin dependencias externas obligatorias:

| Pieza | Fase 1 (self-hosted) | Fase 2 (cuando duela) |
|---|---|---|
| Cómputo | VPS propio (o mini PC en casa) con Docker Compose | Igual, o varios nodos |
| TLS | **Caddy** con Let's Encrypt automático | Igual |
| Caché / estáticos | **Caddy `proxy_cache`** + cabeceras `Cache-Control` desde la app | Cloudflare delante (opcional) |
| Medios | **`LocalFilesystemStorageAdapter`** detrás de `ObjectStoragePort` | S3/R2 con el mismo puerto |
| Búsqueda | PostgreSQL + pgvector (ya es local) | Igual |
| LLM | **Ollama local** por defecto; API externa opcional por configuración | Igual |
| Email | Deshabilitado; `Mailpit` en desarrollo | SMTP propio o proveedor |
| Observabilidad | Prometheus + Grafana + Loki (o Uptime Kuma al principio) | Igual |
| Backups | **restic** a disco externo/NAS | + réplica fuera |

## Reglas de diseño que esto impone
- **RN1** Ningún módulo puede depender de una API de CDN: la purga es un puerto (`CachePurgePort`)
  con adaptador local (fase 1) y de CDN (fase 2).
- **RN2** El almacenamiento es un puerto (`ObjectStoragePort`). Cambiar de disco a S3 es un adaptador.
- **RN3** El render no asume CDN: se apoya en `Cache-Control` + `ETag`, que Caddy respeta.
- **RN4** La app debe arrancar completa con `docker compose up` sin ninguna clave externa.
- **RN5** El LLM es opcional: sin él, el sistema funciona con enriquecido deshabilitado.

## Alternativas
| Alternativa | Por qué no (todavía) |
|---|---|
| Cloudflare desde el día 1 | Añade una dependencia externa y trabajo manual de DNS antes de tener producto |
| MinIO como S3 local | Repo no mantenido desde 2026; AIStor exige licencia |
| Garage / SeaweedFS | Válidos, pero sobran para una sola máquina; se evalúan si hace falta S3 real |
| Servir desde casa con IP doméstica | IP dinámica, cortes de luz y de fibra; sirve para staging, no para producción |

## Consecuencias
- Positivas: cero coste variable, cero lock-in, todo reproducible en local, y las pruebas
  de integración corren contra la misma tecnología que producción.
- Negativas: la disponibilidad es la de tu máquina. Se mitiga con backups, `restart: always`
  y un chequeo externo de disponibilidad (Uptime Kuma o un cron de Hermes que avise).
- Riesgo: si el VPS cae, no hay CDN que sirva caché. Se acepta en fase 1.

## Hardware decidido (2026-11-06)
VPS con **32 GB de RAM**. Sobra para todo el stack y permite Ollama con `bge-m3`
(~2 GB) sin sacrificar PostgreSQL ni la JVM. Reparto previsto:

| Componente | RAM estimada |
|---|---|
| PostgreSQL + pgvector | 4 GB (`shared_buffers` 1 GB) |
| JVM de la aplicación | 2–3 GB (`MaxRAMPercentage=75` del contenedor) |
| Ollama + bge-m3 | 2–3 GB |
| Caddy + sistema | < 1 GB |
| **Margen** | > 20 GB |

Queda pendiente el destino de backups.
