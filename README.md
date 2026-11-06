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

## Estado

| Flujo | Estado |
|---|---|
| F01 Ingesta de fuentes | implementado y probado |
