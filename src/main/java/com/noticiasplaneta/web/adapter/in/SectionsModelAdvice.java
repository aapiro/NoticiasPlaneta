package com.noticiasplaneta.web.adapter.in;

import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import com.noticiasplaneta.section.application.port.in.SectionCatalogUseCase;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * Navegación del portal desde {@code section_config} (ADR-012, decisión 4.a).
 *
 * <p>Antes estaba escrita a mano en la plantilla: desactivar una sección en el panel
 * no la quitaba del menú. Ahora el menú es lo que el editor tenga activo.
 */
@ControllerAdvice(basePackages = "com.noticiasplaneta.web.adapter.in")
class SectionsModelAdvice {

    private final SectionCatalogUseCase secciones;

    SectionsModelAdvice(SectionCatalogUseCase secciones) {
        this.secciones = secciones;
    }

    @ModelAttribute("seccionesNav")
    List<SectionAdminUseCase.SectionView> seccionesNav() {
        return secciones.activas();
    }
}
