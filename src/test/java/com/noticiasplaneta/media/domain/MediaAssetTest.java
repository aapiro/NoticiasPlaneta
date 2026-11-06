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

    static final String ALT = "Un autobús siniestrado en la cuneta";

    private static final String SHA = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");

    @Test
    @DisplayName("La clave es content-addressed")
    void claveContentAddressed() {
        MediaAsset asset = MediaAsset.register(SHA, "image/png", 800, 600, 1024, "u1", NOW, "Un autobús siniestrado en la cuneta", "Redacción");

        assertThat(asset.storageKey()).isEqualTo("media/aa/" + SHA + ".png");
    }

    @Test
    @DisplayName("Un MIME no permitido se rechaza")
    void mimeNoPermitido() {
        assertThatThrownBy(() -> MediaAsset.register(SHA, "image/svg+xml", 10, 10, 100, "u1", NOW, "Un autobús siniestrado en la cuneta", null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("MIME_NOT_ALLOWED");
    }

    @Test
    @DisplayName("Un tamaño fuera de rango se rechaza")
    void tamanoInvalido() {
        assertThatThrownBy(() -> MediaAsset.register(SHA, "image/png", 10, 10,
                AllowedMime.MAX_SIZE_BYTES + 1, "u1", NOW, ALT, null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("MEDIA_SIZE_INVALID");
    }

    @Test
    @DisplayName("Un activo antiguo sin alt sigue sin poder publicarse")
    void altObligatorio() {
        // los activos registrados antes de exigir el alt se rehidratan sin él
        MediaAsset viejo = MediaAsset.rehydrate(MediaAssetId.newId(), SHA, "media/ab/x.png",
                "image/png", 10, 10, 100, "u1", NOW, null, null);

        assertThatThrownBy(viejo::assertPublishable)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("ALT_REQUIRED");
    }

    @Test
    @DisplayName("Con alt ya es publicable")
    void conAltEsPublicable() {
        MediaAsset asset = MediaAsset.register(SHA, "image/png", 10, 10, 100, "u1", NOW, "Un autobús siniestrado en la cuneta", null);
        asset.describe("Un edificio del Congreso", "Foto: EFE");

        assertThatCode(asset::assertPublishable).doesNotThrowAnyException();
        assertThat(asset.credit()).isEqualTo("Foto: EFE");
    }

    @Test
    @DisplayName("Sin texto alternativo no se registra el medio")
    void sinAlt() {
        assertThatThrownBy(() -> MediaAsset.register(SHA, "image/png", 10, 10, 100, "u1", NOW, null, null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("ALT_TEXT_REQUIRED");
    }

    @Test
    @DisplayName("Un alt de dos palabras no describe nada y se rechaza")
    void altCorto() {
        assertThatThrownBy(() -> MediaAsset.register(SHA, "image/png", 10, 10, 100, "u1", NOW, "foto", null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("ALT_TEXT_REQUIRED");
    }

    @Test
    @DisplayName("El alt y el crédito se guardan recortados")
    void guardaAltYCredito() {
        MediaAsset asset = MediaAsset.register(SHA, "image/png", 10, 10, 100, "u1", NOW,
                "  Un autobús siniestrado  ", "  Redacción  ");
        assertThat(asset.altText()).isEqualTo("Un autobús siniestrado");
        assertThat(asset.credit()).isEqualTo("Redacción");
    }
}
