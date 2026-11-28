package com.noticiasplaneta.admin.adapter.in.web;

import com.noticiasplaneta.identity.application.port.in.AccountAdminUseCase;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Gestión de cuentas del backoffice.
 *
 * <p>La raíz —rol {@code ADMIN}— ve y apaga todas las demás cuentas. Un
 * {@code AGENT} ni siquiera entra aquí: la cadena de seguridad le devuelve 403.
 */
@Controller
class AdminCuentasController {

    private final AccountAdminUseCase cuentas;

    AdminCuentasController(AccountAdminUseCase cuentas) {
        this.cuentas = cuentas;
    }

    @GetMapping("/admin/cuentas")
    String listado(Authentication auth, Model model,
                   @RequestParam(required = false) String ok,
                   @RequestParam(required = false) String error) {
        model.addAttribute("cuentas", cuentas.cuentas(auth.getName()));
        model.addAttribute("ok", ok);
        model.addAttribute("error", error);
        return "admin/cuentas";
    }

    @PostMapping("/admin/cuentas/{id}/estado")
    String cambiar(Authentication auth, @PathVariable String id,
                   @RequestParam(defaultValue = "true") boolean activar) {
        var resultado = cuentas.cambiarEstado(auth.getName(), parsear(id), activar);
        return switch (resultado) {
            case HECHO -> "redirect:/admin/cuentas?ok=" + (activar ? "activada" : "desactivada");
            case NO_EXISTE -> "redirect:/admin/cuentas?error=no-existe";
            case ES_LA_RAIZ -> "redirect:/admin/cuentas?error=es-la-raiz";
            case SIN_PERMISO -> "redirect:/admin/cuentas?error=sin-permiso";
        };
    }

    /** Un id mal formado no puede tumbar la petición con un 500. */
    private UUID parsear(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return new UUID(0, 0);
        }
    }
}
