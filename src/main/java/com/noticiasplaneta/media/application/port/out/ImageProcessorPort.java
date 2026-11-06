package com.noticiasplaneta.media.application.port.out;

/** Reencodea la imagen (elimina EXIF y payloads) y extrae dimensiones. */
public interface ImageProcessorPort {

    ProcessedImage process(byte[] raw, String declaredMime);

    record ProcessedImage(byte[] bytes, String mime, int width, int height, String sha256) {
    }
}
