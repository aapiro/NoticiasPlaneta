package com.noticiasplaneta.news.application.port.out;

import java.time.Instant;

/** Comprueba si una URL responde. */
public interface UrlChecker {

    Check check(String url);

    record Check(int status, boolean reachable, Instant checkedAt) {
    }
}
