package com.noticiasplaneta.media.adapter.in.web;

import com.noticiasplaneta.media.application.port.in.CommitResult;
import com.noticiasplaneta.media.application.port.in.CommitUploadUseCase;
import com.noticiasplaneta.media.application.port.in.PresignResult;
import com.noticiasplaneta.media.application.port.in.PresignUploadUseCase;
import com.noticiasplaneta.media.application.port.out.ObjectStoragePort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
class MediaController {

    private final PresignUploadUseCase presign;
    private final CommitUploadUseCase commit;
    private final ObjectStoragePort storage;

    MediaController(PresignUploadUseCase presign, CommitUploadUseCase commit,
                    ObjectStoragePort storage) {
        this.presign = presign;
        this.commit = commit;
        this.storage = storage;
    }

    record PresignRequest(@NotBlank String filename, @NotBlank String contentType,
                          @Positive long sizeBytes, String uploaderId) {
    }

    record CommitRequest(@NotBlank String storageKey, String uploaderId,
                             String altText, String credit) {
    }

    @PostMapping("/presign")
    ResponseEntity<PresignResult> presign(@Valid @RequestBody PresignRequest request) {
        return ResponseEntity.ok(presign.presign(request.filename(), request.contentType(),
                request.sizeBytes(), request.uploaderId() == null ? "anon" : request.uploaderId()));
    }

    @PostMapping("/commit")
    ResponseEntity<CommitResult> commit(@Valid @RequestBody CommitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commit.commit(request.storageKey(),
                        request.uploaderId() == null ? "anon" : request.uploaderId(),
                        request.altText(), request.credit()));
    }

    /** Recibe la subida directa firmada. En fase 2 esto lo sirve el bucket, no la aplicación. */
    @PutMapping(path = "/blob/{*key}", consumes = MediaType.ALL_VALUE)
    ResponseEntity<Void> upload(@PathVariable String key,
                                @RequestParam long exp,
                                @RequestParam String token,
                                @RequestBody byte[] body) {
        String cleanKey = key.startsWith("/") ? key.substring(1) : key;
        storage.putSigned(cleanKey, exp, token, body);
        return ResponseEntity.ok().build();
    }
}
