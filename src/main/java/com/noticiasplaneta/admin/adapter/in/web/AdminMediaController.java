package com.noticiasplaneta.admin.adapter.in.web;

import com.noticiasplaneta.media.application.port.in.CachePendingImagesUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Imágenes propias (backlog #2).
 *
 * <p>Muestra cuántas noticias siguen apuntando al medio de origen y permite
 * cachearlas por tandas. Bajo la cadena de {@code /admin/**}: sesión y CSRF.
 */
@Controller
class AdminMediaController {

    private static final int TANDA = 20;

    private final CachePendingImagesUseCase cache;

    AdminMediaController(CachePendingImagesUseCase cache) {
        this.cache = cache;
    }

    @GetMapping("/admin/media")
    String ver(Model model) {
        model.addAttribute("pendientes", cache.pendientes());
        return "admin/media";
    }

    @PostMapping("/admin/media/cachear")
    String cachear(@RequestParam(value = "limite", defaultValue = "" + TANDA) int limite) {
        var reporte = cache.cachear(limite);
        return "redirect:/admin/media?ok=" + reporte.cacheadas()
                + "&fallidas=" + reporte.fallidas()
                + "&quedan=" + reporte.pendientesDespues();
    }
}
