package com.noticiasplaneta.ingest.adapter.in.web;

import com.noticiasplaneta.ingest.application.port.in.AddSourceUseCase;
import com.noticiasplaneta.ingest.application.port.in.IngestReport;
import com.noticiasplaneta.ingest.application.port.in.IngestSourceUseCase;
import com.noticiasplaneta.ingest.domain.Source;
import com.noticiasplaneta.ingest.domain.SourceId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sources")
class IngestController {

    private final AddSourceUseCase addSource;
    private final IngestSourceUseCase ingestSource;

    IngestController(AddSourceUseCase addSource, IngestSourceUseCase ingestSource) {
        this.addSource = addSource;
        this.ingestSource = ingestSource;
    }

    record NewSourceRequest(@NotBlank String name, @NotBlank String feedUrl) {
    }

    record SourceResponse(String id, String name, String feedUrl, String state, int failureCount) {
        static SourceResponse of(Source s) {
            return new SourceResponse(s.id().toString(), s.name(), s.feedUrl(),
                    s.state().name(), s.failureCount());
        }
    }

    @PostMapping
    ResponseEntity<SourceResponse> create(@Valid @RequestBody NewSourceRequest request) {
        Source source = addSource.add(request.name(), request.feedUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(SourceResponse.of(source));
    }

    @PostMapping("/{id}/ingest")
    ResponseEntity<IngestReport> ingest(@PathVariable String id) {
        return ResponseEntity.ok(ingestSource.run(SourceId.of(id)));
    }
}
