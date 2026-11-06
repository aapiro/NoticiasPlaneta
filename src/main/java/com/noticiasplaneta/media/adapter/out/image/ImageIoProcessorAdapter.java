package com.noticiasplaneta.media.adapter.out.image;

import com.noticiasplaneta.media.application.port.out.ImageProcessorPort;
import com.noticiasplaneta.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Reencodea con ImageIO: al volver a escribir la imagen se pierden EXIF, comentarios y
 * cualquier payload embebido. Es la defensa contra polyglots (RN2 de F08).
 */
@Component
class ImageIoProcessorAdapter implements ImageProcessorPort {

    @Override
    public ProcessedImage process(byte[] raw, String declaredMime) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(raw));
            if (image == null) {
                throw new DomainException("MEDIA_DECODE_FAILED", "No se pudo decodificar la imagen");
            }

            String format = switch (declaredMime) {
                case "image/png" -> "png";
                case "image/gif" -> "gif";
                default -> "jpg";
            };

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, out)) {
                throw new DomainException("MEDIA_ENCODE_FAILED",
                        "Formato no soportado para reencodear: " + declaredMime);
            }

            byte[] encoded = out.toByteArray();
            String mime = switch (format) {
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                default -> "image/jpeg";
            };

            return new ProcessedImage(encoded, mime, image.getWidth(), image.getHeight(), sha256(encoded));
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("MEDIA_DECODE_FAILED", "Imagen ilegible: " + e.getMessage());
        }
    }

    private String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(data));
    }
}
