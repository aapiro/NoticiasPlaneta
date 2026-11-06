package com.noticiasplaneta.media.adapter.in.web;

import com.noticiasplaneta.media.application.port.out.ObjectStoragePort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Sirve los binarios de medios.
 *
 * <p>El contenido es direccionable por hash (content-addressed), así que la respuesta
 * puede cachearse para siempre: si cambia el hash, cambia la URL.
 */
@RestController
class MediaServingController {

    /** media/ab/<sha256>.<ext> — se valida para no permitir rutas arbitrarias. */
    private static final Pattern KEY = Pattern.compile("^media/[0-9a-f]{2}/[0-9a-f]{64}\\.[a-z0-9]{3,4}$");

    private final ObjectStoragePort storage;

    MediaServingController(ObjectStoragePort storage) {
        this.storage = storage;
    }

    @GetMapping("/media/{prefix}/{file}")
    ResponseEntity<byte[]> serve(@PathVariable String prefix, @PathVariable String file) {
        String key = "media/" + prefix + "/" + file;
        if (!KEY.matcher(key).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medio no encontrado");
        }

        byte[] bytes;
        try {
            bytes = storage.get(key);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medio no encontrado");
        }

        MediaType tipo = file.endsWith(".png") ? MediaType.IMAGE_PNG
                : file.endsWith(".avif") ? MediaType.parseMediaType("image/avif")
                : file.endsWith(".webp") ? MediaType.parseMediaType("image/webp")
                : MediaType.IMAGE_JPEG;

        return ResponseEntity.ok()
                .contentType(tipo)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }
}
