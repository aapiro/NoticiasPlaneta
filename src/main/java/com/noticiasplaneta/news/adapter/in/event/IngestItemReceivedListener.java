package com.noticiasplaneta.news.adapter.in.event;

import com.noticiasplaneta.ingest.application.port.in.GetIngestItemUseCase;
import com.noticiasplaneta.ingest.application.port.in.IngestItemView;
import com.noticiasplaneta.news.application.port.in.CreateDraftCommand;
import com.noticiasplaneta.news.application.port.in.CreateDraftFromItemUseCase;
import com.noticiasplaneta.shared.events.IngestItemReceived;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Cierra el circuito: un elemento ingerido se convierte en borrador automáticamente.
 *
 * <p>Sigue sin publicarse nada: el borrador espera revisión humana.
 */
@Component
class IngestItemReceivedListener {

    private final GetIngestItemUseCase ingestItems;
    private final CreateDraftFromItemUseCase createDraft;
    private final String defaultSection;

    IngestItemReceivedListener(GetIngestItemUseCase ingestItems,
                               CreateDraftFromItemUseCase createDraft,
                               @Value("${news.default-section:general}") String defaultSection) {
        this.ingestItems = ingestItems;
        this.createDraft = createDraft;
        this.defaultSection = defaultSection;
    }

    @EventListener
    void on(IngestItemReceived event) {
        IngestItemView item = ingestItems.byId(event.itemId()).orElse(null);
        if (item == null) {
            return;
        }
        createDraft.create(new CreateDraftCommand(
                item.id().toString(),
                "es",
                defaultSection,
                item.title(),
                item.body(),
                item.canonicalUrl(),
                com.noticiasplaneta.news.domain.ArticleOrigin.INGEST));
    }
}
