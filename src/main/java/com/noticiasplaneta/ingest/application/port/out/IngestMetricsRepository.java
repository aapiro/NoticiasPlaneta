package com.noticiasplaneta.ingest.application.port.out;

import java.time.Instant;
import java.util.List;

/**
 * Números de la ingesta (backlog #10).
 *
 * <p>Sin esto, la única forma de saber qué fuente rinde era abrir psql. Y la pregunta
 * que importa no es cuántas noticias hay, sino cuántas de las que trae cada medio
 * acaban publicadas y cuántas solo ocupan sitio.
 */
public interface IngestMetricsRepository {

    Totales totales();

    List<PorFuente> porFuente();

    /** Actividad de los últimos días, del más antiguo al más reciente. */
    List<Dia> ultimosDias(int dias);

    record Totales(long articulos, long publicados, long borradores, long piezas,
                   long fuentes, long fuentesActivas, long medios) {

        /** Publicado sobre total, en porcentaje entero. */
        public int porcentajePublicado() {
            return articulos == 0 ? 0 : (int) Math.round(publicados * 100.0 / articulos);
        }
    }

    record PorFuente(String nombre, String estado, long piezas, long publicadas,
                     long borradores, Instant ultimaLectura, int fallos) {

        /** Qué parte de lo que trajo este medio ha llegado al portal. */
        public int porcentajePublicado() {
            return piezas == 0 ? 0 : (int) Math.round(publicadas * 100.0 / piezas);
        }

        /** Trae mucho y no se publica casi nada: candidata a sobra. */
        public boolean ruidosa() {
            return piezas >= 50 && porcentajePublicado() <= 5;
        }
    }

    record Dia(String fecha, long piezas, long publicadas) {
    }
}
