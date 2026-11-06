package com.noticiasplaneta.search.application.port.in;

public record SearchHit(String articleId, String slug, String sectionId, String title, double score) {
}
