package com.noticiasplaneta.media.application.port.in;

public interface CommitUploadUseCase {

    CommitResult commit(String storageKey, String uploaderId);
}
