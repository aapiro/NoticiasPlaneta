package com.noticiasplaneta.generation.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verificación determinista del borrador (F8, paso 4).
 *
 * <p>Es la regla «un dato sin fuente se elimina» escrita en código comprobable: no se
 * pide al modelo que se porte bien, se comprueba lo que ha escrito. Se descarta el
 * párrafo completo, no la frase: recortar una frase deja el resto diciendo otra cosa.
 *
 * <p>Alcance real, sin adornos: comprueba <strong>cifras</strong>, <strong>citas entre
 * comillas</strong> y que cada párrafo <strong>cite fuentes existentes</strong>. No
 * detecta afirmaciones cualitativas sin cifra («la situación es insostenible») ni la
 * omisión selectiva de datos. Por eso la salida es un borrador con revisión humana.
 */
public final class Verificador {

    public static final int MIN_PARRAFO = 80;
    public static final int MAX_PARRAFO = 1500;
    public static final int MAX_TITULAR = 120;

    private static final Pattern NUMERO = Pattern.compile("\\d+(?:[.,]\\d+)*");
    /** Comillas tipográficas, latinas y rectas: el modelo usa las cuatro. */
    private static final Pattern ENTRE_COMILLAS = Pattern.compile("[«\"“]([^»\"”]{3,200})[»\"”]");
    /** Separador de millar: punto o coma entre grupos de tres cifras (1.800, 1,800,000). */
    private static final Pattern MILLAR = Pattern.compile("(?<=\\d)[.,](?=\\d{3}(?:\\D|$))");

    private Verificador() {
    }

    public static BorradorVerificado verificar(BorradorSinVerificar crudo, List<Fragmento> fragmentos) {
        List<Descarte> descartes = new ArrayList<>();
        Map<Integer, Fragmento> porRef = new LinkedHashMap<>();
        if (fragmentos != null) {
            for (Fragmento f : fragmentos) {
                porRef.put(f.ref(), f);
            }
        }
        if (porRef.isEmpty()) {
            return new BorradorVerificado("", List.of(), List.of(new Descarte("sin_fragmentos", "")));
        }

        List<ParrafoVerificado> aceptados = new ArrayList<>();
        for (ParrafoSinVerificar parrafo : crudo.parrafos()) {
            String texto = normalizar(parrafo.texto());
            if (texto.length() < MIN_PARRAFO) {
                descartes.add(new Descarte("parrafo_corto", recorte(texto)));
                continue;
            }
            if (texto.length() > MAX_PARRAFO) {
                descartes.add(new Descarte("parrafo_largo", recorte(texto)));
                continue;
            }
            List<Integer> citas = parrafo.citas().stream()
                    .filter(porRef::containsKey)
                    .distinct()
                    .sorted()
                    .toList();
            if (citas.isEmpty()) {
                descartes.add(new Descarte("sin_citas", recorte(texto)));
                continue;
            }
            String respaldo = respaldo(citas, porRef);
            String cifraSuelta = primerNumeroFuera(texto, numeros(respaldo));
            if (cifraSuelta != null) {
                descartes.add(new Descarte("dato_sin_fuente", cifraSuelta));
                continue;
            }
            String citaSuelta = primeraCitaFuera(texto, respaldo);
            if (citaSuelta != null) {
                descartes.add(new Descarte("cita_textual_sin_fuente", recorte(citaSuelta)));
                continue;
            }
            aceptados.add(new ParrafoVerificado(texto, citas));
        }

        // El titular solo puede apoyarse en lo que sobrevivió: si un párrafo se cayó,
        // sus datos ya no sostienen nada.
        List<Integer> refsAceptadas = aceptados.stream()
                .flatMap(p -> p.citas().stream())
                .distinct()
                .toList();
        String respaldoAceptado = refsAceptadas.isEmpty() ? "" : respaldo(refsAceptadas, porRef);
        String titular = normalizar(crudo.titular());
        String motivo = motivoTitular(titular, respaldoAceptado);
        if (motivo != null) {
            descartes.add(new Descarte(motivo, recorte(titular)));
            titular = "";
        }
        return new BorradorVerificado(titular, List.copyOf(aceptados), List.copyOf(descartes));
    }

    private static String motivoTitular(String titular, String respaldo) {
        if (titular.isBlank()) {
            return "titular_vacio";
        }
        if (titular.length() > MAX_TITULAR) {
            return "titular_largo";
        }
        if (respaldo.isBlank()) {
            return "titular_sin_respaldo";
        }
        String cifra = primerNumeroFuera(titular, numeros(respaldo));
        if (cifra != null) {
            return "titular_con_dato_sin_fuente";
        }
        if (primeraCitaFuera(titular, respaldo) != null) {
            return "titular_con_cita_sin_fuente";
        }
        return null;
    }

    private static String respaldo(List<Integer> citas, Map<Integer, Fragmento> porRef) {
        StringBuilder sb = new StringBuilder();
        for (Integer ref : citas) {
            Fragmento fragmento = porRef.get(ref);
            if (fragmento != null) {
                sb.append(fragmento.material()).append('\n');
            }
        }
        return sb.toString();
    }

    /** Primera cifra del texto que no aparece en el respaldo, o {@code null} si todas aparecen. */
    static String primerNumeroFuera(String texto, Set<String> permitidos) {
        Matcher m = NUMERO.matcher(texto == null ? "" : texto);
        while (m.find()) {
            String bruto = m.group();
            if (!permitidos.contains(canonico(bruto))) {
                return bruto;
            }
        }
        return null;
    }

    /** Primera cita entre comillas que no aparece literalmente en el respaldo. */
    static String primeraCitaFuera(String texto, String respaldo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String normalizado = normalizar(respaldo);
        Matcher m = ENTRE_COMILLAS.matcher(texto);
        while (m.find()) {
            String cita = normalizar(m.group(1));
            if (!cita.isEmpty() && !normalizado.contains(cita)) {
                return m.group(1);
            }
        }
        return null;
    }

    /** Cifras de un texto en forma canónica (miles sin separador, decimal con punto). */
    public static Set<String> numeros(String texto) {
        Set<String> salida = new LinkedHashSet<>();
        Matcher m = NUMERO.matcher(texto == null ? "" : texto);
        while (m.find()) {
            salida.add(canonico(m.group()));
        }
        return salida;
    }

    static String canonico(String bruto) {
        String sinMiles = MILLAR.matcher(bruto).replaceAll("");
        return sinMiles.replace(',', '.');
    }

    /** Espacios colapsados, sin invisibles y sin BOM. */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("\u00ad", "")
                .replace("\u2060", "")
                .replace("\u200b", "")
                .replace("\ufeff", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String recorte(String texto) {
        String limpio = normalizar(texto);
        return limpio.length() <= 120 ? limpio : limpio.substring(0, 120) + "…";
    }
}
