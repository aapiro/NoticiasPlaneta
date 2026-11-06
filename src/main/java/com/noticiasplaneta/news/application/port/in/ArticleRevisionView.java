package com.noticiasplaneta.news.application.port.in;

/** Una versión anterior del texto, tal como se guardó. */
public record ArticleRevisionView(int revision, String title, String body, String editedBy,
                                  String editedAt, java.util.List<String> changeSummary) {
}
