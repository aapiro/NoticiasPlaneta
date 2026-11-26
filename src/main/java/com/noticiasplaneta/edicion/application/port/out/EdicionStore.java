package com.noticiasplaneta.edicion.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia y consultas de la edición.
 *
 * <p>Lee {@code article}, {@code ingest_item} y {@code section_config} porque el informe de
 * una edición es exactamente la foto de esas tablas en ese instante. Ninguna escritura
 * editorial pasa por aquí: para publicar se usa la máquina de estados de {@code news}.
 */
public interface EdicionStore {

    /**
     * Abre una ejecución con estado EN_CURSO. El índice único parcial de la tabla impide dos
     * a la vez, aunque sean dos instancias de la aplicación.
     *
     * @return el identificador de la ejecución abierta, o vacío si ya había una en curso
     */
    Optional<UUID> abrir(UUID id, Instant inicio, String actor, String modo);

    void cerrar(Cierre cierre);

    boolean enCurso();

    Optional<EjecucionFila> ejecucion(UUID id);

    List<EjecucionFila> ejecuciones(int limite);

    List<PiezaFila> piezas(UUID ejecucionId);

    void guardarPiezas(UUID ejecucionId, List<PiezaFila> piezas);

    /** Borradores nacidos desde {@code desde}: los que crea la ingesta, no esta ejecución. */
    int borradoresCreadosDesde(Instant desde);

    /** Borradores seleccionables: con foto, ventana, sección activa y cupo por sección/fuente. */
    List<Candidato> candidatos(Instant desde, int limite, int maxPorFuente);

    /** Piezas publicadas desde {@code desde} agrupadas por {@code section_id}. */
    Map<String, Integer> piezasPublicadasPorSeccion(Instant desde);

    long totalPublicadas();

    record Cierre(UUID id, Instant fin, String estado, int fuentes, int fuentesConError,
                  int creadas, int borradores, int publicadas, int errores, String detalle) {
    }

    record EjecucionFila(UUID id, Instant inicio, Instant fin, String actor, String modo,
                         String estado, int fuentes, int fuentesConError, int creadas,
                         int borradores, int publicadas, int errores, String detalle) {
    }

    record PiezaFila(String articleId, String sectionId, String titulo) {
    }

    record Candidato(String articleId, String titulo, String sectionId) {
    }
}
