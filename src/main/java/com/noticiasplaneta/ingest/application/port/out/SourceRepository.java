package com.noticiasplaneta.ingest.application.port.out;

import com.noticiasplaneta.ingest.domain.Source;
import com.noticiasplaneta.ingest.domain.SourceId;
import com.noticiasplaneta.ingest.domain.SourceState;

import java.util.List;
import java.util.Optional;

public interface SourceRepository {

    Source save(Source source);

    Optional<Source> findByFeedUrl(String feedUrl);

    Optional<Source> findById(SourceId id);

    List<Source> findByState(SourceState state);

    /** Borrado real. Solo debe llamarse cuando la fuente no tiene piezas asociadas. */
    void delete(SourceId id);

    /** Cuántas piezas de ingesta cuelgan de esta fuente. */
    long countItems(SourceId id);
}
