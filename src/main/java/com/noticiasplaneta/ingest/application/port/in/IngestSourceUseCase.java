package com.noticiasplaneta.ingest.application.port.in;

import com.noticiasplaneta.ingest.domain.SourceId;

public interface IngestSourceUseCase {

    IngestReport run(SourceId sourceId);
}
