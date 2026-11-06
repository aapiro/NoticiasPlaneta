package com.noticiasplaneta.media.application.port.in;

/** Un medio colocado en un artículo. */
public record ArticleMediaView(String mediaId, String storageKey, String mime, int width, int height,
                               String altText, String credit, String role, int position,
                               String caption) {
}
