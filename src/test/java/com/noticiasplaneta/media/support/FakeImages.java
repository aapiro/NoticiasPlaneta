package com.noticiasplaneta.media.support;

import com.noticiasplaneta.media.application.port.out.ImageProcessorPort;

public class FakeImages implements ImageProcessorPort {

    private final String sha;
    private final String mime;
    private final int width;
    private final int height;

    public FakeImages(String sha, String mime, int width, int height) {
        this.sha = sha;
        this.mime = mime;
        this.width = width;
        this.height = height;
    }

    @Override
    public ProcessedImage process(byte[] raw, String declaredMime) {
        return new ProcessedImage(new byte[]{1, 2, 3}, mime, width, height, sha);
    }
}
