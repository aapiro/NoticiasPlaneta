package com.noticiasplaneta.admin.adapter.in.web;

import com.noticiasplaneta.ai.application.port.in.AiCapabilitiesAdminUseCase;
import com.noticiasplaneta.autopublish.application.port.in.AutoPublishRunUseCase;
import com.noticiasplaneta.ingest.application.port.in.SourceTrustUseCase;
import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * F6 — panel de la publicación automática.
 *
 * <p>Aquí viven los dos interruptores de emergencia: el global (capacidad
 * {@code PUBLISH_AUTO}) y el de cada sección ({@code auto_publica} + cupo diario). Ambos
 * se leen de la base en cada pasada, así que apagarlos surte efecto sin reiniciar.
 *
 * <p>Solo habla con puertos de entrada; el test de arquitectura lo vigila.
 */
@Controller
class AdminAutoPublishController {

    private static final String PUBLISH_AUTO = "PUBLISH_AUTO";

    private final AutoPublishRunUseCase autoPublish;
    private final AiCapabilitiesAdminUseCase capabilities;
    private final SectionAdminUseCase sections;
    private final SourceTrustUseCase sources;

    AdminAutoPublishController(AutoPublishRunUseCase autoPublish,
                               AiCapabilitiesAdminUseCase capabilities,
                               SectionAdminUseCase sections,
                               SourceTrustUseCase sources) {
        this.autoPublish = autoPublish;
        this.capabilities = capabilities;
        this.sections = sections;
        this.sources = sources;
    }

    @GetMapping("/admin/autopublicacion")
    ModelAndView ver() {
        ModelAndView mav = new ModelAndView("admin/autopublicacion");
        addState(mav);
        return mav;
    }

    @PostMapping("/admin/autopublicacion/global")
    ModelAndView interruptor(@RequestParam("activo") boolean activo, Authentication authentication) {
        String who = authentication != null ? authentication.getName() : "desconocido";
        boolean changed = capabilities.setMode(PUBLISH_AUTO, activo ? "EXTERNAL" : "OFF", who);
        return changed
                ? new ModelAndView("redirect:/admin/autopublicacion?ok=global")
                : new ModelAndView("redirect:/admin/autopublicacion?error=global");
    }

    @PostMapping("/admin/autopublicacion/ejecutar")
    ModelAndView ejecutar() {
        AutoPublishRunUseCase.Report report = autoPublish.runNow();
        return new ModelAndView("redirect:/admin/autopublicacion?ok=pasada&estado=" + report.outcome()
                + "&clasificadas=" + report.classified() + "&publicadas=" + report.published());
    }

    @PostMapping("/admin/autopublicacion/fuente")
    ModelAndView fuente(@RequestParam("id") String id,
                        @RequestParam(value = "confianza", required = false) String confianza,
                        Authentication authentication) {
        String who = authentication != null ? authentication.getName() : "desconocido";
        boolean changed = sources.setTrusted(id, confianza != null, who);
        return changed
                ? new ModelAndView("redirect:/admin/autopublicacion?ok=fuente")
                : new ModelAndView("redirect:/admin/autopublicacion?error=fuente");
    }

    @PostMapping("/admin/autopublicacion/limite")
    ModelAndView limite(@RequestParam("slug") String slug,
                        @RequestParam("limite") int limite) {
        boolean changed = sections.setAutoLimite(slug, limite);
        return changed
                ? new ModelAndView("redirect:/admin/autopublicacion?ok=limite")
                : new ModelAndView("redirect:/admin/autopublicacion?error=limite");
    }

    private void addState(ModelAndView mav) {
        boolean globalOn = capabilities.capabilities().stream()
                .anyMatch(c -> PUBLISH_AUTO.equals(c.capability()) && c.on());
        mav.addObject("globalOn", globalOn);
        mav.addObject("ultima", autoPublish.lastRun());
        mav.addObject("secciones", autoPublish.sections());
        mav.addObject("fuentes", sources.sources());
    }
}
