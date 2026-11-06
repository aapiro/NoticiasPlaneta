# ADR-007 — Hermes como orquestador, CMS como ejecutor

**Estado:** aceptada · **Fecha:** 2026-11-06

## Contexto
Se quiere que el sitio esté gestionado por uno o varios agentes de IA.

## Decisión
1. **El CMS expone un servidor MCP** con herramientas acotadas. Los agentes no usan navegador.
2. **Hermes** actúa como planificador: cron durable, skills versionadas, perfiles aislados,
   Kanban para trabajo largo.
3. **El humano aprueba la publicación.** El techo del agente es `schedule_publish`.
4. **`/mcp` no está expuesto a Internet**: red privada o Cloudflare Access + token por perfil.
5. **Todo write del agente lleva `agent_id`** en `audit_log`.

## Restricciones reales del runtime que condicionan el diseño
- Cron tiene un **corte duro de 3 minutos** por ejecución → el trabajo largo se modela como
  tarjetas de Kanban, no como job eterno.
- `delegate_task` **no es durable**: no se usa para trabajo que deba terminar sí o sí.

## Alternativas
| Alternativa | Veredicto |
|---|---|
| OpenClaw | Buen asistente personal/ops; el Radar Vol 35 lo pone en *Caution*. No para redacción |
| Paperclip | Capa de "empresa" (objetivos, presupuestos, organigrama). Se evalúa en fase 3 |
| Agente que navega el panel | Frágil, no auditable, imposible de testear |

## Consecuencias
- Positivas: la caída de Hermes no afecta al lector; todo es auditable y testeable.
- Negativas: hay que mantener el contrato MCP y sus versiones.
