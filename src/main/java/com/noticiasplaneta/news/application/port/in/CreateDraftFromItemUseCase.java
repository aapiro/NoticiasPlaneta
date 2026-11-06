package com.noticiasplaneta.news.application.port.in;

public interface CreateDraftFromItemUseCase {

    CreateDraftResult create(CreateDraftCommand command);
}
