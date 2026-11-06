package com.noticiasplaneta.news.adapter.in.web;

import com.noticiasplaneta.news.application.port.in.CreateDraftCommand;
import com.noticiasplaneta.news.application.port.in.CreateDraftFromItemUseCase;
import com.noticiasplaneta.news.application.port.in.CreateDraftResult;
import com.noticiasplaneta.news.application.port.in.TransitionArticleUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionCommand;
import com.noticiasplaneta.news.application.port.in.UnpublishArticleUseCase;
import com.noticiasplaneta.news.application.port.in.UnpublishCommand;
import com.noticiasplaneta.news.domain.UnpublishKind;
import com.noticiasplaneta.news.domain.Actor;
import com.noticiasplaneta.news.domain.ActorType;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.ArticleOrigin;
import com.noticiasplaneta.news.domain.ArticleState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/articles")
class ArticleController {

    private final CreateDraftFromItemUseCase createDraft;
    private final TransitionArticleUseCase transition;
    private final UnpublishArticleUseCase unpublish;

    ArticleController(CreateDraftFromItemUseCase createDraft, TransitionArticleUseCase transition,
                      UnpublishArticleUseCase unpublish) {
        this.createDraft = createDraft;
        this.transition = transition;
        this.unpublish = unpublish;
    }

    record NewDraftRequest(
            String ingestItemId,
            String language,
            @NotBlank String sectionId,
            @NotBlank String title,
            String body,
            String sourceUrl,
            String origin) {
    }

    record DraftResponse(String id, String slug, String state, boolean created) {
    }

    record TransitionRequest(
            @NotBlank String target,
            @NotBlank String actorId,
            @NotBlank String actorType,
            Set<String> roles,
            Set<String> sections,
            String reason) {
    }

    record UnpublishRequest(@NotBlank String kind, @NotBlank String reason,
                            @NotBlank String actorId, @NotBlank String actorType,
                            Set<String> roles, Set<String> sections) {
    }

    record ArticleResponse(String id, String slug, String state, String publishedAt,
                           String correctionNote) {
        static ArticleResponse of(Article a) {
            return new ArticleResponse(a.id().toString(), a.slug().value(), a.state().name(),
                    a.publishedAt() == null ? null : a.publishedAt().toString(),
                    a.correctionNote());
        }
    }

    @PostMapping
    ResponseEntity<DraftResponse> create(@Valid @RequestBody NewDraftRequest request) {
        ArticleOrigin origin = request.origin() == null
                ? ArticleOrigin.MANUAL
                : ArticleOrigin.valueOf(request.origin().toUpperCase());

        CreateDraftResult result = createDraft.create(new CreateDraftCommand(
                request.ingestItemId(),
                request.language() == null ? "es" : request.language(),
                request.sectionId(),
                request.title(),
                request.body(),
                request.sourceUrl(),
                origin));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DraftResponse(result.articleId().toString(), result.slug().value(),
                        "DRAFT", result.created()));
    }

    @PostMapping("/{id}/unpublish")
    ResponseEntity<ArticleResponse> unpublish(@PathVariable String id,
                                              @Valid @RequestBody UnpublishRequest request) {
        Actor actor = new Actor(
                request.actorId(),
                ActorType.valueOf(request.actorType().toUpperCase()),
                request.roles() == null ? Set.of() : request.roles(),
                request.sections() == null ? Set.of() : request.sections());

        Article article = unpublish.unpublish(new UnpublishCommand(
                ArticleId.of(id),
                UnpublishKind.valueOf(request.kind().toUpperCase()),
                request.reason(),
                actor));

        return ResponseEntity.ok(ArticleResponse.of(article));
    }

    @PostMapping("/{id}/transitions")
    ResponseEntity<ArticleResponse> transition(@PathVariable String id,
                                               @Valid @RequestBody TransitionRequest request) {
        Actor actor = new Actor(
                request.actorId(),
                ActorType.valueOf(request.actorType().toUpperCase()),
                request.roles() == null ? Set.of() : request.roles(),
                request.sections() == null ? Set.of() : request.sections());

        Article article = transition.transition(new TransitionCommand(
                ArticleId.of(id),
                ArticleState.valueOf(request.target().toUpperCase()),
                actor,
                request.reason()));

        return ResponseEntity.ok(ArticleResponse.of(article));
    }
}
