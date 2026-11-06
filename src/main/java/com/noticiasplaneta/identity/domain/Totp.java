package com.noticiasplaneta.identity.domain;

import com.noticiasplaneta.shared.domain.DomainException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Instant;

/**
 * TOTP (RFC 6238) sobre HOTP (RFC 4226): HMAC-SHA1, paso de 30 s, 6 dígitos.
 *
 * <p>Sin dependencias externas: son 40 líneas y así se puede verificar contra los vectores
 * de prueba de la propia RFC.
 */
public final class Totp {

    public static final int DIGITS = 6;
    public static final int STEP_SECONDS = 30;
    /** Ventana de tolerancia: acepta el código anterior y el siguiente (±30 s de desfase). */
    public static final int WINDOW = 1;

    private Totp() {
    }

    public static String code(TotpSecret secret, Instant at) {
        return hotp(secret.bytes(), at.getEpochSecond() / STEP_SECONDS, DIGITS);
    }

    public static boolean verify(TotpSecret secret, String candidate, Instant at) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        String clean = candidate.replace(" ", "");
        long counter = at.getEpochSecond() / STEP_SECONDS;
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            String expected = hotp(secret.bytes(), counter + offset, DIGITS);
            if (constantTimeEquals(expected, clean)) {
                return true;
            }
        }
        return false;
    }

    static String hotp(byte[] key, long counter, int digits) {
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int modulo = (int) Math.pow(10, digits);
            String code = Integer.toString(binary % modulo);
            return "0".repeat(digits - code.length()) + code;
        } catch (Exception e) {
            throw new DomainException("TOTP_FAILURE", "No se pudo calcular el código: " + e.getMessage());
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
