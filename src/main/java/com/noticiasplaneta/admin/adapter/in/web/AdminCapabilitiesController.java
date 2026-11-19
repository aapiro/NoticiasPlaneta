package com.noticiasplaneta.admin.adapter.in.web;

import com.noticiasplaneta.ai.application.port.in.AiCapabilitiesAdminUseCase;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Panel de capacidades de IA (ADR-011, F1).
 *
 * <p>Encender y apagar sin reiniciar. Va bajo la cadena de {@code /admin/**}: sesión,
 * CSRF y login obligatorios. Solo habla con el módulo {@code ai} por su puerto de
 * entrada, como exige el test de arquitectura.
 */
@Controller
class AdminCapabilitiesController {

    private final AiCapabilitiesAdminUseCase capabilities;

    AdminCapabilitiesController(AiCapabilitiesAdminUseCase capabilities) {
        this.capabilities = capabilities;
    }

    @GetMapping("/admin/capacidades")
    String ver(Model model) {
        model.addAttribute("capacidades", capabilities.capabilities());
        model.addAttribute("modos", capabilities.modes());
        return "admin/capacidades";
    }

    @PostMapping("/admin/capacidades")
    String cambiar(@RequestParam("capability") String capability,
                   @RequestParam("mode") String mode,
                   Authentication authentication) {

        String who = authentication != null ? authentication.getName() : "desconocido";
        boolean changed = capabilities.setMode(capability, mode, who);

        return changed
                ? "redirect:/admin/capacidades?ok=" + capability
                : "redirect:/admin/capacidades?error=modo";
    }
}
