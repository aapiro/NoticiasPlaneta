package com.noticiasplaneta.section.application.port.out;

import com.noticiasplaneta.section.domain.SectionConfig;

import java.util.List;
import java.util.Optional;

public interface SectionConfigRepository {

    /** Todas las secciones, ordenadas por {@code orden}. */
    List<SectionConfig> findAll();

    /** Solo las activas, para la navegación del portal. */
    List<SectionConfig> findActivas();

    Optional<SectionConfig> findBySlug(String slug);

    void save(SectionConfig config);
}
