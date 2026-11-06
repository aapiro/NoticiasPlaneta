package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.news.domain.ArticleOrigin;

public record CreateDraftCommand(
        String ingestItemId,
        String language,
        String sectionId,
        String title,
        String body,
        String sourceUrl,
        ArticleOrigin origin) {
}
