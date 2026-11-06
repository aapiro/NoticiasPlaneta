package com.noticiasplaneta.taxonomy.application.port.in;

import java.time.Instant;
import java.util.Optional;

public interface GetTopicTimelineUseCase {

    Optional<TopicTimeline> bySlug(String slug, Instant since);
}
