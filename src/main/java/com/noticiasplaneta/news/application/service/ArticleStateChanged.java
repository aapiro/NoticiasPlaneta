package com.noticiasplaneta.news.application.service;

import java.util.UUID;

public record ArticleStateChanged(UUID articleId, String from, String to, String actorId) {

    public static final String TYPE = "ArticleStateChanged.v1";
}
