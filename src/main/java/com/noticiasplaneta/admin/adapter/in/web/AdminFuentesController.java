package com.noticiasplaneta.admin.adapter.in.web;

import com.noticiasplaneta.ingest.application.port.in.FuenteAdminUseCase;
import com.noticiasplaneta.shared.domain.DomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Fuentes de ingesta (backlog #4).
 *
 * <p>Alta, activación y desactivación sin tocar SQL. Bajo {@code /admin/**}: sesión y CSRF.
 */
@Controller
class AdminFuentesController {

    private final FuenteAdminUseCase fuentes;

    AdminFuentesController(FuenteAdminUseCase fuentes) {
        this.fuentes = fuentes;
    }

    @GetMapping("/admin/fuentes")
    String ver(Model model) {
        model.addAttribute("fuentes", fuentes.fuentes());
        return "admin/fuentes";
    }

    @PostMapping("/admin/fuentes")
    String crear(@RequestParam("nombre") String nombre,
                 @RequestParam("feedUrl") String feedUrl,
                 RedirectAttributes flash) {
        try {
            fuentes.crear(nombre, feedUrl);
            return "redirect:/admin/fuentes?ok=creada";
        } catch (DomainException e) {
            // El dominio ya dice qué falta; se muestra tal cual en vez de inventar otro texto.
            return "redirect:/admin/fuentes?error=" + e.code();
        } catch (DataIntegrityViolationException e) {
            return "redirect:/admin/fuentes?error=URL_DUPLICADA";
        }
    }

    @PostMapping("/admin/fuentes/{id}/estado")
    String estado(@PathVariable String id,
                  @RequestParam("accion") String accion,
                  RedirectAttributes flash) {
        boolean ok = "activar".equals(accion) ? fuentes.activar(id) : fuentes.desactivar(id);
        return ok
                ? "redirect:/admin/fuentes?ok=" + accion
                : "redirect:/admin/fuentes?error=NO_EXISTE";
    }
}
