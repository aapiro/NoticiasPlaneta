package com.noticiasplaneta.admin.adapter.in.web;

import com.noticiasplaneta.edicion.application.port.in.EdicionAdminUseCase;
import com.noticiasplaneta.edicion.application.port.in.EjecucionView;
import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import com.noticiasplaneta.shared.domain.Actor;
import com.noticiasplaneta.shared.domain.DomainException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * F7 · Panel de estado editorial y control de la edición.
 *
 * <p>Bajo la cadena de {@code /admin/**}: sesión y CSRF obligatorios. Habla con el módulo
 * {@code edicion} por su puerto de entrada y con {@code section} para el interruptor de
 * automática; no toca adaptadores ni tablas.
 *
 * <p>Ejecutar una edición publica de verdad, así que se exige CHIEF o ADMIN además de sesión.
 * Un AUTHOR que pulse el botón no se lleva un error de dominio a medio camino: se le dice que
 * no puede.
 */
@Controller
class AdminEdicionController {

    private final EdicionAdminUseCase edicion;
    private final SectionAdminUseCase secciones;

    AdminEdicionController(EdicionAdminUseCase edicion, SectionAdminUseCase secciones) {
        this.edicion = edicion;
        this.secciones = secciones;
    }

    @GetMapping("/admin/edicion")
    String ver(Model model, Authentication authentication) {
        model.addAttribute("tablero", edicion.tablero());
        model.addAttribute("puedeEjecutar", puedePublicar(authentication));
        return "admin/edicion";
    }

    @PostMapping("/admin/edicion/ejecutar")
    String ejecutar(@RequestParam(value = "modo", defaultValue = "publicar") String modo,
                    @RequestParam(value = "confirmo", required = false) String confirmo,
                    Authentication authentication) {

        if (!puedePublicar(authentication)) {
            return "redirect:/admin/edicion?error=permiso";
        }
        if (confirmo == null) {
            return "redirect:/admin/edicion?error=confirmar";
        }

        Actor actor = AdminActor.from(authentication);
        try {
            EjecucionView ejecucion = edicion.ejecutar(authentication.getName(), actor, modo);
            return "redirect:/admin/edicion/" + ejecucion.id();
        } catch (DomainException e) {
            String motivo = "EDICION_EN_CURSO".equals(e.code()) ? "en_curso" : "ejecucion";
            return "redirect:/admin/edicion?error=" + motivo;
        }
    }

    /**
     * Interruptor de automática por sección. El checkbox desmarcado no viaja: su ausencia
     * significa «manual».
     */
    @PostMapping("/admin/edicion/automatico")
    String automatico(@RequestParam("slug") String slug,
                      @RequestParam(value = "auto", required = false) String auto) {
        boolean changed = secciones.setAutoPublica(slug, auto != null);
        return changed
                ? "redirect:/admin/edicion?ok=auto"
                : "redirect:/admin/edicion?error=seccion";
    }

    @GetMapping("/admin/edicion/{id}")
    String detalle(@PathVariable String id, Model model) {
        model.addAttribute("detalle", edicion.detalle(id));
        return "admin/edicion-detalle";
    }

    /** La edición en markdown, igual que la escribe F1, para guardarla o pasarla al canal. */
    @GetMapping(value = "/admin/edicion/{id}/edicion.md", produces = "text/markdown;charset=UTF-8")
    ResponseEntity<String> markdown(@PathVariable String id) {
        String corto = id.length() > 8 ? id.substring(0, 8) : id;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"edicion-" + corto + ".md\"")
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .body(edicion.edicionMarkdown(id));
    }

    private static boolean puedePublicar(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(Object::toString)
                .anyMatch(a -> a.equals("ROLE_CHIEF") || a.equals("ROLE_ADMIN"));
    }
}
