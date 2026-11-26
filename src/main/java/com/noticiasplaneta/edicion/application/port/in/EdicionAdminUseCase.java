package com.noticiasplaneta.edicion.application.port.in;

/**
 * Puerta de entrada del panel de edición (F7).
 *
 * <p>El controlador solo ve estos tipos: ni tablas, ni el módulo {@code news}, ni el de
 * {@code section}. El estado por sección se compone aquí cruzando la configuración de
 * sección con las publicaciones de hoy.
 */
public interface EdicionAdminUseCase {

    TableroEdicionView tablero();

    EjecucionDetalleView detalle(String ejecucionId);

    /**
     * Lanza una edición completa: ingesta, borradores desde lo nuevo, selección por cupo y
     * publicación por la máquina de estados.
     *
     * @param actorId quién lo pidió (queda en la ejecución y en {@code audit_log})
     * @param actor   actor de dominio con sus roles
     * @param modo    «publicar» o «borrador»
     * @return el informe de la ejecución recién cerrada
     */
    EjecucionView ejecutar(String actorId, com.noticiasplaneta.shared.domain.Actor actor, String modo);

    /** Edición en markdown de una ejecución, para descargar (misma forma que F1). */
    String edicionMarkdown(String ejecucionId);
}
