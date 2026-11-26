# ADR-014 · Ejecutar la edición desde el panel, en proceso y por la máquina de estados

- **Estado:** aceptado
- **Fecha:** 26/11/2026
- **Ámbito:** F2 (botón «Ejecutar edición») y F7 (panel de estado), módulo `edicion`

## Contexto

F1 entregó `scripts/edicion-diaria.sh`: un script bash que ingesta, crea borradores,
selecciona piezas con foto y publica. Funciona y está verificado, pero vive fuera de la
aplicación y hace dos cosas que el dominio no permite:

1. Publica con `update article set state = 'PUBLISHED'`, saltándose `TransitionPolicy` y sin
   emitir `ArticleStateChanged`. El outbox y el enriquecido con IA no se enteran.
2. No deja rastro en `audit_log`: no se puede decir quién publicó qué.

El panel necesita lanzar esa edición con sesión, CSRF y trazabilidad, y enseñar el resultado.
Hay tres formas de hacerlo y solo una respeta lo que ya está construido.

## Decisión

**La edición se ejecuta en proceso, invocando los casos de uso**, y su informe se guarda en
`edicion_ejecucion` (V20). No se lanza ningún proceso externo (`Runtime.exec`/`ProcessBuilder`)
y no se escribe SQL de publicación desde el panel.

Cadena real de una ejecución:

```
IngestAllSourcesUseCase.runDetailed()   → items nuevos por fuente, con errores por fuente
        ↓ (IngestItemReceivedListener ya crea el borrador de cada item)
EdicionStore.candidatos(...)            → borradores con foto, ventana, sección activa y cupo
TransitionArticleUseCase × 4            → IN_REVIEW → APPROVED → SCHEDULED → PUBLISHED
EdicionStore.cerrar(...)                → cifras + estado de la ejecución
```

Consecuencias directas de hacerlo así:

- Cada publicación pasa por `TransitionPolicy` (CHIEF/ADMIN, un agente nunca publica) y deja
  su entrada en `audit_log` y su evento de dominio. Cuatro transiciones por pieza: es el
  precio de no tener un atajo `DRAFT→PUBLISHED`, y es correcto que lo pague el panel.
- El candado contra ejecuciones solapadas es un **índice único parcial** en PostgreSQL
  (`where estado = 'EN_CURSO'`), no un flag en memoria: vale también con dos instancias.
- Una ejecución que quedó abierta porque el proceso murió se cierra sola como `FALLIDA`
  pasados 30 minutos, en la propia apertura de la siguiente. Sin eso, un despliegue en mitad
  de una edición bloquea el botón para siempre.

## Alternativas descartadas

| Opción | Por qué no |
|---|---|
| `Runtime.exec("scripts/edicion-diaria.sh")` desde el panel | El script publica por SQL (sin eventos ni auditoría), necesita docker y bash dentro del contenedor, y el informe hay que adivinarlo parseando logs |
| SQL de publicación desde el panel | Es el defecto que F1 ya tiene; reproducirlo en Java no lo arregla |
| Cola/worker asíncrono | Correcto a medio plazo (F6 lo necesita para publicar sin humano), pero hoy no hay nada que consuma la cola y una ejecución síncrona tarda decenas de segundos con 8 fuentes: se asume y se documenta |

## Lo que este ADR NO decide

- **Clasificación por sección.** Los borradores entran en `general` (lo hace
  `IngestItemReceivedListener` con `news.default-section`). El panel respeta `section_config`
  y su cupo, pero no reparte: eso es F4/F6. El panel lo enseña tal cual, sin maquillar.
- **Publicación automática.** El interruptor `auto_publica` se guarda y se muestra; todavía no
  publica sola. Es F6.
- **Idempotencia diaria del script.** El bash tiene guardia de fin de semana y edición única
  por día; el panel ejecuta cuando se lo piden, tantas veces como se pida, respetando el cupo
  por edición y sin repetir piezas ya publicadas (solo selecciona `DRAFT`).
