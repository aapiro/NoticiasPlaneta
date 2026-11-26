package com.noticiasplaneta.generation.domain;

import java.time.Instant;

/**
 * Fragmento de una fuente: lo que el modelo tiene permitido afirmar (F8, paso 2).
 *
 * @param ref     número con el que el modelo cita este fragmento (1..n)
 * @param medio   nombre del medio de origen
 * @param url     URL canónica del fragmento
 * @param fecha   fecha de publicación según la fuente ({@code null} si no la trae)
 * @param titular titular original
 * @param texto   cuerpo recortado del fragmento
 */
public record Fragmento(int ref, String medio, String url, Instant fecha, String titular, String texto) {

    public Fragmento {
        if (ref < 1) {
            throw new IllegalArgumentException("La referencia de un fragmento empieza en 1");
        }
        url = url == null ? "" : url.trim();
        medio = medio == null || medio.isBlank() ? Dominio.raiz(url) : medio.trim();
        titular = titular == null ? "" : titular.trim();
        texto = texto == null ? "" : texto.trim();
    }

    public String dominio() {
        return Dominio.raiz(url);
    }

    /** Texto contra el que se comprueba que una cifra o una cita está respaldada. */
    public String material() {
        return titular + "\n" + texto;
    }
}
