#!/usr/bin/env bash
# Crea en el kanban de Hermes el plan de Noticias Planeta, colgando del tablero
# "Noticias Planeta" (t_dfca1c8f), ORDENADO POR VALOR FUNCIONAL / ESFUERZO.
#
# Criterio del orden: primero lo que deja resultado visible en horas, despues lo
# que prepara la IA, y al final el panel de control, que es visibilidad y no
# desbloquea nada.
#
#   100  Publicacion por lotes         valor alto,  coste bajo
#    90  Boton "Ejecutar edicion"      valor alto,  coste bajo
#    80  Enfoque aplicado al prompt    valor alto,  coste medio
#    70  Seccion automatizada con IA   valor muy alto, coste alto
#    60  Generador desde un tema       valor muy alto, coste alto
#    50  Panel de estado editorial     valor medio, coste medio
#
#   ./scripts/kanban-fases.sh            crea todo
#   ./scripts/kanban-fases.sh --dry-run  solo muestra lo que haria
set -uo pipefail

BOARD="t_dfca1c8f"
K="hermes kanban --board default"
DRY=0
[ "${1:-}" = "--dry-run" ] && DRY=1

crear() {
    local titulo="$1" prioridad="$2"
    if [ "$DRY" -eq 1 ]; then
        printf '[dry] %3s  %s\n' "$prioridad" "$titulo"
        cat > /dev/null
        return 0
    fi
    local id
    id=$($K create "$titulo" --parent "$BOARD" --assignee dev-v1 --created-by dev-v1 \
             --priority "$prioridad" --body-file - --json 2>&1 \
         | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 \
         | sed 's/.*"\(t_[a-f0-9]*\)"/\1/')
    printf '%-10s %3s  %s\n' "${id:-FALLO}" "$prioridad" "$titulo"
    [ -n "${id:-}" ] && echo "$id" >> /tmp/kanban-fases-ids.txt
    return 0
}

# 1º valor alto, coste bajo: el editor publica 20 de un clic.
crear "1º · Publicación por lotes en el panel (valor alto, coste bajo)" 100 <<'TXT'
Prioridad: PRIMERA. Es el mayor valor por hora invertida: el editor ya puede
publicar, pero de una en una. Con 1889 articulos y 19 publicados, esto es lo que
cambia la portada manana.

Trabajo:
- Casilla por articulo en la bandeja y "seleccionar todo de la pagina".
- POST /admin/articulos/publicar-lote (sesion + CSRF), por la maquina de estados
  existente: nada de saltarse el dominio.
- Resumen: publicados, omitidos y por que.
- audit_log con los ids afectados, no solo un contador.

Aceptacion: 20 borradores -> 20 publicados, la portada los muestra y audit_log
tiene 20 entradas con el mismo actor. Depende de: nada.
TXT

# 2º valor alto, coste bajo: quitar la terminal de encima.
crear "2º · Botón «Ejecutar edición» en el panel (valor alto, coste bajo)" 90 <<'TXT'
Prioridad: SEGUNDA. El script de F1 ya funciona y esta verificado; esto lo pone a
un clic.

