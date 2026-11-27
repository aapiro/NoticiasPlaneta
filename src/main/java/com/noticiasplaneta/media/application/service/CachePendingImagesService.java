package com.noticiasplaneta.media.application.service;

import com.noticiasplaneta.media.application.port.in.CacheFeedImageUseCase;
import com.noticiasplaneta.media.application.port.in.CachePendingImagesUseCase;
import com.noticiasplaneta.media.application.port.out.PendingImageRepository;
import org.springframework.stereotype.Service;

@Service
class CachePendingImagesService implements CachePendingImagesUseCase {

    private final PendingImageRepository pendientes;
    private final CacheFeedImageUseCase cache;

    CachePendingImagesService(PendingImageRepository pendientes, CacheFeedImageUseCase cache) {
        this.pendientes = pendientes;
        this.cache = cache;
    }

    @Override
    public long pendientes() {
        return pendientes.countPending();
    }

    @Override
    public Reporte cachear(int limite) {
        var tanda = pendientes.findPending(Math.max(1, limite));
        int ok = 0;
        for (var p : tanda) {
            // Sin alt útil, el propio servicio pone uno genérico: nunca bloquea el lote.
            if (cache.cacheFromUrl(p.imageUrl(), p.articleId(), p.title()).isPresent()) {
                ok++;
            }
        }
        return new Reporte(tanda.size(), ok, tanda.size() - ok, pendientes.countPending());
    }
}
