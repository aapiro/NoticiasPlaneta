package com.noticiasplaneta.ingest.domain;

import com.noticiasplaneta.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * Fuente de noticias.
 *
 * <p>RN1 (F01): una fuente que falla {@value #DEGRADE_THRESHOLD} veces seguidas pasa a
 * DEGRADED y genera un aviso. Nunca se borra ni se desactiva sola.
 */
public final class Source {

    public static final int DEGRADE_THRESHOLD = 5;

    private final SourceId id;
    private String name;
    private String feedUrl;
    private SourceState state;
    private int failureCount;
    private Instant lastCheckedAt;
    private String etag;
    private String lastModified;

    private Source(SourceId id, String name, String feedUrl, SourceState state,
                   int failureCount, Instant lastCheckedAt, String etag, String lastModified) {
        this.id = id;
        this.name = name;
        this.feedUrl = feedUrl;
        this.state = state;
        this.failureCount = failureCount;
        this.lastCheckedAt = lastCheckedAt;
        this.etag = etag;
        this.lastModified = lastModified;
    }

    public static Source register(String name, String feedUrl) {
        validarNombre(name);
        validarUrl(feedUrl);
        return new Source(SourceId.newId(), name.trim(), feedUrl.trim(), SourceState.ACTIVE, 0, null, null, null);
    }

    /**
     * Cambia el nombre y la URL del feed.
     *
     * <p>Si la URL cambia, se tiran los validadores HTTP del feed anterior. Sin eso el
     * servidor nuevo contesta 304 a un {@code If-None-Match} que no es suyo y la fuente
     * se queda en «no ha cambiado» para siempre: parece sana y no ingiere nada.
     */
    public void editar(String nuevoNombre, String nuevaFeedUrl) {
        validarNombre(nuevoNombre);
        validarUrl(nuevaFeedUrl);
        boolean cambiaUrl = !this.feedUrl.equals(nuevaFeedUrl.trim());
        this.name = nuevoNombre.trim();
        this.feedUrl = nuevaFeedUrl.trim();
        if (cambiaUrl) {
            this.etag = null;
            this.lastModified = null;
            this.failureCount = 0;
            if (this.state == SourceState.DEGRADED) {
                this.state = SourceState.ACTIVE;
            }
        }
    }

    private static void validarNombre(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException("SOURCE_NAME_REQUIRED", "La fuente necesita un nombre");
        }
    }

    private static void validarUrl(String feedUrl) {
        if (feedUrl == null || !(feedUrl.startsWith("http://") || feedUrl.startsWith("https://"))) {
            throw new DomainException("SOURCE_URL_INVALID", "La URL del feed no es válida: " + feedUrl);
        }
    }

    public static Source rehydrate(SourceId id, String name, String feedUrl, SourceState state,
                                   int failureCount, Instant lastCheckedAt, String etag, String lastModified) {
        return new Source(id, name, feedUrl, state, failureCount, lastCheckedAt, etag, lastModified);
    }

    /**
     * Registra una lectura correcta: resetea el contador de fallos y conserva los validadores HTTP.
     *
     * @return {@code true} si la fuente sale de DEGRADED.
     */
    public boolean recordSuccess(Instant at, String newEtag, String newLastModified) {
        this.lastCheckedAt = Objects.requireNonNull(at, "at");
        this.etag = newEtag;
        this.lastModified = newLastModified;
        boolean recovered = this.state == SourceState.DEGRADED;
        this.failureCount = 0;
        if (this.state == SourceState.DEGRADED) {
            this.state = SourceState.ACTIVE;
        }
        return recovered;
    }

    /**
     * Registra un fallo de lectura.
     *
     * @return {@code true} si esta llamada ha provocado la degradación (para emitir aviso).
     */
    public boolean recordFailure(Instant at) {
        this.lastCheckedAt = Objects.requireNonNull(at, "at");
        this.failureCount++;
        if (failureCount >= DEGRADE_THRESHOLD && state == SourceState.ACTIVE) {
            this.state = SourceState.DEGRADED;
            return true;
        }
        return false;
    }

    public void disable() {
        this.state = SourceState.DISABLED;
    }

    /**
     * Reactiva a mano una fuente desactivada.
     *
     * <p>Los fallos acumulados se ponen a cero: si no, una fuente recién reactivada
     * podría volver a DEGRADED con un solo error más.
     */
    public void enable() {
        if (this.state == SourceState.DISABLED) {
            this.state = SourceState.ACTIVE;
            this.failureCount = 0;
        }
    }

    public boolean isFetchable() {
        return state != SourceState.DISABLED;
    }

    public SourceId id() { return id; }
    public String name() { return name; }
    public String feedUrl() { return feedUrl; }
    public SourceState state() { return state; }
    public int failureCount() { return failureCount; }
    public Instant lastCheckedAt() { return lastCheckedAt; }
    public String etag() { return etag; }
    public String lastModified() { return lastModified; }
}
