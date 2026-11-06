package com.noticiasplaneta.news.application.service;

import java.util.UUID;

/** Evento de dominio: se ha creado un borrador. */
public record ArticleDrafted(UUID articleId, String language, String sectionId, String origin) {

    public static final String TYPE = "ArticleDrafted.v1";
}
