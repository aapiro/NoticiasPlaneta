package com.noticiasplaneta.ingest.config;

import com.noticiasplaneta.ingest.application.port.in.AddSourceUseCase;
import com.noticiasplaneta.ingest.application.port.in.IngestSourceUseCase;
import com.noticiasplaneta.ingest.application.port.out.ExecutionLock;
import com.noticiasplaneta.ingest.application.port.out.FeedFetcher;
import com.noticiasplaneta.ingest.application.port.out.IngestItemRepository;
import com.noticiasplaneta.ingest.application.port.out.SourceRepository;
import com.noticiasplaneta.ingest.application.service.AddSourceService;
import com.noticiasplaneta.ingest.application.service.IngestSourceService;
import com.noticiasplaneta.shared.domain.EventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class IngestConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    IngestSourceUseCase ingestSourceUseCase(SourceRepository sources,
                                            IngestItemRepository items,
                                            FeedFetcher fetcher,
                                            ExecutionLock lock,
                                            EventPublisher events,
                                            Clock clock) {
        return new IngestSourceService(sources, items, fetcher, lock, events, clock);
    }

    @Bean
    AddSourceUseCase addSourceUseCase(SourceRepository sources) {
        return new AddSourceService(sources);
    }
}
