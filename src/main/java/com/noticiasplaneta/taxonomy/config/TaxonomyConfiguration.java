package com.noticiasplaneta.taxonomy.config;

import com.noticiasplaneta.taxonomy.application.port.in.GetTopicTimelineUseCase;
import com.noticiasplaneta.taxonomy.application.port.out.TopicRepository;
import com.noticiasplaneta.taxonomy.application.service.TopicTimelineService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TaxonomyConfiguration {

    @Bean
    GetTopicTimelineUseCase getTopicTimelineUseCase(TopicRepository topics) {
        return new TopicTimelineService(topics);
    }
}
