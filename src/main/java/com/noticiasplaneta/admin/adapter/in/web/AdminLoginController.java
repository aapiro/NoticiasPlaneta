package com.noticiasplaneta.admin.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class AdminLoginController {

    @GetMapping("/admin/login")
    String login(@RequestParam(name = "error", required = false) String error, Model model) {
        model.addAttribute("error", switch (error == null ? "" : error) {
            case "bad_credentials" -> "Email o contraseña incorrectos.";
            case "bad_totp" -> "El código de verificación no es válido o ha caducado.";
            case "account_disabled" -> "La cuenta está desactivada.";
            case "" -> null;
            default -> "No se ha podido iniciar sesión.";
        });
        return "admin/login";
    }
}
