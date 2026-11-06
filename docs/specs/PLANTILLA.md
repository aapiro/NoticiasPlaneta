# F<NN> — <Nombre del flujo>

**Módulo:** `<modulo>` · **Estado:** borrador | revisión | aprobada · **Versión:** 1

## 1. Contexto
Dos o tres frases: qué problema resuelve y por qué existe este flujo.

## 2. Actores
- Humano / agente / sistema, y qué rol tiene.

## 3. Precondiciones
Qué debe ser cierto antes de empezar.

## 4. Flujo principal
Pasos numerados. Sin ramas.

## 5. Flujos alternativos y errores
| Código | Situación | Resultado esperado |
|---|---|---|
| E01 | ... | ... |

## 6. Reglas de negocio
- RN1: ...

## 7. Criterios de aceptación (Gherkin)
```gherkin
@F01 @aceptacion
Escenario: <nombre>
  Dado ...
  Cuando ...
  Entonces ...
```

## 8. Datos
Tablas afectadas, columnas nuevas, índices, migración.

## 9. Puertos y adaptadores
- **Entrada:** `XxxUseCase` (interfaz en `application/port/in`)
- **Salida:** `XxxRepository`, `YyyGateway` (interfaz en `application/port/out`)
- **Adaptadores:** `JpaXxxRepository` (out), `XxxController` / `McpTool` / `CronJob` (in)

## 10. Eventos
Qué se publica, con qué payload, quién lo consume y si es versionado.

## 11. Tests TDD (lista RED inicial)
1. ...

## 12. No funcional
Rendimiento, seguridad, accesibilidad, coste.

## 13. Riesgos y preguntas abiertas
