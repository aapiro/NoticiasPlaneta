package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.news.domain.SourceKind;

/** Vincula una URL a un artículo y recalcula su contraste. */
public record LinkSourceCommand(String articleId, String url, SourceKind kind, Integer weight) {
}
