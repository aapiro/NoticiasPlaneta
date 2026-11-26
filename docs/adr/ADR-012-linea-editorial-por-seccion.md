# ADR-012 · Línea editorial por sección

**Estado:** aceptado · **Fecha:** 26/11/2026
**Sustituye parcialmente:** `brief-noticias-planeta.md` v1.0, apartado 3 (Idioma y tono)

## Contexto

El brief v1.0 definió Noticias Planeta como un agregador neutral: «cero opinión»,
«no publicamos análisis ni editorial», con secciones de clima, energía, naturaleza
y ciencia. El producto ha cambiado: Tecnología y Política pasan a ser secciones
principales y el editor quiere definir, por sección, el ángulo con el que se
seleccionan y redactan las piezas.

Esto no es un detalle de estilo. Afecta a la promesa al lector y a la confianza en
el medio. Se decide, no se improvisa.

## Decisión

1. **El portal declara su línea editorial por sección.** Cada sección tiene un
   enfoque asignado por el editor humano, visible en el portal junto al nombre de
   la sección.

2. **El enfoque se elige de un catálogo cerrado:** `IZQUIERDA`, `DERECHA`,
   `CENTRO`, `NEUTRAL`. Catálogo cerrado y no texto libre para que el sesgo sea
   comparable entre secciones y no se diluya en matices.

3. **El enfoque se guarda con cada artículo.** Cada pieza registra con qué enfoque
   y con qué versión de prompt se escribió. Sin eso no se puede auditar por qué
   un texto dice lo que dice, y sin auditoría esto no es un medio, es un rumor.

4. **La etiqueta es obligatoria.** Si el enfoque no se muestra al lector, la
   función no se implementa. Un sesgo declarado es una línea editorial; uno
   oculto es un engaño.

5. **Los hechos no se tocan.** El enfoque decide qué se elige, cómo se titula y
   desde dónde se explica; nunca autoriza a inventar, omitir un dato que la
   fuente da ni atribuir declaraciones. Se mantiene la prohibición dura del brief
   v1.0: no se publica ningún dato que no esté en la fuente.

## Consecuencias

- El brief v1.0 queda corregido en lo relativo al tono: se puede tener línea
  editorial, siempre declarada y sin inventar hechos. Lo demás (formato de pieza,
  cadencia, trazabilidad, prohibición de inventar) sigue vigente.
- Se necesita un campo `enfoque` por sección y un registro por artículo del
  enfoque aplicado (`article.editorial_bias` o tabla equivalente).
- La portada y la página de sección muestran la etiqueta de enfoque.
- Si mañana se quiere un portal neutral, basta poner todas las secciones en
  `NEUTRAL`: no hay que tocar código.

## Alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| Texto libre por sección | Cada sección acabaría con un sesgo distinto y no comparable; imposible de auditar |
| Enfoque global del portal | El usuario pidió distinguir por sección, y una sola línea para todo el medio impide cubrir temas distintos con criterios distintos |
| No declararlo al lector | Convierte una línea editorial en un sesgo oculto; rompe la confianza y la trazabilidad |
| Enfoque por artículo | Multiplica la configuración y hace imposible que la sección tenga identidad |
