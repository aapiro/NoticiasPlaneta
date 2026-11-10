package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.news.domain.Article;

/** F06: programa un artículo para que se publique a una hora concreta. */
public interface SchedulePublishUseCase {

    Article schedule(SchedulePublishCommand command);
}
