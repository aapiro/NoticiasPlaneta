package com.noticiasplaneta.generation.adapter.in.web;

import com.noticiasplaneta.generation.application.port.in.GenerarBorradorCommand;
import com.noticiasplaneta.generation.application.port.in.GenerarBorradorUseCase;
import com.noticiasplaneta.generation.application.port.in.ResultadoGeneracion;
import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import com.noticiasplaneta.shared.domain.DomainException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * F8 — generador de borradores desde el panel.
 *
 * <p>Bajo la cadena de {@code /admin/**}: sesión y CSRF obligatorios. El editor escribe
 * el tema, elige sección y recibe el informe: pieza creada con sus fuentes, o el motivo
 * por el que no hay pieza. La publicación automática solo ocurre si la sección la tiene
 * activada y la capacidad encendida; aquí se pasa el permiso de un humano presente.
 */
@Controller
class AdminGeneradorController {

    private final GenerarBorradorUseCase generador;
    private final SectionAdminUseCase secciones;

    AdminGeneradorController(GenerarBorradorUseCase generador, SectionAdminUseCase secciones) {
        this.generador = generador;
        this.secciones = secciones;
    }

    @GetMapping("/admin/generador")
    String ver(Model model) {
        model.addAttribute("secciones", secciones.sections());
        return "admin/generador";
    }

    @PostMapping("/admin/generador")
    String generar(@RequestParam("tema") String tema,
                   @RequestParam("seccion") String seccion,
                   Authentication authentication,
                   Model model) {
        model.addAttribute("tema", tema);
        model.addAttribute("seccionElegida", seccion);
        model.addAttribute("secciones", secciones.sections());
        try {
            ResultadoGeneracion resultado = generador.generar(
                    GenerarBorradorCommand.humano(tema, seccion, authentication.getName()));
            model.addAttribute("resultado", resultado);
        } catch (DomainException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "admin/generador";
    }
}
