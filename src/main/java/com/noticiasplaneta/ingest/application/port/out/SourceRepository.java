package com.noticiasplaneta.ingest.application.port.out;

import com.noticiasplaneta.ingest.domain.Source;
import com.noticiasplaneta.ingest.domain.SourceId;
import com.noticiasplaneta.ingest.domain.SourceState;

import java.util.List;
import java.util.Optional;

public interface SourceRepository {

    Source save(Source source);

    Optional<Source> findById(SourceId id);

    List<Source> findByState(SourceState state);
}
