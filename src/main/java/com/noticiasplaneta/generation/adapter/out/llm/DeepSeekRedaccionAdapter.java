package com.noticiasplaneta.generation.adapter.out.llm;

import com.noticiasplaneta.generation.application.port.out.RedaccionPort;
import com.noticiasplaneta.generation.domain.BorradorSinVerificar;
import com.noticiasplaneta.generation.domain.ParrafoSinVerificar;
import com.noticiasplaneta.shared.domain.DomainException;
import com.noticiasplaneta.shared.observability.LangfuseTracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redacción con DeepSeek (API compatible con OpenAI), F8.
 *
 * <p>El material viaja numerado y delimitado como DATOS: es texto de terceros y no debe
 * interpretarse como instrucciones (misma precaución que en el enriquecido). Se pide JSON
 * con el titular y los párrafos, cada uno con las referencias que lo sostienen; lo que
 * diga se verifica después en código, no se cree por fe.
 */
@Component
@Conditional(RedaccionConfigurada.class)
public class DeepSeekRedaccionAdapter implements RedaccionPort {

    public static final String PROMPT_VERSION = "generar-v1";
    private static final String MODEL = "deepseek-flash";
    private static final String ENDPOINT = "https://api.deepseek.com/chat/completions";
    /** Tarifa peak de DeepSeek (USD por millón de tokens), verificada el 26/11/2026. */
    private static final double USD_MILLON_ENTRADA = 0.30;
    private static final double USD_MILLON_SALIDA = 1.20;
    private static final List<String> TAGS = List.of("generation", "deepseek", "f8");

    private static final String SYSTEM = """
            Eres redactor de «Noticias Planeta». Recibes un tema y un material formado por
            fragmentos numerados de otros medios. Escribes en español de España, tercera persona,
            tono informativo, sin opinión y sin adjetivos de valoración.

            REGLAS DURAS (el sistema descarta lo que las incumpla):
            1. Solo puedes afirmar hechos, cifras, fechas y nombres que aparezcan en el material.
               Prohibido usar conocimiento previo, aunque lo tengas.
            2. Cada párrafo debe declarar en "fuentes" los números de los fragmentos que lo
               sostienen. Un párrafo sin fuentes se descarta.
            3. Toda cifra, año o porcentaje del párrafo tiene que aparecer literalmente en un
               fragmento que cites. Si no está, no la escribas.
            4. Las comillas solo para frases literales del material, copiadas tal cual.
            5. Prohibido inventar contexto, antecedentes, reacciones o declaraciones que no
               estén en el material.
            6. Aplica el enfoque editorial de la sección al ángulo y al titular, pero no omitas
               ni suavices ningún dato del material aunque contradiga ese ángulo.
            7. Titular de 40 a 90 caracteres, sin comillas, sin punto final, sin mayúsculas
               sostenidas ni clickbait.
            8. Entre 3 y 5 párrafos de 300 a 700 caracteres, texto plano en "texto" (sin markdown
               y sin numerar las fuentes dentro del párrafo).
            9. Si el material no permite escribir algo cierto sobre el tema, devuelve
               {"titular": "", "parrafos": []}. Es la respuesta correcta, no un fallo.

            Responde SOLO con json válido con esta forma exacta:
            {"titular": "...", "parrafos": [{"texto": "...", "fuentes": [1, 2]}]}
            """;

    private final String apiKey;
    private final ObjectMapper json;
    private final HttpClient client;
    private final LangfuseTracer tracer;
    private final String endpoint;

    @Autowired
    public DeepSeekRedaccionAdapter(@Value("${ai.deepseek.api-key}") String apiKey,
                                    ObjectMapper json, LangfuseTracer tracer) {
        this(apiKey, json, tracer, ENDPOINT);
    }

