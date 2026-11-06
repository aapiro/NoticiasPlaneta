# Documentación de arquitectura — NoticiasPlaneta

| Directorio | Contenido |
|---|---|
| `specs/` | Especificaciones por flujo (SDD). Fuente de verdad funcional |
| `adr/` | Decisiones de arquitectura irreversibles o caras de revertir |

## Estado

- **Stack:** Java 25 LTS · Spring Boot 4.1.2 · PostgreSQL 18 + pgvector · Thymeleaf + htmx
- **Arquitectura:** monolito modular con puertos y adaptadores por módulo
- **Especificado:** 12 flujos (F01–F12) + dominio + NFR
- **Decidido:** 7 ADR
- **Siguiente:** implementar F01 con TDD (test de aceptación → unitarios → adaptador)

## Cómo se lee esto

1. `specs/000-dominio.md` — lenguaje y agregados.
2. `specs/001-nfr.md` — los umbrales que no se negocian.
3. Los flujos en orden: F01 → F12.
4. Los ADR cuando alguien pregunte "¿por qué así?".
