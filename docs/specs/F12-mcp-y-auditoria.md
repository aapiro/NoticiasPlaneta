# F12 — Servidor MCP para agentes y auditoría

**Módulo:** `mcp` + `shared` · **Estado:** borrador · **Versión:** 1

## 1. Contexto
Los agentes no usan el panel: usan herramientas. Este flujo define el contrato, los
permisos por perfil y la trazabilidad de todo lo que tocan.

## 2. Actores
- Perfiles de Hermes (`newsroom-ingesta`, `-redaccion`, `-auditor`, `-ops`)

## 3. Precondiciones
- Token vigente con `agent_id` y scopes.
- Petición desde la red privada (o Cloudflare Access).

## 4. Flujo principal
1. El agente envía `tools/call` con `Authorization: Bearer <token>` y `X-Agent-Id`.
2. El servidor MCP valida token, scope y que la herramienta esté permitida para ese perfil.
3. Valida el esquema de entrada (JSON Schema) antes de tocar el dominio.
4. Invoca el caso de uso correspondiente (el mismo que usa el panel).
5. Escribe `audit_log` con `actor_type = agent`, `agent_id`, herramienta y diff.
6. Devuelve el resultado; si es error de negocio, código estable (no stacktrace).

## 5. Alternativos y errores
| Código | Situación | Resultado |
|---|---|---|
| E01 | Token inválido/expirado | `401` |
| E02 | Scope insuficiente | `403 SCOPE_DENIED` |
| E03 | Herramienta inexistente | `404` |
| E04 | Entrada fuera de esquema | `422` |
| E05 | Agente pide `unpublish` sin scope | `403` + alerta |

## 6. Reglas
- **RN1** Un agente usa las mismas reglas de dominio que un humano. No hay atajos.
- **RN2** `publish` no se expone como herramienta. Nunca.
- **RN3** Toda llamada queda auditada, incluida la lectura de datos sensibles.
- **RN4** Los errores devuelven código, mensaje y `traceId`; nunca la traza completa.
- **RN5** Rate limit por `agent_id`.
- **RN6** El contenido recuperado de fuentes se devuelve **marcado como datos no confiables**.

## 7. Gherkin
```gherkin
@F12 @aceptacion
Escenario: Un agente sin scope no puede despublicar
  Dado un token de newsroom-redaccion sin scope unpublish
  Cuando invoca la herramienta unpublish
  Entonces se rechaza con 403 SCOPE_DENIED
  Y se escribe auditoría del intento

@F12 @aceptacion
Escenario: Publicar no es una herramienta disponible
  Dado cualquier token de agente
  Cuando se lista tools/list
  Entonces no aparece ninguna herramienta que pase un artículo a published

@F12 @aceptacion
Escenario: Toda llamada queda auditada
  Dado un agente que llama create_draft
  Cuando la llamada termina correctamente
  Entonces existe una fila en audit_log con actor_type=agent y el agent_id
```

## 8. Datos
`agent_token(id, agent_id, scopes, expires_at, revoked_at)`
`audit_log(id, actor_id, actor_type, action, entity, entity_id, before, after, trace_id, at)`

## 9. Puertos
In: servidor MCP (adaptador de entrada) · Out: los mismos casos de uso del panel + `AuditPort`

## 10. Eventos
Único evento propio: `AgentActionDenied.v1` para alertas de seguridad.

## 11. Tests RED
1. Token sin scope → 403.
2. publish no está en `tools/list`.
3. Esquema inválido → 422 antes de tocar dominio.
4. Auditoría escrita en éxito y en fracaso.
5. Rate limit por agente.
6. Frontera: MCP no puede importar `adapter/out` de otros módulos (ArchUnit).

## 12. NFR
p95 de herramienta < 300 ms (sin contar enriquecido). Registro de auditoría no bloquea la respuesta.
