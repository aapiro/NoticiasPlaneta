package com.noticiasplaneta.news.application.port.out;

import com.noticiasplaneta.news.application.port.in.ArticleRow;

import java.util.List;
import java.util.Map;

public interface ArticleAdminQueryRepository {

    List<ArticleRow> list(String state, String sectionId, String text, int limit);

    Map<String, Long> countsByState();

    List<AuditRow> auditOf(String articleId);

    record AuditRow(String actor, String action, String before, String after, String at) {
    }
}
