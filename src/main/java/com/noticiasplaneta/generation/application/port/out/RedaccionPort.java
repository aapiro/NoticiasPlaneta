package com.noticiasplaneta.generation.application.port.out;

import com.noticiasplaneta.generation.domain.BorradorSinVerificar;

import java.util.List;

/**
 * Redacción del borrador a partir del material recopilado.
 *
 * <p>El adaptador no decide nada editorial: recibe fragmentos numerados, devuelve un
 * borrador con sus citas y se limita a lo que diga el material. La verificación es
 * posterior y determinista ({@code Verificador}), no se delega en el proveedor.
 */
public interface RedaccionPort {

    Resultado redactar(Peticion peticion);

    record Peticion(String tema, String seccion, String enfoque, String instruccionEnfoque,
                    List<Fuente> fuentes) {

        public Peticion {
            fuentes = fuentes == null ? List.of() : List.copyOf(fuentes);
        }
    }

    /** Un fragmento tal y como viaja al modelo: numerado, con medio, URL y fecha. */
    record Fuente(int ref, String medio, String url, String fecha, String titular, String texto) {
    }

    record Resultado(BorradorSinVerificar borrador, String modelo, String promptVersion,
                     String proveedor, long costeMicros) {
    }
}
