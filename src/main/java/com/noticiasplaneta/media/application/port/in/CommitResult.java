package com.noticiasplaneta.media.application.port.in;

public record CommitResult(String mediaId, String storageKey, String mime, int width, int height,
                           long sizeBytes, boolean deduplicated) {
}
