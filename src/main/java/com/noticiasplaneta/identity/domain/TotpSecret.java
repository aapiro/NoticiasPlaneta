package com.noticiasplaneta.identity.domain;

import com.noticiasplaneta.shared.domain.DomainException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Secreto TOTP en base32 (RFC 4648, sin relleno), como lo esperan las apps de autenticación.
 */
public record TotpSecret(String base32) {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    public TotpSecret {
        if (base32 == null || base32.isBlank()) {
            throw new DomainException("TOTP_SECRET_INVALID", "El secreto TOTP no puede estar vacío");
        }
    }

    /** 160 bits de entropía, el tamaño recomendado por la RFC 4226. */
    public static TotpSecret generate() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return new TotpSecret(encodeBase32(bytes));
    }

    public byte[] bytes() {
        return decodeBase32(base32);
    }

    static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    static byte[] decodeBase32(String text) {
        String clean = text.replace("=", "").replace(" ", "").toUpperCase(java.util.Locale.ROOT);
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char c : clean.toCharArray()) {
            int value = ALPHABET.indexOf(c);
            if (value < 0) {
                throw new DomainException("TOTP_SECRET_INVALID", "Carácter no válido en base32: " + c);
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    @Override
    public String toString() {
        return base32;
    }

    public static TotpSecret ofRaw(String raw) {
        return new TotpSecret(raw.getBytes(StandardCharsets.UTF_8).length > 0
                ? encodeBase32(raw.getBytes(StandardCharsets.UTF_8))
                : null);
    }
}
