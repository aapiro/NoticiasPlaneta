package com.noticiasplaneta.news.domain;

import com.noticiasplaneta.shared.domain.DomainException;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Dominio raíz de una URL.
 *
 * <p>Cuenta dominios, no enlaces: dos URLs de elpais.com suman una sola fuente. Se
 * reconocen los sufijos de dos niveles más habituales (co.uk, com.es, …).
 */
public final class RootDomain {

    private static final Set<String> TWO_LEVEL = Set.of(
            "co.uk", "com.es", "org.es", "gob.es", "edu.es", "net.es",
            "com.ar", "com.mx", "com.co", "com.br", "co.jp", "com.au", "co.nz");

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private RootDomain() {
    }

    public static String of(String url) {
        if (url == null || url.isBlank()) {
            throw new DomainException("SOURCE_URL_INVALID", "La fuente necesita una URL");
        }

        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new DomainException("SOURCE_URL_INVALID", "URL inválida: " + url);
        }

        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new DomainException("SOURCE_URL_INVALID",
                    "La URL debe empezar por http:// o https://");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new DomainException("SOURCE_URL_INVALID", "La URL no tiene dominio: " + url);
        }

        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        if (IPV4.matcher(host).matches() || host.contains(":")) {
            return host;
        }

        String[] parts = host.split("\\.");
        if (parts.length <= 2) {
            return host;
        }

        String lastTwo = parts[parts.length - 2] + "." + parts[parts.length - 1];
        if (TWO_LEVEL.contains(lastTwo)) {
            return parts[parts.length - 3] + "." + lastTwo;
        }
        return lastTwo;
    }
}
