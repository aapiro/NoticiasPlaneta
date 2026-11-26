package com.noticiasplaneta.section.domain;

/**
 * Configuración de una sección informativa.
 *
 * @param slug      identificador canónico; es el {@code section_id} de los artículos
 * @param nombre    nombre visible
 * @param activa    si es false no aparece en la navegación ni recibe piezas nuevas
 * @param orden     orden en la navegación (menor primero)
 * @param maxPiezas cupo máximo de piezas por edición
 * @param enfoque   línea editorial declarada, visible en el portal (ADR-012)
 * @param autoPublica si puede publicar sin intervención humana (F6)
 * @param autoLimiteDiario cupo diario de la publicación automática (F6); 0 = ninguna
 */
public record SectionConfig(
        String slug,
        String nombre,
        String descripcion,
        boolean activa,
        int orden,
        int maxPiezas,
        EditorialBias enfoque,
        boolean autoPublica,
        int autoLimiteDiario) {
}
