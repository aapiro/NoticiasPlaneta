package com.noticiasplaneta.news.domain;

/** Nivel de contraste derivado del número de dominios independientes. */
public enum ContrastLevel {
    LOW,
    MEDIUM,
    HIGH;

    public static ContrastLevel from(int independentSources) {
        if (independentSources >= 4) {
            return HIGH;
        }
        if (independentSources >= 2) {
            return MEDIUM;
        }
        return LOW;
    }
}
