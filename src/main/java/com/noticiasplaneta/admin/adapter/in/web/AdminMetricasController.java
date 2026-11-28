package com.noticiasplaneta.admin.adapter.in.web;

import com.noticiasplaneta.ingest.application.port.in.IngestMetricsUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Métricas de ingesta (backlog #10). Bajo /admin: Basic + sesión + TOTP. */
@Controller
class AdminMetricasController {

    private final IngestMetricsUseCase metricas;

    AdminMetricasController(IngestMetricsUseCase metricas) {
        this.metricas = metricas;
    }

    @GetMapping("/admin/metricas")
    String ver(@RequestParam(name = "dias", defaultValue = "14") int dias, Model model) {
        var informe = metricas.informe(dias);
        model.addAttribute("informe", informe);
        model.addAttribute("dias", dias);
        return "admin/metricas";
    }
}
