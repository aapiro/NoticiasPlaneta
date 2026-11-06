package com.noticiasplaneta.media.application;

import com.noticiasplaneta.media.application.port.in.CommitResult;
import com.noticiasplaneta.media.application.service.CommitUploadService;
import com.noticiasplaneta.media.application.service.PresignUploadService;
import com.noticiasplaneta.media.support.FakeImages;
import com.noticiasplaneta.media.support.FakeStorage;
import com.noticiasplaneta.media.support.InMemoryMedia;
import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("F08 · Casos de uso: presign y commit")
class CommitUploadServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");
    private static final String SHA = "b".repeat(64);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 0, 0, 0, 0, 0};

    private FakeStorage storage;
    private InMemoryMedia media;
    private CommitUploadService commit;
    private PresignUploadService presign;

    @BeforeEach
    void setUp() {
        storage = new FakeStorage();
        media = new InMemoryMedia();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        commit = new CommitUploadService(media, storage, new FakeImages(SHA, "image/png", 800, 600), clock);
        presign = new PresignUploadService(storage, clock);
    }

    @Test
    @DisplayName("El presign rechaza tipos no permitidos y tamaños fuera de rango")
    void presignValida() {
        assertThatThrownBy(() -> presign.presign("a.svg", "image/svg+xml", 100, "u1"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("MIME_NOT_ALLOWED");

        assertThatThrownBy(() -> presign.presign("a.png", "image/png", 0, "u1"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("MEDIA_SIZE_INVALID");
    }

    @Test
    @DisplayName("Un commit válido registra el activo con su clave content-addressed")
    void commitValido() {
        storage.seed("tmp/1", PNG);

        CommitResult result = commit.commit("tmp/1", "u1");

        assertThat(result.deduplicated()).isFalse();
        assertThat(result.mime()).isEqualTo("image/png");
        assertThat(result.width()).isEqualTo(800);
        assertThat(result.storageKey()).isEqualTo("media/bb/" + SHA + ".png");
        assertThat(storage.objects()).containsKey(result.storageKey());
        assertThat(storage.wasDeleted("tmp/1")).isTrue();
        assertThat(media.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un archivo que no es imagen se rechaza y se borra")
    void magicBytesMienten() {
        byte[] pdf = {'%', 'P', 'D', 'F', '-', '1', '.', '7', 0, 0, 0, 0, 0, 0, 0, 0};
        storage.seed("tmp/2", pdf);

        assertThatThrownBy(() -> commit.commit("tmp/2", "u1"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("MIME_MISMATCH");

        assertThat(storage.wasDeleted("tmp/2")).isTrue();
        assertThat(media.count()).isZero();
    }

    @Test
    @DisplayName("El mismo binario subido dos veces se deduplica")
    void deduplica() {
        storage.seed("tmp/1", PNG);
        CommitResult first = commit.commit("tmp/1", "u1");

        storage.seed("tmp/2", PNG);
        CommitResult second = commit.commit("tmp/2", "u1");

        assertThat(second.deduplicated()).isTrue();
        assertThat(second.mediaId()).isEqualTo(first.mediaId());
        assertThat(media.count()).isEqualTo(1);
        assertThat(storage.wasDeleted("tmp/2")).isTrue();
    }

    @Test
    @DisplayName("Un objeto inexistente devuelve MEDIA_NOT_FOUND")
    void objetoInexistente() {
        assertThatThrownBy(() -> commit.commit("tmp/no-existe", "u1"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("MEDIA_NOT_FOUND");
    }
}
