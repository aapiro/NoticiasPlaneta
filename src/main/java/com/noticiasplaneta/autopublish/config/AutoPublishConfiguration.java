package com.noticiasplaneta.autopublish.config;

import com.noticiasplaneta.ai.application.port.in.ClassifyArticleUseCase;
import com.noticiasplaneta.ai.application.port.in.PublishAutoGate;
import com.noticiasplaneta.autopublish.application.port.in.AutoPublishRunUseCase;
import com.noticiasplaneta.autopublish.application.service.AutoPublishRunService;
import com.noticiasplaneta.news.application.port.in.AutoPublishUseCase;
import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class AutoPublishConfiguration {

    @Bean
    AutoPublishRunUseCase autoPublishRunUseCase(AutoPublishUseCase news, SectionAdminUseCase sections,
                                                ClassifyArticleUseCase classifier, PublishAutoGate gate,
                                                Clock clock,
                                                @Value("${autopublish.batch-size:10}") int batchSize) {
        return new AutoPublishRunService(news, sections, classifier, gate, clock, batchSize);
    }
}
