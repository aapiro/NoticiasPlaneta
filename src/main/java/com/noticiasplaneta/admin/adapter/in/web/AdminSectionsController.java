package com.noticiasplaneta.admin.adapter.in.web;

import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Secciones y enfoque editorial (F3 + ADR-012).
 *
 * <p>Bajo la cadena de {@code /admin/**}: sesión y CSRF obligatorios. Solo habla con
 * el módulo {@code section} por su puerto de entrada.
 */
@Controller
class AdminSectionsController {

    private final SectionAdminUseCase sections;

    AdminSectionsController(SectionAdminUseCase sections) {
        this.sections = sections;
    }

    @GetMapping("/admin/secciones")
    String ver(Model model) {
        model.addAttribute("secciones", sections.sections());
        model.addAttribute("enfoques", sections.biases());
        return "admin/secciones";
    }

    /**
     * Los checkboxes no viajan si están desmarcados: su ausencia es el valor false.
     * Por eso {@code required = false} y se interpreta null como "no".
     */
    @PostMapping("/admin/secciones")
    String guardar(@RequestParam("slug") String slug,
                   @RequestParam(value = "activa", required = false) String activa,
                   @RequestParam(value = "enfoque", defaultValue = "NEUTRAL") String enfoque,
                   @RequestParam(value = "auto", required = false) String auto) {

        boolean changed = sections.update(slug, activa != null, enfoque, auto != null);
        return changed
                ? "redirect:/admin/secciones?ok=" + slug
                : "redirect:/admin/secciones?error=seccion";
    }
}
