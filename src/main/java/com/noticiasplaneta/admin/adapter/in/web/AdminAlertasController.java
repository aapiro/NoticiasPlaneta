package com.noticiasplaneta.admin.adapter.in.web;

import com.noticiasplaneta.ingest.application.port.in.FuenteAdminUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Avisos de fuente degradada (backlog #7).
 *
 * <p>El agregado ya contaba los fallos y sabía cuándo degradarse, pero el booleano se
 * tiraba: la fuente se rompía y nadie se enteraba. Aquí se ve qué está degradado y qué
 * pasó.
 */
@Controller
class AdminAlertasController {

    private final FuenteAdminUseCase fuentes;

    AdminAlertasController(FuenteAdminUseCase fuentes) {
        this.fuentes = fuentes;
    }

    @GetMapping("/admin/alertas")
    String ver(Model model) {
        var todas = fuentes.fuentes();
        model.addAttribute("degradadas",
                todas.stream().filter(FuenteAdminUseCase.Fuente::degradada).toList());
        model.addAttribute("caidas",
                todas.stream().filter(f -> !f.activa()).toList());
        model.addAttribute("vigiladas", todas.size());
        model.addAttribute("avisos", fuentes.historial(40));
        return "admin/alertas";
    }
}
