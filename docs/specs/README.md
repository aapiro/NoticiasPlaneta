# Especificaciones — NoticiasPlaneta

Metodología: **SDD** (spec antes de código) + **TDD** (test antes de implementación) +
**hexagonal** (dominio sin framework) + **vertical slices** (cada spec entrega algo desplegable).

## Cómo trabajamos

1. Se escribe la spec (este directorio). Sin spec aprobada no hay código.
2. De cada spec salen los **escenarios Gherkin** = tests de aceptación.
3. De cada escenario salen los **tests unitarios RED** del dominio y del caso de uso.
4. Implementación mínima hasta verde. Refactor. Solo entonces se pasa al adaptador.
5. ArchUnit + PIT + Testcontainers en CI. Sin eso, la spec no está cumplida.

## Orden de lectura

| Doc | Contenido |
|---|---|
| `000-dominio.md` | Lenguaje ubicuo, agregados, eventos, mapa de módulos |
| `001-nfr.md` | Requisitos no funcionales y presupuestos medibles |
| `PLANTILLA.md` | Plantilla obligatoria de spec de flujo |
| `F01..F12` | Flujos del sistema |

## Definition of Ready (una spec puede empezar a codificarse cuando…)

- [ ] Tiene actores, precondiciones y flujo principal sin ambigüedad
- [ ] Tiene criterios de aceptación en Gherkin, uno por regla de negocio
- [ ] Tiene los casos límite y los errores esperados enumerados
- [ ] Tiene el impacto en datos (tablas/migración) y en eventos
- [ ] Tiene puertos identificados (entrada/salida) y su adaptador
- [ ] Tiene presupuesto de rendimiento o se declara que no aplica
- [ ] Está revisada por negocio (Tony) y por quien la implementa

## Definition of Done

- [ ] Todos los escenarios Gherkin pasan como tests de aceptación
- [ ] Cobertura de dominio >= 90% **y** mutation score >= 70% (PIT) en el módulo
- [ ] Tests de integración con Testcontainers para cada adaptador de persistencia
- [ ] ArchUnit verde (fronteras de módulo, dominio sin framework)
- [ ] Sin `TODO`, sin `@Disabled`, sin sleeps en tests
- [ ] Migración Flyway versionada y reversible por delante (nunca editar una aplicada)
- [ ] Auditoría escribiendo `agent_id`/`actor_id` en toda transición de estado
- [ ] Observabilidad: log estructurado con `traceId` y métrica de la operación
