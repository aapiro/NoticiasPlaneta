package com.noticiasplaneta.media.application.port.in;

import java.util.List;

public interface AttachMediaUseCase {

    List<ArticleMediaView> attach(AttachMediaCommand command);

    List<ArticleMediaView> detach(String articleId, String mediaId);

    List<ArticleMediaView> of(String articleId);
}
