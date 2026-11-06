package com.noticiasplaneta.media.adapter.out.storage;

import com.noticiasplaneta.media.application.port.out.ObjectStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Almacenamiento en disco con URL de subida firmada por HMAC.
 *
 * <p>Mantiene el mismo contrato que S3 (URL prefirmada + PUT), así que al migrar a R2 solo
 * cambia este adaptador.
 */
@Component
class LocalFilesystemStorageAdapter implements ObjectStoragePort {

    private final Path root;
    private final String secret;

    LocalFilesystemStorageAdapter(@Value("${media.root:${java.io.tmpdir}/noticiasplaneta-media}") String root,
                                  @Value("${media.signing-secret:dev-only-change-me}") String secret) {
        this.root = Path.of(root);
        this.secret = secret;
        try {
            Files.createDirectories(this.root);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se puede crear el almacén de medios en " + this.root
                            + " (revisa permisos del volumen)", e);
        }
    }

    @Override
    public String presignPut(String key, String contentType, Duration ttl) {
        long expiresAt = System.currentTimeMillis() / 1000 + ttl.toSeconds();
        String token = sign(key, expiresAt);
        return "/api/v1/media/blob/" + key + "?exp=" + expiresAt + "&token=" + token;
    }

    @Override
    public void put(String key, byte[] content) {
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo escribir " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (Exception ignored) {
            // el objeto temporal se limpiará con el barrido periódico
        }
    }

    @Override
    public void putSigned(String key, long expiresAt, String token, byte[] content) {
        if (expiresAt < System.currentTimeMillis() / 1000 || !sign(key, expiresAt).equals(token)) {
            throw new com.noticiasplaneta.shared.domain.DomainException(
                    "UPLOAD_TOKEN_INVALID", "URL de subida caducada o firma incorrecta");
        }
        put(key, content);
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root.normalize())) {
            throw new IllegalArgumentException("Ruta fuera del almacén: " + key);
        }
        return path;
    }

    private String sign(String key, long expiresAt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal((key + "|" + expiresAt).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar la URL", e);
        }
    }

    static String sha256Hex(byte[] data) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
