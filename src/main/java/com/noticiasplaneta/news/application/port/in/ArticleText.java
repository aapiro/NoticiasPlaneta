package com.noticiasplaneta.news.application.port.in;

import java.util.UUID;

public record ArticleText(UUID articleId, String title, String body, String sectionId,
                          String state, String origin, int revision) {
}