    DeepSeekRedaccionAdapter(String apiKey, ObjectMapper json, LangfuseTracer tracer, String endpoint) {
        this.apiKey = apiKey;
        this.json = json;
        this.tracer = tracer;
        this.endpoint = endpoint;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public Resultado redactar(Peticion peticion) {
        String payload = buildPayload(peticion);
        Map<String, String> meta = Map.of(
                "provider", "deepseek",
                "prompt_version", PROMPT_VERSION,
                "section", safe(peticion.seccion()),
                "sources", String.valueOf(peticion.fuentes().size()));
        String error = null;

        try (LangfuseTracer.Trace trace = tracer.startTrace("generar-borrador",
                json.writeValueAsString(Map.of("tema", safe(peticion.tema()))), meta, TAGS)) {
            try (LangfuseTracer.Generation generation = tracer.startGeneration(
                    "generar-borrador", "generate-draft", MODEL, messagesJson(payload), meta, TAGS)) {
                generation.modelParameters("{\"temperature\":0.2,\"response_format\":\"json_object\"}");
                generation.prompt("generate-draft", PROMPT_VERSION);
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                            .timeout(Duration.ofSeconds(120))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey)
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();
                    HttpResponse<String> response = client.send(request,
                            HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 400) {
                        throw new DomainException("AI_PROVIDER_ERROR",
                                "DeepSeek respondió " + response.statusCode());
                    }
                    Resultado resultado = parse(response.body());
                    generation.usage(inputTokens(response.body()), outputTokens(response.body()));
                    generation.output(resumenTrazado(resultado.borrador()));
                    trace.output(resumenTrazado(resultado.borrador()));
                    return resultado;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    error = "Llamada interrumpida";
                    throw new DomainException("AI_PROVIDER_ERROR", error);
                } catch (DomainException e) {
                    error = e.getMessage();
                    throw e;
                } catch (Exception e) {
                    error = "Fallo llamando a DeepSeek: " + e.getMessage();
                    throw new DomainException("AI_PROVIDER_ERROR", error);
                } finally {
                    if (error != null) {
                        generation.error(error);
                        trace.error(error);
                    }
                }
            }
        }
    }

    private String buildPayload(Peticion peticion) {
        var root = json.createObjectNode();
        root.put("model", MODEL);
        root.put("temperature", 0.2);
        root.put("response_format", "json_object");
        root.put("max_tokens", 2000);
        var messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM);

        StringBuilder user = new StringBuilder();
        user.append("<tema>").append(safe(peticion.tema())).append("</tema>\n");
        user.append("<seccion slug=\"").append(safe(peticion.seccion()))
                .append("\" enfoque=\"").append(safe(peticion.enfoque())).append("\">\n")
                .append(safe(peticion.instruccionEnfoque())).append("\n</seccion>\n");
        user.append("<material>\n");
        for (Fuente fuente : peticion.fuentes()) {
            user.append('[').append(fuente.ref()).append("] ").append(safe(fuente.medio()))
                    .append(" · ").append(safe(fuente.fecha()))
                    .append(" · ").append(safe(fuente.url())).append('\n')
                    .append("TITULAR: ").append(safe(fuente.titular())).append('\n')
                    .append("TEXTO: ").append(safe(fuente.texto())).append("\n\n");
        }
        user.append("</material>\n");
        user.append("Devuelve SOLO el json con el titular y los párrafos citando el material.");
        messages.addObject().put("role", "user").put("content", user.toString());
        return json.writeValueAsString(root);
    }

    private Resultado parse(String responseBody) {
        JsonNode root = json.readTree(responseBody);
        String content = root.path("choices").path(0).path("message").path("content").asString();
        if (content == null || content.isBlank()) {
            throw new DomainException("AI_PROVIDER_ERROR", "respuesta vacía del modelo");
        }
        JsonNode node = json.readTree(extractJson(content));
        String titular = node.path("titular").asString("");

        List<ParrafoSinVerificar> parrafos = new ArrayList<>();
        for (JsonNode parrafo : node.path("parrafos")) {
            List<Integer> citas = new ArrayList<>();
            for (JsonNode ref : parrafo.path("fuentes")) {
                citas.add(ref.asInt());
            }
            parrafos.add(new ParrafoSinVerificar(parrafo.path("texto").asString(""), citas));
        }
        long costeMicros = (long) Math.ceil(USD_MILLON_ENTRADA * inputTokens(responseBody)
                + USD_MILLON_SALIDA * outputTokens(responseBody));
        return new Resultado(new BorradorSinVerificar(titular, parrafos), MODEL, PROMPT_VERSION,
                "deepseek", costeMicros);
    }

    private long inputTokens(String body) {
        return usage(body).path("prompt_tokens").asLong(0);
    }

    private long outputTokens(String body) {
        return usage(body).path("completion_tokens").asLong(0);
    }

    private JsonNode usage(String body) {
        try {
            return json.readTree(body).path("usage");
        } catch (RuntimeException e) {
            return json.createObjectNode();
        }
    }

    private String messagesJson(String payload) {
        try {
            return json.writeValueAsString(json.readTree(payload).path("messages"));
        } catch (RuntimeException e) {
            return "[]";
        }
    }

    private String resumenTrazado(BorradorSinVerificar borrador) {
        try {
            var node = json.createObjectNode();
            node.put("titular", borrador.titular());
            var parrafos = node.putArray("parrafos");
            for (ParrafoSinVerificar parrafo : borrador.parrafos()) {
                var p = parrafos.addObject();
                p.put("texto", parrafo.texto());
                var refs = p.putArray("fuentes");
                parrafo.citas().forEach(refs::add);
            }
            return json.writeValueAsString(node);
        } catch (RuntimeException e) {
            return "{}";
        }
    }

    /** El modelo a veces envuelve el JSON en markdown; se recorta. */
    static String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return start >= 0 && end > start ? raw.substring(start, end + 1) : raw;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
