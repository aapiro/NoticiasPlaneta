package com.noticiasplaneta.media.config;

import com.noticiasplaneta.media.application.port.in.CommitUploadUseCase;
import com.noticiasplaneta.media.application.port.in.PresignUploadUseCase;
import com.noticiasplaneta.media.application.port.out.ImageProcessorPort;
import com.noticiasplaneta.media.application.port.out.MediaRepository;
import com.noticiasplaneta.media.application.port.out.ObjectStoragePort;
import com.noticiasplaneta.media.application.service.CommitUploadService;
import com.noticiasplaneta.media.application.service.PresignUploadService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class MediaConfiguration {

    @Bean
    PresignUploadUseCase presignUploadUseCase(ObjectStoragePort storage, Clock clock) {
        return new PresignUploadService(storage, clock);
    }

    @Bean
    CommitUploadUseCase commitUploadUseCase(MediaRepository media, ObjectStoragePort storage,
                                            ImageProcessorPort images, Clock clock) {
        return new CommitUploadService(media, storage, images, clock);
    }
}
