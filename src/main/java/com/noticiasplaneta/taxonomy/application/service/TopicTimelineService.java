package com.noticiasplaneta.taxonomy.application.service;

import com.noticiasplaneta.taxonomy.application.port.in.GetTopicTimelineUseCase;
import com.noticiasplaneta.taxonomy.application.port.in.TopicTimeline;
import com.noticiasplaneta.taxonomy.application.port.out.TopicRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * F11 — Línea temporal y delta.
 *
 * <p>RN1: el delta se calcula en el servidor a partir de una fecha que envía el cliente.
 * RN2: sin fecha no hay delta ni cookies: no se rastrea a nadie para esto.
 */
public class TopicTimelineService implements GetTopicTimelineUseCase {

    private final TopicRepository topics;

    public TopicTimelineService(TopicRepository topics) {
        this.topics = topics;
    }

    @Override
    public Optional<TopicTimeline> bySlug(String slug, Instant since) {
        return topics.findTimeline(slug).map(base -> {
            if (since == null) {
                return new TopicTimeline(base.slug(), base.title(), base.summary(),
                        base.entries(), null, List.of());
            }
            return new TopicTimeline(base.slug(), base.title(), base.summary(),
                    base.entries(), since, topics.findPublishedAfter(slug, since));
        });
    }
}
