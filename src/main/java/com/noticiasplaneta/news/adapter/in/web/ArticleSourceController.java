package com.noticiasplaneta.news.adapter.in.web;

import com.noticiasplaneta.news.application.port.in.ArticleSourcesView;
import com.noticiasplaneta.news.application.port.in.GetArticleSourcesUseCase;
import com.noticiasplaneta.news.application.port.in.LinkSourceCommand;
import com.noticiasplaneta.news.application.port.in.LinkSourceUseCase;
import com.noticiasplaneta.news.application.port.in.RecalculateContrastUseCase;
import com.noticiasplaneta.news.domain.SourceKind;
import com.noticiasplaneta.shared.domain.DomainException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/** F04 — fuentes vinculadas y contraste. */
@RestController
@RequestMapping("/api/v1/articles/{id}/sources")
class ArticleSourceController {

    private final LinkSourceUseCase link;
    private final RecalculateContrastUseCase recalculate;
    private final GetArticleSourcesUseCase get;

    ArticleSourceController(LinkSourceUseCase link, RecalculateContrastUseCase recalculate,
                            GetArticleSourcesUseCase get) {
        this.link = link;
        this.recalculate = recalculate;
        this.get = get;
    }

    record LinkRequest(@NotBlank String url, @NotBlank String kind, Integer weight) {
    }

    @PostMapping
    ResponseEntity<ArticleSourcesView> link(@PathVariable String id,
                                            @Valid @RequestBody LinkRequest request) {
        SourceKind kind;
        try {
            kind = SourceKind.valueOf(request.kind().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("SOURCE_KIND_INVALID",
                    "Tipo de fuente inválido: " + request.kind());
        }

        ArticleSourcesView view = link.link(new LinkSourceCommand(
                id, request.url(), kind, request.weight()));
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @PostMapping("/recalculate")
    ResponseEntity<ArticleSourcesView> recalculate(@PathVariable String id) {
        return ResponseEntity.ok(recalculate.recalculate(id));
    }

    @GetMapping
    ResponseEntity<ArticleSourcesView> list(@PathVariable String id) {
        return ResponseEntity.ok(get.get(id));
    }
}
