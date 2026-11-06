package com.noticiasplaneta.web.adapter.in;

import com.noticiasplaneta.taxonomy.application.port.in.GetTopicTimelineUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Página de tema. El "desde tu última visita" llega como fecha ISO en la query: el cliente
 * la guarda en localStorage y aquí no se pone ni una cookie (RN2 de F11).
 */
@Controller
class TopicPageController {

    private final GetTopicTimelineUseCase topics;

    TopicPageController(GetTopicTimelineUseCase topics) {
        this.topics = topics;
    }

    @GetMapping("/tema/{slug}")
    String tema(@PathVariable String slug,
                @RequestParam(name = "desde", required = false) String desde,
                Model model) {

        Instant since = parse(desde);
        var timeline = topics.bySlug(slug, since).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tema no encontrado"));

        model.addAttribute("tema", timeline);
        return "tema";
    }

    private Instant parse(String desde) {
        if (desde == null || desde.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(desde);
        } catch (DateTimeParseException e) {
            return null;   // una fecha corrupta se trata como primera visita (E02)
        }
    }
}
