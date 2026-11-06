# NoticiasPlaneta

Portal de noticias hiperlocal. Monolito modular con puertos y adaptadores.

- **Java 21 (bytecode) · se ejecuta en JVM 25 LTS** · **Spring Boot 4.1.2** · **PostgreSQL 18 + pgvector** · Thymeleaf + htmx
- Documentación de arquitectura y especificaciones: [`docs/`](docs/README-docs.md)

## Requisitos

- JDK 21 o superior para compilar (el `pom` fija `release 21`; producción corre sobre JVM 25)
- Docker (Colima o Docker Desktop) para los tests de integración
- Maven (o `./mvnw`)

## Tests

```bash
make test        # suite completa (unit + ArchUnit + Testcontainers)
make test-unit   # solo lo rápido, sin Docker
```

En macOS con **Colima** hay que indicar el socket, o Testcontainers no arranca Ryuk:

```bash
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## Estructura

```
src/main/java/com/noticiasplaneta/
├── shared/     kernel: errores, eventos
├── ingest/     F01 · fuentes, fetch y deduplicación
└── ...         (news, taxonomy, media, identity, ai, search, mcp, admin)

    domain/         entidades y reglas, sin framework
    application/    port/in, port/out, service
    adapter/in      controladores, jobs, herramientas MCP
    adapter/out     JPA, HTTP, almacenamiento
```

Las fronteras se verifican con ArchUnit (`HexagonalArchitectureTest`).

## Arrancar

```bash
docker compose up -d --build      # app + PostgreSQL/pgvector + Caddy
./scripts/smoke.sh https://localhost
```

La app queda detrás de Caddy en `https://localhost` (certificado local) y el puerto 8080
solo es accesible dentro de la red de Compose.

## API

| Método | Ruta | Qué hace |
|---|---|---|
| POST | `/api/v1/sources` | registra una fuente RSS/Atom |
| POST | `/api/v1/sources/{id}/ingest` | ejecuta la ingesta de esa fuente |
| POST | `/api/v1/articles` | crea un borrador |
| POST | `/api/v1/articles/{id}/transitions` | cambia de estado (con reglas de rol) |
| POST | `/api/v1/media/presign` | firma una URL de subida directa |
| PUT | `/api/v1/media/blob/{key}` | recibe el binario (firma HMAC) |
| POST | `/api/v1/media/commit` | valida, reencodea y registra el activo |
| GET | `/api/v1/public/search?q=` | búsqueda híbrida pública |
| POST | `/mcp` | servidor MCP para agentes (exige `X-Api-Key`) |
| GET | `/actuator/health` | salud |

Todas las rutas de escritura exigen la cabecera `X-Api-Key` (agentes). La URL prefirmada de
subida lleva su propia firma HMAC y no necesita cabecera.

## Estado

| Flujo | Estado |
|---|---|
| F01 Ingesta de fuentes | implementado y probado |
| F02 Borradores desde ingesta | implementado y probado |
| F05 Flujo editorial y auditoría | implementado y probado |
| F08 Medios | implementado y probado |
| F09 Seguridad | cadenas y clave de agente; WebAuthn cableado |
| F10 Búsqueda híbrida | implementado y probado |
| F03 Enriquecido (DeepSeek) | implementado; requiere `DEEPSEEK_API_KEY` |
| F12 Servidor MCP | 3 herramientas para agentes, sin herramienta de publicación |
