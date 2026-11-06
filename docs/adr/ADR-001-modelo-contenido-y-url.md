# ADR-001 — Modelo de contenido y URL canónica

**Estado:** aceptada · **Fecha:** 2026-11-06 · **Decisores:** Tony, Dev

## Contexto
Es la decisión menos reversible del proyecto. Cambiar la forma de la URL o la identidad
de un artículo rompe SEO, enlaces externos y citas de terceros (incluidos asistentes de IA).

## Decisión
1. **Artículo con revisiones inmutables**, no un documento que se sobrescribe.
2. **URL canónica** `/{seccion}/{slug}` en español, sin fechas ni identificadores.
3. **Slug inmutable tras la primera publicación.** Si el titular cambia, cambia el `<title>`,
   no la URL.
4. **`language` en la fila** y `unique(slug, language)` desde la migración V1, aunque hoy
   solo haya español.
5. Cambio de sección o de slug → **redirección 301 permanente** registrada en `url_redirect`.
6. Retirada → **410 Gone** durante 90 días, luego 404.

## Alternativas consideradas
| Alternativa | Por qué no |
|---|---|
| `/2026/11/06/titular` (estilo periódico) | Fecha en URL envejece el contenido y estorba en piezas vivas |
| Slug regenerado al cambiar el titular | Rompe todos los enlaces y el posicionamiento |
| ID numérico en la URL | Peor para el lector, igual para el buscador, sin ventaja |
| UUID como URL | Horrible para compartir y recordar |

## Consecuencias
- Positivas: enlaces estables de por vida, citables, y permite `hreflang` futuro sin migración.
- Negativas: hay que mantener `url_redirect` y un mapa de redirecciones por sección.
- Riesgo aceptado: colisión de slugs → se resuelve con sufijo numérico determinista.
