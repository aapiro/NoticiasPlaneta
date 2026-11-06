package com.noticiasplaneta.news.application.port.in;

public record EditArticleResult(String articleId, String slug, String title, String body,
                                String state, int revision) {
}
