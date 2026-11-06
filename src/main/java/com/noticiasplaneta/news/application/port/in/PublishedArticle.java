package com.noticiasplaneta.news.application.port.in;

import java.time.Instant;

public record PublishedArticle(String id, String slug, String sectionId, String title,
                               String summary, Instant publishedAt, String publishedLabel) {
}
