package com.noticiasplaneta.ingest.application.service;

import com.noticiasplaneta.ingest.application.port.in.IngestMetricsUseCase;
import com.noticiasplaneta.ingest.application.port.out.IngestMetricsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IngestMetricsService implements IngestMetricsUseCase {

    private final IngestMetricsRepository metricas;

    IngestMetricsService(IngestMetricsRepository metricas) {
        this.metricas = metricas;
    }

    @Override
    @Transactional(readOnly = true)
    public Informe informe(int dias) {
        int ventana = Math.clamp(dias, 1, 90);
        return new Informe(metricas.totales(), metricas.porFuente(), metricas.ultimosDias(ventana));
    }
}
