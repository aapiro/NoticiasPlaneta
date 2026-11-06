package com.noticiasplaneta.shared.events;

import java.util.UUID;

public record ArticleDrafted(UUID articleId, String language, String sectionId, String origin) {

    public static final String TYPE = "ArticleDrafted.v1";
}
