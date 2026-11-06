package com.noticiasplaneta.media.application.port.in;

/** Coloca un medio ya subido en un artículo. */
public record AttachMediaCommand(String articleId, String mediaId, String role,
                                 int position, String caption) {
}
