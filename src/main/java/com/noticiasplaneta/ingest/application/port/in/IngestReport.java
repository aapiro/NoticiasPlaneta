package com.noticiasplaneta.ingest.application.port.in;

/** Resumen de una ejecución de ingesta sobre una fuente. */
public record IngestReport(
        int fetched,
        int created,
        int duplicated,
        int rejected,
        boolean notModified,
        String error) {

    public static IngestReport error(String reason) {
        return new IngestReport(0, 0, 0, 0, false, reason);
    }

    public static IngestReport ofNotModified() {
        return new IngestReport(0, 0, 0, 0, true, null);
    }
}
