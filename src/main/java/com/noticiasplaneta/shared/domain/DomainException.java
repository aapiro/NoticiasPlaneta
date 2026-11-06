package com.noticiasplaneta.shared.domain;

/** Error de negocio con código estable. Nunca se traduce en el dominio. */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
