package com.noticiasplaneta.section.application.port.in;

import java.util.List;

/**
 * Gestión de secciones desde el panel (F3 + ADR-012).
 *
 * <p>El panel solo habla por este puerto, con tipos propios: no ve el dominio ni la
 * persistencia del módulo {@code section}.
 */
public interface SectionAdminUseCase {

    List<SectionView> sections();

    /** Enfoques disponibles para el desplegable (catálogo cerrado, ADR-012). */
    List<String> biases();

    boolean setActive(String slug, boolean activa);

    boolean setBias(String slug, String bias);

    boolean setAutoPublica(String slug, boolean autoPublica);

    /** Cupo diario de la automatización (F6). 0 = esa sección no publica sola. */
    boolean setAutoLimite(String slug, int limiteDiario);

    /** Guarda de una vez los tres campos editables de la fila del panel. */
    boolean update(String slug, boolean activa, String bias, boolean autoPublica);

    record SectionView(String slug, String nombre, String descripcion,
                       boolean activa, int orden, int maxPiezas,
                       String enfoque, boolean autoPublica, int autoLimiteDiario) {
    }
}