Trabajo:
- POST /admin/edicion/ejecutar bajo /admin/** (sesion + CSRF) con confirmacion.
- No se lanza un script con Runtime.exec: se invocan los casos de uso de ingesta y
  publicacion y se guarda el informe. Si hiciera falta proceso externo, acotado con
  timeout y sin shell.
- Resultado en la misma pagina: fuentes, creadas, borradores, publicadas, errores.
- audit_log de quien lo lanzo. Candado para que no se solapen dos ejecuciones.

Aceptacion: con sesion de CHIEF publica y deja rastro; sin sesion 302, sin CSRF 403.
Depende de: F1 (hecho).
TXT

# 3º valor alto, coste medio: es la funcion pedida y prepara la IA.
crear "3º · Enfoque editorial por sección aplicado al prompt (valor alto, coste medio)" 80 <<'TXT'
Prioridad: TERCERA. El campo ya existe y se edita; falta que el modelo lo reciba.
Sin esto, el catalogo de enfoque es un formulario decorativo.

Trabajo:
- Componer el prompt: reglas del brief + enfoque de la seccion + texto de la fuente.
- Guardar en cada articulo con que enfoque y con que version de prompt se escribio
  (article.editorial_bias ya existe; falta rellenarlo).
- Etiqueta visible en el portal: cada seccion declara su enfoque (ADR-012).
- Validacion y audit_log de cada cambio de enfoque.

Aceptacion: cambiar el enfoque de tecnologia cambia el texto de la siguiente pieza y
el articulo guarda con que enfoque se escribio. Depende de: F3 (hecho).
TXT

# 4º valor muy alto, coste alto: el diferencial del producto.
crear "4º · Sección automatizada con IA según su enfoque (valor muy alto, coste alto)" 70 <<'TXT'
Prioridad: CUARTA. Es el diferencial, pero necesita el enfoque (F4) ya cableado.

Trabajo:
- PUBLISH_AUTO solo en las secciones marcadas auto_publica.
- Reglas duras: fuente de confianza, seccion permitida, limite diario, sin repetir
  pieza, ventana 48 h, y que el texto no salga de lo que dice la fuente (ADR-009).
- Clasificacion real por modelo: hoy el ilike manda casi todo a 'general' y
  Tecnologia se queda practicamente vacia. Esto lo arregla.
- Interruptor de emergencia por seccion y global, efectivo sin reiniciar.
- Marca de automatica en el articulo y en audit_log.

Aceptacion: con tecnologia en automatico publica piezas con su angulo y su marca; al
apagar el interruptor no publica mas. Depende de: F1, F4.
TXT

# 5º valor muy alto, coste alto, pero encima de la IA ya montada.
crear "5º · Generador de noticias desde un tema o titular (valor muy alto, coste alto)" 60 <<'TXT'
Prioridad: QUINTA. Reutiliza toda la infraestructura de F4 y F6: por eso va despues.

Trabajo:
- Entrada: tema o titular + seccion. Busqueda en lo ingestado y en fuentes abiertas.
- Recopilacion de fragmentos con medio, URL y fecha.
- Redaccion: solo puede afirmar lo que este en los fragmentos; hereda el enfoque.
- Verificacion: cada parrafo ligado a sus fuentes; un dato sin fuente se elimina.
- Salida: DRAFT marcado como generado con IA, con fuentes y prompt aplicado.
- Menos de 2 fuentes independientes: se aborta y se explica.
- Pasa por BudgetPort. Nunca publica sola salvo PUBLISH_AUTO en esa seccion.

Aceptacion: un tema con cobertura real produce un borrador rastreable; un tema sin
cobertura no inventa y lo dice. Depende de: F1, F4.
TXT

# 6º valor medio: control y visibilidad, cuando ya hay algo que controlar.
crear "6º · Panel de estado editorial y control de la edición (valor medio)" 50 <<'TXT'
Prioridad: SEXTA. Util, pero no desbloquea nada: es visibilidad sobre lo que ya
funciona. Va al final a proposito.

Trabajo:
- /admin/edicion: ultima ejecucion (cuando, quien, fuentes, creadas, publicadas, errores).
- Estado por seccion: activa, automatica o manual, enfoque, piezas hoy, cupo restante.
- Historico de ejecuciones y enlace a la edicion generada.
- Acciones: ejecutar ahora y activar/desactivar automatico por seccion.
- Reutiliza el sistema visual del panel.

Aceptacion: tras dos ejecuciones muestra ambas con sus cifras y el estado coincide
con la BD. Depende de: F2, F5.
TXT

# ---------------------------------------------------------------- hechas
# No se crean: el tablero ya tiene sus equivalentes completadas
# (t_fc4cc833 agregador, t_8fee767c plantilla, t_2bc900e6 automatizacion).
# Crearlas otra vez seria ruido.

echo
echo "Orden: 100 lotes -> 90 boton -> 80 enfoque -> 70 IA -> 60 generador -> 50 panel"
echo "Ids en /tmp/kanban-fases-ids.txt"
