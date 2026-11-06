package com.noticiasplaneta.media.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("F08 · Tipos permitidos y magic bytes")
class AllowedMimeTest {

    @Test
    @DisplayName("Detecta JPEG por su firma")
    void detectaJpeg() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertThat(AllowedMime.detect(jpeg)).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("Detecta PNG por su firma")
    void detectaPng() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertThat(AllowedMime.detect(png)).isEqualTo("image/png");
    }

    @Test
    @DisplayName("Un PDF con extensión .jpg no cuela")
    void detectaNoImagen() {
        byte[] pdf = {'%', 'P', 'D', 'F', '-', '1', '.', '7', 10, '%', (byte) 0xE2, (byte) 0xE3};
        assertThat(AllowedMime.detect(pdf)).isEqualTo("application/octet-stream");
        assertThat(AllowedMime.isAllowed("application/octet-stream")).isFalse();
    }

    @Test
    @DisplayName("SVG no está permitido nunca")
    void svgFuera() {
        assertThat(AllowedMime.isAllowed("image/svg+xml")).isFalse();
    }

    @Test
    @DisplayName("La extensión se deriva del MIME")
    void extension() {
        assertThat(AllowedMime.extensionFor("image/jpeg")).isEqualTo("jpg");
        assertThat(AllowedMime.extensionFor("image/png")).isEqualTo("png");
    }
}
