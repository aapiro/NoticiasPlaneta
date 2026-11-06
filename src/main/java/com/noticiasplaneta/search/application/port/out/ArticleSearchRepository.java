package com.noticiasplaneta.search.application.port.out;

import com.noticiasplaneta.search.application.port.in.SearchHit;

import java.util.List;

public interface ArticleSearchRepository {

    /** Ranking léxico (ts_rank_cd) sobre artículos publicados. */
    List<SearchHit> lexical(String query, int limit);

    /** Ranking vectorial por distancia coseno sobre artículos publicados. */
    List<SearchHit> semantic(float[] embedding, int limit);
}
