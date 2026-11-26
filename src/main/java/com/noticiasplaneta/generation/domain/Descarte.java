package com.noticiasplaneta.generation.domain;

/**
 * Material descartado y por qué. Se guarda con la pieza: si algo no llegó al lector,
 * tiene que poder explicarse.
 */
public record Descarte(String motivo, String detalle) {
}
