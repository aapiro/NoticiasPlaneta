package com.noticiasplaneta.media.application.port.in;

import com.noticiasplaneta.media.application.port.in.PresignResult;

public interface PresignUploadUseCase {

    PresignResult presign(String filename, String contentType, long sizeBytes, String uploaderId);
}
