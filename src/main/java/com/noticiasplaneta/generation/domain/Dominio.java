package com.noticiasplaneta.generation.domain;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Dominio raíz de una URL, para contar fuentes independientes (F8).
 *
 * <p>Misma semántica que {@code news.domain.RootDomain}, duplicada a propósito: el
 * módulo {@code generation} no puede depender de los internals de {@code news}
 * (regla de arquitectura) y esta cuenta es de dominio, no de infraestructura.
 * Si cambia el catálogo de sufijos de dos niveles, hay que cambiar los dos.
 */
public final class Dominio {

    private static final Set<String> DOS_NIVELES = Set.of(
            "co.uk", "com.es", "org.es", "gob.es", "edu.es", "net.es",
            "com.ar", "com.mx", "com.co", "com.br", "co.jp", "com.au", "co.nz");

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private Dominio() {
    }

    /** Dominio raíz, o cadena vacía si la URL no sirve. Nunca lanza: un fragmento roto no aborta la pieza. */
    public static String raiz(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return "";
        }
        String esquema = uri.getScheme();
        if (esquema == null
                || (!esquema.equalsIgnoreCase("http") && !esquema.equalsIgnoreCase("https"))) {
            return "";
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "";
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        if (IPV4.matcher(host).matches() || host.contains(":")) {
            return host;
        }
        String[] partes = host.split("\\.");
        if (partes.length <= 2) {
            return host;
        }
        String dos = partes[partes.length - 2] + "." + partes[partes.length - 1];
        if (DOS_NIVELES.contains(dos)) {
            return partes[partes.length - 3] + "." + dos;
        }
        return dos;
    }
}
