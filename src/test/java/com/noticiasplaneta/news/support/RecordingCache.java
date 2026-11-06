package com.noticiasplaneta.news.support;

import com.noticiasplaneta.news.application.port.out.CachePurgePort;

import java.util.ArrayList;
import java.util.List;

public class RecordingCache implements CachePurgePort {

    private final List<String> immediate = new ArrayList<>();
    private final List<String> eventual = new ArrayList<>();

    @Override
    public void purgeImmediately(String url) {
        immediate.add(url);
    }

    @Override
    public void purgeEventually(String url) {
        eventual.add(url);
    }

    public List<String> immediate() { return List.copyOf(immediate); }
    public List<String> eventual() { return List.copyOf(eventual); }
}
