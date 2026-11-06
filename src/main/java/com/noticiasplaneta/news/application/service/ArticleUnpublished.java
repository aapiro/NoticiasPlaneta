package com.noticiasplaneta.news.application.service;

import java.util.UUID;

public record ArticleUnpublished(UUID articleId, String url, String kind, String reason) {

    public static final String TYPE = "ArticleUnpublished.v1";
}
