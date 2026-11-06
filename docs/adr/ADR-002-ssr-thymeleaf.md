# ADR-002 — Render SSR con Thymeleaf en lugar de un frontend SPA

**Estado:** aceptada · **Fecha:** 2026-11-06 · **Decisores:** Tony, Dev

## Contexto
Un portal de noticias vive de LCP, INP y de que el HTML sea indexable y citable.
Se evaluaron dos opciones con sendos prototipos ejecutados.

## Evidencia medida (local, mismo contenido)
| Métrica | Thymeleaf (Boot 4.1.2) | Next.js 16.4.5 |
|---|---|---|
| TTFB p50 / p95 | 2,10 / 2,44 ms | 6,08 / 8,81 ms |
| JS al navegador | 0 B (HTML 424 B) | 556 KB sin comprimir (~170 KB gzip) |
| Arranque | 2,19 s | 228 ms |

## Decisión
**Spring Boot 4.1.2 + Thymeleaf + htmx**, un solo artefacto desplegable.
htmx en la línea 2.x (la 4.0 salió en agosto de 2026, pero 2.x sigue siendo la estable).

## Alternativas
| Alternativa | Veredicto |
|---|---|
| Next.js + API Spring | Descartada para el MVP: 170 KB de JS para pintar titulares |
| Thymeleaf + islas React puntuales | Válida si aparece una interacción muy rica. Se reserva |

## Consecuencias
- Positivas: CWV ganados por diseño, un solo CI, sin cadena de suministro npm.
- Negativas: no hay sistema de componentes tipado; la reutilización es `th:fragment`.
- **Escotillas**: los controladores devuelven DTOs (contrato HTTP listo), cero lógica en
  plantillas, htmx para lo interactivo.
- **Disparador de revisión**: si la portada personalizada supera el 30% del tráfico y el
  p95 de servidor pasa de 300 ms con caché caliente, se replantea.
