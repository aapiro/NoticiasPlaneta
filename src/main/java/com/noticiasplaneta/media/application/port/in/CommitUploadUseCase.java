package com.noticiasplaneta.media.application.port.in;

public interface CommitUploadUseCase {

    /**
     * Registra el medio ya subido.
     *
     * @param altText texto alternativo: obligatorio, es lo que lee quien no ve la imagen
     * @param credit autoría de la foto, si se conoce
     */
    CommitResult commit(String storageKey, String uploaderId, String altText, String credit);
}
