package com.noticiasplaneta.media.domain;

import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("F08 · Activo de medios")
class MediaAssetTest {

    private static final String SHA = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");

    @Test
    @DisplayName("La clave es content-addressed")
    void claveContentAddressed() {
        MediaAsset asset = MediaAsset.register(SHA, "image/png", 800, 600, 1024, "u1", NOW);

        assertThat(asset.storageKey()).isEqualTo("media/aa/" + SHA + ".png");
    }

    @Test
    @DisplayName("Un MIME no permitido se rechaza")
    void mimeNoPermitido() {
        assertThatThrownBy(() -> MediaAsset.register(SHA, "image/svg+xml", 10, 10, 100, "u1", NOW))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("MIME_NOT_ALLOWED");
    }

    @Test
    @DisplayName("Un tamaño fuera de rango se rechaza")
    void tamanoInvalido() {
        assertThatThrownBy(() -> MediaAsset.register(SHA, "image/png", 10, 10,
                AllowedMime.MAX_SIZE_BYTES + 1, "u1", NOW))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("MEDIA_SIZE_INVALID");
    }

    @Test
    @DisplayName("Un activo sin alt no es publicable")
    void altObligatorio() {
        MediaAsset asset = MediaAsset.register(SHA, "image/png", 10, 10, 100, "u1", NOW);

        assertThatThrownBy(asset::assertPublishable)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("ALT_REQUIRED");
    }

    @Test
    @DisplayName("Con alt ya es publicable")
    void conAltEsPublicable() {
        MediaAsset asset = MediaAsset.register(SHA, "image/png", 10, 10, 100, "u1", NOW);
        asset.describe("Un edificio del Congreso", "Foto: EFE");

        assertThatCode(asset::assertPublishable).doesNotThrowAnyException();
        assertThat(asset.credit()).isEqualTo("Foto: EFE");
    }
}
