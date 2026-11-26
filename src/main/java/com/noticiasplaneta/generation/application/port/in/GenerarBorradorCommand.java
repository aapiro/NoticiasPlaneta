package com.noticiasplaneta.generation.application.port.in;

/**
 * Entrada del generador: un tema o titular y la sección donde encaja (F8).
 *
 * <p>{@code permitirAutoPublicacion} es explícito y no un detalle del llamante: el panel
 * lo pone a {@code true} (un humano está delante) y el MCP a {@code false}, porque un
 * agente nunca publica (RN5 de F05). Aun así, la sección tiene que estar en
 * {@code auto_publica} y la capacidad {@code PUBLISH_AUTO} encendida.
 */
public record GenerarBorradorCommand(String tema, String seccion, String actorId, String tipoActor,
                                     boolean permitirAutoPublicacion) {

    public static GenerarBorradorCommand humano(String tema, String seccion, String actorId) {
        return new GenerarBorradorCommand(tema, seccion, actorId, "HUMAN", true);
    }

    public static GenerarBorradorCommand agente(String tema, String seccion, String actorId) {
        return new GenerarBorradorCommand(tema, seccion, actorId, "AGENT", false);
    }
}
