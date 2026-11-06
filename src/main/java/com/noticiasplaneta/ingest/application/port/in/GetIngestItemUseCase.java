package com.noticiasplaneta.ingest.application.port.in;

import java.util.Optional;
import java.util.UUID;

public interface GetIngestItemUseCase {

    Optional<IngestItemView> byId(UUID itemId);
}
