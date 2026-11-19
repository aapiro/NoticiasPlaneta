package com.noticiasplaneta.ai.adapter.in.event;

import com.noticiasplaneta.ai.application.port.in.EnrichArticleUseCase;
import com.noticiasplaneta.ai.application.port.out.AiCapabilities;
import com.noticiasplaneta.ai.domain.AiCapability;
import com.noticiasplaneta.ai.domain.AiMode;
import com.noticiasplaneta.shared.events.ArticleStateChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ADR-011 F0 · No se encola trabajo de IA si la capacidad está apagada")
class ArticlePublishedListenerTest {

    private final EnrichArticleUseCase enrich = mock(EnrichArticleUseCase.class);
    private final AiCapabilities capabilities = mock(AiCapabilities.class);

    private ArticleStateChanged published() {
        ArticleStateChanged event = mock(ArticleStateChanged.class);
        when(event.to()).thenReturn("PUBLISHED");
        when(event.articleId()).thenReturn(UUID.randomUUID());
        return event;
    }

    @Test
    @DisplayName("Con ENRICH_TAGS apagada no se crea ningún job")
    void apagadaNoEncola() {
        when(capabilities.isOn(AiCapability.ENRICH_TAGS)).thenReturn(false);
        ArticlePublishedListener listener = new ArticlePublishedListener(enrich, capabilities);

        listener.on(published());

        verify(enrich, never()).enqueue(any());
    }

    @Test
    @DisplayName("Con ENRICH_TAGS encendida se encola exactamente una vez")
    void encendidaEncola() {
        when(capabilities.isOn(AiCapability.ENRICH_TAGS)).thenReturn(true);
        ArticlePublishedListener listener = new ArticlePublishedListener(enrich, capabilities);

        listener.on(published());

        verify(enrich, times(1)).enqueue(any());
    }

    @Test
    @DisplayName("Un cambio a DRAFT nunca encola")
    void borradorNoEncola() {
        when(capabilities.isOn(AiCapability.ENRICH_TAGS)).thenReturn(true);
        ArticleStateChanged draft = mock(ArticleStateChanged.class);
        when(draft.to()).thenReturn("DRAFT");
        ArticlePublishedListener listener = new ArticlePublishedListener(enrich, capabilities);

        listener.on(draft);

        verify(enrich, never()).enqueue(any());
    }
}
