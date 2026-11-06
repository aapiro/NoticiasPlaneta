package com.noticiasplaneta.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vectores de prueba de la RFC 6238 (apéndice B), adaptados a 6 dígitos.
 * Secreto de prueba: "12345678901234567890" en ASCII.
 */
@DisplayName("F09 · TOTP (RFC 6238)")
class TotpTest {

    private final byte[] rfcSecret = "12345678901234567890".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private final TotpSecret secret = new TotpSecret(TotpSecret.encodeBase32(rfcSecret));

    @Test
    @DisplayName("Coincide con los vectores de la RFC para 6 dígitos")
    void vectoresRfc() {
        assertThat(Totp.code(secret, Instant.ofEpochSecond(59))).isEqualTo("287082");
        assertThat(Totp.code(secret, Instant.ofEpochSecond(1111111109))).isEqualTo("081804");
        assertThat(Totp.code(secret, Instant.ofEpochSecond(1234567890))).isEqualTo("005924");
        assertThat(Totp.code(secret, Instant.ofEpochSecond(2000000000))).isEqualTo("279037");
    }

    @Test
    @DisplayName("El código vale dentro de la ventana de ±30 s")
    void ventanaDeTolerancia() {
        Instant now = Instant.ofEpochSecond(1111111109);
        String code = Totp.code(secret, now);

        assertThat(Totp.verify(secret, code, now)).isTrue();
        assertThat(Totp.verify(secret, code, now.plusSeconds(29))).isTrue();
    }

    @Test
    @DisplayName("Un código de hace dos pasos ya no vale")
    void fueraDeVentana() {
        Instant now = Instant.ofEpochSecond(1111111109);
        String code = Totp.code(secret, now);

        assertThat(Totp.verify(secret, code, now.plusSeconds(95))).isFalse();
    }

    @Test
    @DisplayName("Un código inventado se rechaza")
    void codigoIncorrecto() {
        assertThat(Totp.verify(secret, "000000", Instant.ofEpochSecond(1111111109))).isFalse();
        assertThat(Totp.verify(secret, "", Instant.ofEpochSecond(1111111109))).isFalse();
        assertThat(Totp.verify(secret, null, Instant.ofEpochSecond(1111111109))).isFalse();
    }

    @Test
    @DisplayName("El secreto generado es base32 válido y decodifica a 20 bytes")
    void secretoGenerado() {
        TotpSecret generated = TotpSecret.generate();

        assertThat(generated.base32()).matches("[A-Z2-7]+");
        assertThat(generated.bytes()).hasSize(20);
    }

    @Test
    @DisplayName("El mismo secreto produce el mismo código (determinista)")
    void determinista() {
        Instant t = Instant.ofEpochSecond(1700000000);

        assertThat(Totp.code(secret, t)).isEqualTo(Totp.code(secret, t)).hasSize(6);
    }
}
