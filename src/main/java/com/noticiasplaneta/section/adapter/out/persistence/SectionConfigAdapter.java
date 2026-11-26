package com.noticiasplaneta.section.adapter.out.persistence;

import com.noticiasplaneta.section.application.port.out.SectionConfigRepository;
import com.noticiasplaneta.section.domain.EditorialBias;
import com.noticiasplaneta.section.domain.SectionConfig;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
class SectionConfigAdapter implements SectionConfigRepository {

    private final SpringDataSectionConfigRepository jpa;

    SectionConfigAdapter(SpringDataSectionConfigRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SectionConfig> findAll() {
        return jpa.findAllByOrderByOrdenAsc().stream().map(SectionConfigAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SectionConfig> findActivas() {
        return jpa.findByActivaTrueOrderByOrdenAsc().stream().map(SectionConfigAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SectionConfig> findBySlug(String slug) {
        if (slug == null) return Optional.empty();
        return jpa.findById(slug).map(SectionConfigAdapter::toDomain);
    }

    @Override
    @Transactional
    public void save(SectionConfig config) {
        SectionConfigJpaEntity entity = jpa.findById(config.slug()).orElseGet(SectionConfigJpaEntity::new);
        entity.slug = config.slug();
        entity.nombre = config.nombre();
        entity.descripcion = config.descripcion();
        entity.activa = config.activa();
        entity.orden = config.orden();
        entity.maxPiezas = config.maxPiezas();
        entity.enfoque = config.enfoque().name();
        entity.autoPublica = config.autoPublica();
        entity.autoLimiteDiario = config.autoLimiteDiario();
        entity.actualizadaEn = Instant.now();
        jpa.save(entity);
    }

    private static SectionConfig toDomain(SectionConfigJpaEntity e) {
        return new SectionConfig(
                e.slug, e.nombre, e.descripcion, e.activa, e.orden, e.maxPiezas,
                EditorialBias.parse(e.enfoque), e.autoPublica, e.autoLimiteDiario);
    }
}
