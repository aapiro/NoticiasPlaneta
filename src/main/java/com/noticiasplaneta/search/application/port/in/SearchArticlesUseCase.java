package com.noticiasplaneta.search.application.port.in;

import java.util.List;

public interface SearchArticlesUseCase {

    List<SearchHit> search(String query, int limit);
}
