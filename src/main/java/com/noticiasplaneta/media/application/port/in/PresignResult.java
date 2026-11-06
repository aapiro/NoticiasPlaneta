package com.noticiasplaneta.media.application.port.in;

import java.time.Instant;

public record PresignResult(String uploadUrl, String storageKey, Instant expiresAt) {
}
