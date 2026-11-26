# ADR-017 · Presupuesto de IA: día natural de Madrid y coste estimado por operación

- **Estado:** aceptado e implementado (26/11/2026)
- **Contexto:** F03 (enriquecido asíncrono), F6 (clasificación y publicación automática),
  ADR-006 (módulo IA) y ADR-009 (datos y proveedor LLM). El número 016 queda reservado
  para el generador desde tema (F8, en curso).

## Problema

Dos defectos que se tapaban entre sí:

1. **El "presupuesto diario" no era diario.** `InMemoryBudgetAdapter` sumaba en un
   `AtomicLong` que solo volvía a cero al reiniciar el proceso. Con
   `AI_DAILY_BUDGET_MICROS=5.000.000` y la estimación de 20.000 micros por clasificación,
   la publicación automática dejaba de clasificar tras ~250 piezas **hasta el siguiente
   reinicio**, no hasta el día siguiente.
2. **Las estimaciones no salían de ningún cálculo.** 20.000 micros por clasificación y
   50.000 por enriquecido son 130 y 17 veces el coste real medido. Un presupuesto que
   frena a las 250 piezas cuando el dinero que protege daría para decenas de miles no
   protege: estorba.

## Decisión

### 1. El día se guarda junto al gasto

- El adaptador guarda un registro inmutable `(día, microdólares)` y lo sustituye con CAS.
  Al consumir, si el día del registro no es el de hoy, se parte de cero.
- El día es el **civil de `ai.budget.zone`** (Europe/Madrid por defecto, configurable con
  `AI_BUDGET_ZONE`), no el día UTC ni 24 h desde el arranque:
  `LocalDate.ofInstant(clock.instant(), zone)`. El `Clock` se inyecta (el bean
  `Clock.systemUTC()` que ya usa el resto del monolito), así el cálculo es Madrid aunque
  el reloj sea UTC.
- Un intento que no cabe no descuenta nada; un coste negativo es un error
  (`IllegalArgumentException`), no un ingreso. El techo se respeta exacto en concurrencia
  porque la comparación y la suma ocurren dentro del CAS.
- Sigue siendo **memoria y por instancia**: con varias instancias cada una lleva su
  contador. El puerto `BudgetPort.tryConsume(long)` no cambia; si algún día se escala, el
  adaptador pasa a PostgreSQL sin tocar a los servicios.
- Un salto de reloj hacia atrás (corrección NTP) también reinicia. Es aceptable: esto es
  una red de seguridad contra bucles de gasto, no contabilidad.

### 2. Estimaciones medidas, no inventadas

Precios oficiales de DeepSeek (`api-docs.deepseek.com/quick_start/pricing`, consultado el
26/11/2026), `deepseek-flash`, USD por 1M de tokens:

| | Hora punta | Hora valle |
|---|---|---|
| Entrada sin caché | $0,30 | $0,15 |
| Entrada con caché | $0,006 | $0,003 |
| Salida | $1,20 | $0,60 |

`deepseek-chat` —el modelo del clasificador— responde hoy como `deepseek-flash`: la
respuesta real trae `"model":"deepseek-flash"`, así que se aplica esta tarifa.

Medición con la clave real y el payload exacto que construye el código (prompts extraídos
de los `.java`, no reescritos), hora punta y sin caché para quedarse con el peor caso:

| Operación | Entrada | Salida | Coste medido | Estimación en el código |
|---|---|---|---|---|
| Clasificar un titular de 90 caracteres | 184 tok | 17 tok | 75,6 µ$ | **150 µ$** |
| Enriquecer un cuerpo de 7.076 caracteres (p90 real) | 1.902 tok | 794–803 tok (631 de razonamiento) | 1.534 µ$ | **4.000 µ$** |
| Enriquecer un cuerpo de 26.241 caracteres (p99 real) | 6.548 tok | 866–1.234 tok (715–1.030 de razonamiento) | 2.918–3.450 µ$ | **4.000 µ$** |

- El **razonamiento** (thinking es el modo por defecto de flash) se factura como salida y
  supone el 60–80 % de los tokens de salida del enriquecido. Por eso la estimación cubre
  el p99 y no la media: la variabilidad de esos tokens es lo que puede romper el techo.
- Con caché caliente el coste cae mucho: en la segunda medición, 1.664 de 1.902 tokens de
  entrada y 6.400 de 6.548 vinieron de caché.
- El cuerpo del enriquecido **no se trunca**. Distribución real de la base (3.294
  artículos): p50 174, p90 7.076, p99 26.241 y máximo 70.588 caracteres. Un artículo del
  máximo ronda los 6.300 µ$ y se cobra por debajo de su coste: **el presupuesto es una red
  de seguridad, no una factura**. Para una cota exacta habría que reconciliar con el
  `usage` que ya devuelve el proveedor; queda fuera de esta tarjeta.
- Con el presupuesto por defecto (5.000.000 µ$ = $5/día) caben ~33.000 clasificaciones o
  ~1.250 enriquecidos al día, en vez de 250 clasificaciones para toda la vida del proceso.

### 3. Lo que no cambia

- `BudgetPort` sigue con un único método: los servicios no saben de días, zonas ni precios.
- Nada de base de datos. El defecto era el reinicio, no la persistencia.
- El adaptador de clasificación tenía un `COST_MICROS = 500` privado y sin usar que
  contradecía a la constante real; se elimina para que solo haya un número.

## Consecuencias

- Un proceso con días de vida vuelve a tener presupuesto cada mañana sin reiniciar.
- Cambiar `AI_DAILY_BUDGET_MICROS` ajusta el techo sin tocar código ni reiniciar (se lee al
  arrancar; el valor no cambia en caliente).
- Si el endpoint apunta a un modelo autoalojado, el presupuesto deja de representar dinero
  (coste cero) y solo limita **volumen**. Es el comportamiento esperado.
- La estimación es fija por operación: no distingue un cuerpo de 300 caracteres de uno de
  26.000. Es deliberado (una constante por camino, fácil de auditar); si el coste se
  desvía, se ajusta la constante con una medición nueva y se anota aquí.

## Cómo se verifica

```bash
./mvnw -o test -Dtest=InMemoryBudgetAdapterTest
```

6 tests. Los dos que importan:

- `alCambiarElDiaElContadorVuelveACero` — aceptación: se agota el día 1, se avanza el
  reloj al 27/11 00:00:01 de Madrid y el mismo presupuesto vuelve a estar entero; el techo
  del día 2 se respeta igual.
- `elDiaEsElDeMadridNoElDeUtc` — 23:30 UTC del 26 son las 00:30 del 27 en Madrid (día
  nuevo); las 22:00 UTC del 27 son las 23:00 en Madrid (mismo día, sigue agotado).

Contra la implementación anterior fallan 3 de los 6 (comprobado), así que el test muerde.
