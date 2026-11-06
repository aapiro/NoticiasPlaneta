package com.noticiasplaneta.media.application.service;

import com.noticiasplaneta.media.application.port.in.ArticleMediaView;
import com.noticiasplaneta.media.application.port.in.AttachMediaCommand;
import com.noticiasplaneta.media.application.port.in.AttachMediaUseCase;
import com.noticiasplaneta.media.application.port.out.ArticleMediaRepository;
import com.noticiasplaneta.shared.domain.DomainException;

import java.util.List;
import java.util.Set;

/**
 * Coloca medios en un artículo.
 *
 * <p>Regla de la casa: una imagen publicada sin texto alternativo es una imagen que no
 * existe para quien no ve. El alt no se puede saltar (viene exigido al subir el medio).
 */
public class AttachMediaService implements AttachMediaUseCase {

    private static final Set<String> ROLES = Set.of("LEAD", "BODY");

    private final ArticleMediaRepository repository;

    public AttachMediaService(ArticleMediaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ArticleMediaView> attach(AttachMediaCommand command) {
        String role = command.role() == null ? "BODY" : command.role().toUpperCase();
        if (!ROLES.contains(role)) {
            throw new DomainException("MEDIA_ROLE_INVALID", "Papel no válido: " + command.role());
        }
        repository.attach(command.articleId(), command.mediaId(), role,
                Math.max(command.position(), 0),
                command.caption() == null ? null : command.caption().trim());
        return repository.of(command.articleId());
    }

    @Override
    public List<ArticleMediaView> detach(String articleId, String mediaId) {
        repository.detach(articleId, mediaId);
        return repository.of(articleId);
    }

    @Override
    public List<ArticleMediaView> of(String articleId) {
        return repository.of(articleId);
    }
}
