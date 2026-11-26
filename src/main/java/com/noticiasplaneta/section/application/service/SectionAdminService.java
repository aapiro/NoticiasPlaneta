package com.noticiasplaneta.section.application.service;

import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import com.noticiasplaneta.section.application.port.out.SectionConfigRepository;
import com.noticiasplaneta.section.domain.EditorialBias;
import com.noticiasplaneta.section.domain.SectionConfig;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
class SectionAdminService implements SectionAdminUseCase {

    private final SectionConfigRepository sections;

    SectionAdminService(SectionConfigRepository sections) {
        this.sections = sections;
    }

    @Override
    public List<SectionView> sections() {
        return sections.findAll().stream()
                .map(s -> new SectionView(s.slug(), s.nombre(), s.descripcion(),
                        s.activa(), s.orden(), s.maxPiezas(),
                        s.enfoque().name(), s.autoPublica()))
                .toList();
    }

    @Override
    public List<String> biases() {
        return Arrays.stream(EditorialBias.values()).map(Enum::name).toList();
    }

    @Override
    public boolean setActive(String slug, boolean activa) {
        return mutate(slug, s -> new SectionConfig(s.slug(), s.nombre(), s.descripcion(),
                activa, s.orden(), s.maxPiezas(), s.enfoque(), s.autoPublica()));
    }

    @Override
    public boolean setBias(String slug, String bias) {
        return mutate(slug, s -> new SectionConfig(s.slug(), s.nombre(), s.descripcion(),
                s.activa(), s.orden(), s.maxPiezas(), EditorialBias.parse(bias), s.autoPublica()));
    }

    @Override
    public boolean setAutoPublica(String slug, boolean autoPublica) {
        return mutate(slug, s -> new SectionConfig(s.slug(), s.nombre(), s.descripcion(),
                s.activa(), s.orden(), s.maxPiezas(), s.enfoque(), autoPublica));
    }

    @Override
    public boolean update(String slug, boolean activa, String bias, boolean autoPublica) {
        return mutate(slug, s -> new SectionConfig(s.slug(), s.nombre(), s.descripcion(),
                activa, s.orden(), s.maxPiezas(), EditorialBias.parse(bias), autoPublica));
    }

    private boolean mutate(String slug, java.util.function.UnaryOperator<SectionConfig> change) {
        return sections.findBySlug(slug)
                .map(actual -> {
                    sections.save(change.apply(actual));
                    return true;
                })
                .orElse(false);
    }
}
