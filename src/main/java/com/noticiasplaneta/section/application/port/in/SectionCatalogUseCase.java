package com.noticiasplaneta.section.application.port.in;

import java.util.List;
import java.util.Optional;

/**
 * Lectura pública de secciones (backlog #3).
 *
 * <p>Solo las activas. El portal necesita el nombre y, sobre todo, el enfoque
 * declarado que se muestra al lector (ADR-012).
 */
public interface SectionCatalogUseCase {

    List<SectionAdminUseCase.SectionView> activas();

    Optional<SectionAdminUseCase.SectionView> porSlug(String slug);
}
