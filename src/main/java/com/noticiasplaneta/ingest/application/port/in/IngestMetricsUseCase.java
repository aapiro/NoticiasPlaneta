package com.noticiasplaneta.ingest.application.port.in;

import com.noticiasplaneta.ingest.application.port.out.IngestMetricsRepository;

import java.util.List;

/** Números de la ingesta para el panel (backlog #10). */
public interface IngestMetricsUseCase {

    Informe informe(int dias);

    record Informe(IngestMetricsRepository.Totales totales,
                   List<IngestMetricsRepository.PorFuente> porFuente,
                   List<IngestMetricsRepository.Dia> dias) {

        public List<IngestMetricsRepository.PorFuente> ruidosas() {
            return porFuente.stream().filter(IngestMetricsRepository.PorFuente::ruidosa).toList();
        }
    }
}
