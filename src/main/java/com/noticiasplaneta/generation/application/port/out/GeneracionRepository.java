package com.noticiasplaneta.generation.application.port.out;

import com.noticiasplaneta.generation.domain.Descarte;
import com.noticiasplaneta.generation.domain.Fragmento;
import com.noticiasplaneta.generation.domain.ParrafoVerificado;

import java.util.List;
import java.util.Optional;

/** Trazabilidad de la generación: qué material, qué prompt y qué párrafos sostienen la pieza (F8). */
public interface GeneracionRepository {

    void save(Registro registro);

    Optional<Registro> findByArticleId(String articleId);

    record Registro(
            String articleId,
            String consulta,
            String seccion,
            String enfoque,
            String promptVersion,
            String modelo,
            String proveedor,
            long costeMicros,
            int fuentesIndependientes,
            List<Fragmento> fragmentos,
            List<ParrafoVerificado> parrafos,
            List<Descarte> descartes) {

        public Registro {
            fragmentos = fragmentos == null ? List.of() : List.copyOf(fragmentos);
            parrafos = parrafos == null ? List.of() : List.copyOf(parrafos);
            descartes = descartes == null ? List.of() : List.copyOf(descartes);
        }
    }
}
