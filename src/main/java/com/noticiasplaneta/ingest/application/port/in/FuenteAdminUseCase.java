package com.noticiasplaneta.ingest.application.port.in;

import java.time.Instant;
import java.util.List;

/**
 * Alta y estado de las fuentes desde el panel (backlog #4).
 *
 * <p>Hasta ahora las ocho fuentes solo se podían tocar por SQL: añadir un medio o
 * parar uno que se rompe dependía de mí. La confianza va por {@code SourceTrustUseCase};
 * aquí no se duplica.
 */
public interface FuenteAdminUseCase {

    List<Fuente> fuentes();

    /** @return id de la fuente creada */
    String crear(String nombre, String feedUrl);

    /** @return {@code false} si la fuente no existe */
    boolean activar(String id);

    boolean desactivar(String id);

    record Fuente(String id, String nombre, String url, String estado, boolean confianza,
                  int fallos, Instant ultima) {

        /** Una fuente degradada se sigue leyendo: el estado lo dice, no lo esconde. */
        public boolean activa() {
            return !"DISABLED".equals(estado);
        }

        public boolean degradada() {
            return "DEGRADED".equals(estado);
        }
    }
}
