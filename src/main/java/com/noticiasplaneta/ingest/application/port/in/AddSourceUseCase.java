package com.noticiasplaneta.ingest.application.port.in;

import com.noticiasplaneta.ingest.domain.Source;

public interface AddSourceUseCase {

    Source add(String name, String feedUrl);
}
