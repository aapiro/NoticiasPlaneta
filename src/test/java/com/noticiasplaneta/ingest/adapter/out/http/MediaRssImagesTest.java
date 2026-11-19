package com.noticiasplaneta.ingest.adapter.out.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Las fotos del feed se extraen y se asocian a su entrada")
class MediaRssImagesTest {

    private static final String FEED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <item>
                  <guid>https://ejemplo.es/con-foto</guid>
                  <link>https://ejemplo.es/con-foto</link>
                  <title>Con foto</title>
                  <media:content url="https://img.ejemplo.es/foto.jpg" medium="image" type="image/jpeg"/>
                </item>
                <item>
                  <guid>https://ejemplo.es/sin-foto</guid>
                  <link>https://ejemplo.es/sin-foto</link>
                  <title>Sin foto</title>
                  <description>Solo texto</description>
                </item>
                <item>
                  <guid>https://ejemplo.es/solo-video</guid>
                  <link>https://ejemplo.es/solo-video</link>
                  <title>Solo video</title>
                  <media:content url="https://vid.ejemplo.es/v.mp4" medium="video" type="video/mp4"/>
                </item>
                <item>
                  <guid>https://ejemplo.es/miniatura</guid>
                  <title>Con miniatura</title>
                  <media:thumbnail url="https://img.ejemplo.es/thumb.jpg"/>
                </item>
              </channel>
            </rss>
            """;

    @Test
    @DisplayName("Saca la imagen y la indexa por guid y por link")
    void extraePorGuidYLink() {
        Map<String, String> fotos = MediaRssImages.extract(FEED.getBytes(StandardCharsets.UTF_8));

        assertThat(fotos).containsEntry("https://ejemplo.es/con-foto", "https://img.ejemplo.es/foto.jpg");
        assertThat(fotos).containsEntry("https://ejemplo.es/miniatura", "https://img.ejemplo.es/thumb.jpg");
    }

    @Test
    @DisplayName("Una entrada sin foto no aparece en el mapa")
    void sinFotoNoAparece() {
        Map<String, String> fotos = MediaRssImages.extract(FEED.getBytes(StandardCharsets.UTF_8));

        assertThat(fotos).doesNotContainKey("https://ejemplo.es/sin-foto");
    }

    @Test
    @DisplayName("Un vídeo no se confunde con una foto")
    void videoNoEsFoto() {
        Map<String, String> fotos = MediaRssImages.extract(FEED.getBytes(StandardCharsets.UTF_8));

        assertThat(fotos).doesNotContainKey("https://ejemplo.es/solo-video");
        assertThat(fotos.values()).noneMatch(u -> u.endsWith(".mp4"));
    }

    @Test
    @DisplayName("Un XML roto no revienta: devuelve vacío")
    void xmlRotoNoRevienta() {
        assertThat(MediaRssImages.extract("<rss><channel><item>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(MediaRssImages.extract(new byte[0])).isEmpty();
        assertThat(MediaRssImages.extract(null)).isEmpty();
    }
}
