package com.noticiasplaneta.news.adapter.in.scheduler;

import com.noticiasplaneta.news.application.port.out.CachePurgePort;
import com.noticiasplaneta.news.application.port.out.OutboxEvent;
import com.noticiasplaneta.news.application.port.out.SearchIndexPort;
import com.noticiasplaneta.news.support.InMemoryOutbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("F06 · Relay del outbox")
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");

    @Test
    @DisplayName("Procesa el evento y lo marca como hecho")
    void procesa() {
        InMemoryOutbox outbox = new InMemoryOutbox();
        RecordingCache cache = new RecordingCache();
        RecordingSearch search = new RecordingSearch();
        OutboxRelay relay = new OutboxRelay(outbox, cache, search, Clock.fixed(NOW, ZoneOffset.UTC));

        UUID articleId = UUID.randomUUID();
        outbox.enqueue(new OutboxEvent(UUID.randomUUID(), articleId,
                "ArticlePublished.v1", "/economia/reforma-fiscal", NOW));

        relay.tick();

        assertThat(cache.urls).containsExactly("/economia/reforma-fiscal");
        assertThat(search.articleIds).containsExactly(articleId.toString());
        assertThat(outbox.pending()).isEmpty();
        assertThat(outbox.processed()).hasSize(1);
    }

    private static final class RecordingCache implements CachePurgePort {
        private final List<String> urls = new ArrayList<>();

        @Override
        public void purgeImmediately(String url) {
            urls.add(url);
        }

        @Override
        public void purgeEventually(String url) {
            urls.add(url);
        }
    }

    private static final class RecordingSearch implements SearchIndexPort {
        private final List<String> articleIds = new ArrayList<>();

        @Override
        public void index(String articleId) {
            articleIds.add(articleId);
        }

        @Override
        public void remove(String articleId) {
            articleIds.remove(articleId);
        }
    }
}
