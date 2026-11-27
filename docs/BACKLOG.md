# Backlog — Noticias Planeta

Features que faltan para que el portal sea operativo de verdad, ordenados por
valor funcional / esfuerzo. Todo lo de esta lista **funciona sin IA**: las pruebas
del modelo quedan para el final, cuando haya clave.

Estado a 27/11/2026: 23 publicadas de 4302 artículos, 8 fuentes, 24 h de
retención, panel operativo, IA construida pero apagada.

| Prioridad | Feature | Valor | Coste | Por qué |
|---:|---|---|---|---|
| 95 | SEO técnico: sitemap, robots, Open Graph y JSON-LD | Muy alto | Bajo | Sin esto Google no indexa y los enlaces no generan vista previa. Es la diferencia entre existir y no existir |
| 90 | Imágenes propias: descargar y cachear las del feed | Alto | Medio | Hoy la portada hace hotlink a El País y ABC. Si cambian la URL o bloquean el referer, te quedas sin fotos |
| 85 | Paginación y listado por sección | Alto | Bajo | La portada corta en N. Con 23 publicadas y creciendo, hace falta paginar y que cada sección tenga su página |
| 80 | Gestión de fuentes desde el panel | Alto | Bajo | Añadir, activar y desactivar RSS sin SQL. Hoy se tocan a mano y ya hubo que añadir seis |
| 70 | RSS de salida del portal | Medio | Bajo | Sindicación y distribución; también hace que otros agregadores te citen |
| 65 | Programación de publicación | Medio | Medio | `scheduled_at` ya existe en la tabla; falta la interfaz y el worker que publique a su hora |
| 60 | Alertas de fuente degradada | Medio | Bajo | Las 8 fuentes estuvieron en DEGRADED y nadie se enteró. Aviso en el panel y por Telegram |
| 55 | Copia de seguridad automática de la base | Medio | Bajo | 4302 artículos sin copia. Un `pg_dump` diario y rotación de siete días |
| 50 | CI en GitHub Actions | Medio | Bajo | 317 tests que hoy se corren a mano. Que corran en cada push |
| 45 | Métricas de ingesta en el panel | Bajo | Bajo | Cuántas por fuente, duplicadas y rechazadas, y cómo evoluciona |

## Descartados por ahora

- **Comentarios**: moderación y RGPD para un portal que aún no tiene lectores.
- **Analítica con cookies**: contradice la línea de no rastrear del proyecto.
- **Multimedia/vídeo**: el producto es texto y enlace a la fuente.
- **Multi-idioma**: sin lectores todavía, es coste sin retorno.
- **App móvil**: la web responsive cubre el caso.

## Lo que NO entra aquí

Las pruebas de IA (generador, clasificación, piloto automático) quedan al final,
por decisión de producto. Están construidas y con tests, pero sin
`DEEPSEEK_API_KEY` no se pueden validar con datos reales.
