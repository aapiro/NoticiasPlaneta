package com.noticiasplaneta.taxonomy.application.port.out;

import com.noticiasplaneta.taxonomy.application.port.in.TopicTimeline;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TopicRepository {

    Optional<TopicTimeline> findTimeline(String slug);

    List<TopicTimeline.Entry> findPublishedAfter(String slug, Instant since);
}
