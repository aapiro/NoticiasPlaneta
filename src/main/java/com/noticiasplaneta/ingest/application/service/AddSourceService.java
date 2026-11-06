package com.noticiasplaneta.ingest.application.service;

import com.noticiasplaneta.ingest.application.port.in.AddSourceUseCase;
import com.noticiasplaneta.ingest.application.port.out.SourceRepository;
import com.noticiasplaneta.ingest.domain.Source;

public class AddSourceService implements AddSourceUseCase {

    private final SourceRepository sources;

    public AddSourceService(SourceRepository sources) {
        this.sources = sources;
    }

    @Override
    public Source add(String name, String feedUrl) {
        return sources.save(Source.register(name, feedUrl));
    }
}
