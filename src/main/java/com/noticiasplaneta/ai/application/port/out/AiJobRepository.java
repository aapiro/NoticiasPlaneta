package com.noticiasplaneta.ai.application.port.out;

import java.util.List;
import java.util.UUID;

public interface AiJobRepository {

    record Job(UUID id, UUID articleId, String type, String state, int attempts) {
    }

    void enqueueIfAbsent(UUID articleId, String type);

    List<Job> findPending(int max);

    void markDone(UUID id, long costMicros);

    void markFailed(UUID id, String error);
}
