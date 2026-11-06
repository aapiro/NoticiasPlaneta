package com.noticiasplaneta.news.support;

import com.noticiasplaneta.news.application.port.out.SearchIndexPort;

import java.util.ArrayList;
import java.util.List;

public class RecordingSearch implements SearchIndexPort {

    private final List<String> removed = new ArrayList<>();

    @Override
    public void index(String articleId) {
    }

    @Override
    public void remove(String articleId) {
        removed.add(articleId);
    }

    public List<String> removed() { return List.copyOf(removed); }
}
