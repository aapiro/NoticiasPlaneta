package com.noticiasplaneta.search.application;

import com.noticiasplaneta.search.application.port.in.SearchHit;
import com.noticiasplaneta.search.application.port.out.ArticleSearchRepository;
import com.noticiasplaneta.search.application.port.out.EmbeddingPort;
import com.noticiasplaneta.search.application.service.SearchArticlesService;
import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("F10 · Fusión RRF")
class SearchArticlesServiceTest {

    private SearchHit hit(String id) {
        return new SearchHit(id, "slug-" + id, "economia", "Titular " + id, 0d);
    }

    @Test
    @DisplayName("Lo que aparece en los dos rankings gana al que solo aparece en uno")
    void rrfFusionaDosRankings() {
        List<SearchHit> lexical = List.of(hit("a"), hit("b"), hit("c"));
        List<SearchHit> semantic = List.of(hit("c"), hit("d"));

        List<SearchHit> fused = SearchArticlesService.fuse(lexical, semantic, 10);

        assertThat(fused).extracting(SearchHit::articleId).containsExactly("c", "a", "b", "d");
    }

    @Test
    @DisplayName("Sin resultados vectoriales devuelve el ranking léxico")
    void soloLexico() {
        List<SearchHit> fused = SearchArticlesService.fuse(List.of(hit("a"), hit("b")), List.of(), 10);

        assertThat(fused).extracting(SearchHit::articleId).containsExactly("a", "b");
    }

    @Test
    @DisplayName("Una consulta de menos de 2 caracteres se rechaza")
    void consultaCorta() {
        SearchArticlesService service = new SearchArticlesService(
                new NoopRepository(), fixed(new float[]{0f}));

        assertThatThrownBy(() -> service.search("a", 10))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("QUERY_TOO_SHORT");
    }

    @Test
    @DisplayName("Si el modelo de embeddings falla, la búsqueda degrada a léxica")
    void degradaSiFallaElModelo() {
        SearchArticlesService service = new SearchArticlesService(
                new NoopRepository(), failing());

        assertThat(service.search("reforma fiscal", 10)).isEmpty();
    }

    private static EmbeddingPort fixed(float[] vector) {
        return new EmbeddingPort() {
            @Override
            public int dimensions() {
                return vector.length;
            }

            @Override
            public float[] embed(String text) {
                return vector;
            }
        };
    }

    private static EmbeddingPort failing() {
        return new EmbeddingPort() {
            @Override
            public int dimensions() {
                return 1024;
            }

            @Override
            public float[] embed(String text) {
                throw new IllegalStateException("ollama caído");
            }
        };
    }

    private static class NoopRepository implements ArticleSearchRepository {
        @Override
        public List<SearchHit> lexical(String query, int limit) {
            return List.of();
        }

        @Override
        public List<SearchHit> semantic(float[] embedding, int limit) {
            return List.of();
        }
    }
}
