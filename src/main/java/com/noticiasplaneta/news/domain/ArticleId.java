package com.noticiasplaneta.news.domain;

import java.util.Objects;
import java.util.UUID;

public record ArticleId(UUID value) {

    public ArticleId {
        Objects.requireNonNull(value, "value");
    }

    public static ArticleId newId() {
        return new ArticleId(UUID.randomUUID());
    }

    public static ArticleId of(String raw) {
        return new ArticleId(UUID.fromString(raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
