package com.noticiasplaneta.media.application.service;

import com.noticiasplaneta.media.application.port.in.CacheFeedImageUseCase;
import com.noticiasplaneta.media.application.port.out.ArticleMediaRepository;
import com.noticiasplaneta.media.application.port.out.ImageProcessorPort;
import com.noticiasplaneta.media.application.port.out.MediaRepository;
import com.noticiasplaneta.media.application.port.out.ObjectStoragePort;
import com.noticiasplaneta.media.domain.MediaAsset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Descarga la foto del feed y la deja servida desde casa (backlog #2).
 *
 * <p>Content-addressed: si dos noticias traen la misma foto, se guarda una sola vez
 * y las dos la comparten. El reencodeo lo hace {@link ImageProcessorPort}, que además
 * elimina EXIF y payloads raros.
 *
 * <p>El tipo se toma del {@code Content-Type} de la respuesta, no de la extensión:
 * hay medios que sirven WebP con nombre {@code .png} y fiarse de la extensión hace
 * fallar la decodificación.
 */
@Service
class CacheFeedImageService implements CacheFeedImageUseCase {

    private static final Logger log = LoggerFactory.getLogger(CacheFeedImageService.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final String ALT_POR_DEFECTO = "Imagen de la noticia";
    private static final String UPLOADER = "feed";

    private final HttpClient http;
    private final ImageProcessorPort processor;
    private final ObjectStoragePort storage;
    private final MediaRepository media;
    private final ArticleMediaRepository articleMedia;
    private final Clock clock;

    CacheFeedImageService(ImageProcessorPort processor, ObjectStoragePort storage,
                          MediaRepository media, ArticleMediaRepository articleMedia, Clock clock) {
        this.processor = processor;
        this.storage = storage;
        this.media = media;
        this.articleMedia = articleMedia;
        this.clock = clock;
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public Optional<String> cacheFromUrl(String imageUrl, String articleId, String altText) {
        if (imageUrl == null || imageUrl.isBlank() || articleId == null) return Optional.empty();

        Descarga descarga = descargar(imageUrl);
        if (descarga == null) return Optional.empty();
        if (descarga.bytes().length > MAX_BYTES) {
            log.warn("foto descartada por tamaño ({} bytes): {}", descarga.bytes().length, imageUrl);
            return Optional.empty();
        }
        if (descarga.mime().equals("image/webp") || descarga.mime().equals("image/avif")) {
            // ImageIO estándar no decodifica estos formatos sin plugin nativo. Se deja
            // la URL original antes que guardar un fichero que no se puede reencode.
            log.info("formato no decodificable sin plugin ({}): {}", descarga.mime(), imageUrl);
            return Optional.empty();
        }

        try {
            ImageProcessorPort.ProcessedImage procesada = processor.process(descarga.bytes(), descarga.mime());

            // Deduplicación gratis por huella: la misma foto en dos noticias es un solo activo.
            MediaAsset asset = media.findBySha256(procesada.sha256())
                    .orElseGet(() -> guardar(procesada, altText));

            articleMedia.attach(articleId, asset.id().value().toString(), "LEAD", 0, null);
            return Optional.of(asset.id().value().toString());

        } catch (RuntimeException e) {
            // Una foto que no se puede procesar no invalida la noticia.
            log.warn("no se pudo cachear la foto {}: {}", imageUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private MediaAsset guardar(ImageProcessorPort.ProcessedImage procesada, String altText) {
        storage.put(MediaAsset.storageKeyFor(procesada.sha256(), procesada.mime()), procesada.bytes());

        MediaAsset asset = MediaAsset.register(
                procesada.sha256(), procesada.mime(), procesada.width(), procesada.height(),
                procesada.bytes().length, UPLOADER, clock.instant(),
                altTextValido(altText), null);
        return media.save(asset);
    }

    private Descarga descargar(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "NoticiasPlaneta/1.0 (+https://noticiasplaneta.com)")
                    .header("Accept", "image/*")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("foto no descargada (HTTP {}): {}", response.statusCode(), url);
                return null;
            }
            String tipo = response.headers().firstValue("Content-Type").orElse("").split(";")[0].trim();
            if (!tipo.startsWith("image/")) {
                tipo = porExtension(url);
            }
            return new Descarga(response.body(), tipo);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("foto no descargada ({}): {}", e.getClass().getSimpleName(), url);
            return null;
        }
    }

    /** Solo si el servidor no dice el tipo. */
    private static String porExtension(String url) {
        String limpia = url.toLowerCase();
        if (limpia.contains(".png")) return "image/png";
        if (limpia.contains(".webp")) return "image/webp";
        if (limpia.contains(".gif")) return "image/gif";
        return "image/jpeg";
    }

    /** {@code MediaAsset} exige 8 caracteres: sin texto útil, uno honesto y genérico. */
    private static String altTextValido(String altText) {
        if (altText == null) return ALT_POR_DEFECTO;
        String limpio = altText.replaceAll("\\s+", " ").trim();
        if (limpio.length() < MediaAsset.MIN_ALT_LENGTH) return ALT_POR_DEFECTO;
        return limpio.length() > 300 ? limpio.substring(0, 300) : limpio;
    }

    private record Descarga(byte[] bytes, String mime) {
    }
}
