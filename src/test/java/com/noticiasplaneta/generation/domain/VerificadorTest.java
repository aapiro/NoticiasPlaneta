package com.noticiasplaneta.generation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("F8 · Verificación: solo lo que está en los fragmentos")
class VerificadorTest {

    private static final Fragmento SAU = new Fragmento(1, "Diario A",
            "https://a.example/embalses",
            Instant.parse("2026-11-25T08:00:00Z"),
            "El embalse de Sau baja al 18 %",
            "La capacidad del embalse de Sau ha bajado al 18 % este mes, según la agencia catalana "
                    + "del agua. La situación afecta al abastecimiento de 1.800 vecinos de la comarca.");

    private static final Fragmento GOVERN = new Fragmento(2, "Diario B",
            "https://b.example/sequia",
            Instant.parse("2026-11-25T09:30:00Z"),
            "El Govern descarta cortes de agua por ahora",
            "El Govern estudió ayer restricciones y descartó cortes de agua por ahora, con las "
                    + "reservas de las cuencas internas al 19 %. La portavoz dijo que \"no habrá "
                    + "cortes este invierno\".");

    private static final List<Fragmento> FRAGMENTOS = List.of(SAU, GOVERN);

    private static String parrafo(String extra) {
        return "El embalse de Sau ha bajado al 18 % de su capacidad, según los datos de la agencia "
                + "catalana del agua, y la situación afecta al abastecimiento de la comarca. " + extra;
    }

    /** Párrafo apoyado en la fuente 2: sus cifras son las de esa fuente, no las de la 1. */
    private static String parrafoGovern(String extra) {
        return "El Govern descartó cortes de agua por ahora, con las reservas de las cuencas internas "
                + "al 19 %, según el medio citado, y la decisión se revisará en las próximas semanas. "
                + extra;
    }

    @Test
    @DisplayName("Acepta un párrafo cuyas cifras están en la fuente que cita")
    void aceptaParrafoRespaldado() {
        BorradorVerificado v = Verificador.verificar(new BorradorSinVerificar(
                "La sequía deja Sau al 18 % de su capacidad",
                List.of(new ParrafoSinVerificar(parrafo("Los vecinos lo notan."), List.of(1)))), FRAGMENTOS);

        assertThat(v.parrafos()).hasSize(1);
        assertThat(v.parrafos().get(0).citas()).containsExactly(1);
        assertThat(v.titular()).isEqualTo("La sequía deja Sau al 18 % de su capacidad");
        assertThat(v.descartes()).isEmpty();
    }

    @Test
    @DisplayName("Descarta el párrafo si una cifra no está en la fuente citada")
    void descartaCifraInventada() {
        String conCifraFalsa = parrafo("El pantano ha perdido 2,3 hectómetros cúbicos.");

        BorradorVerificado v = Verificador.verificar(new BorradorSinVerificar("Titular sin cifras",
                List.of(new ParrafoSinVerificar(conCifraFalsa, List.of(1)))), FRAGMENTOS);

        assertThat(v.parrafos()).isEmpty();
        assertThat(v.descartes()).anySatisfy(d -> {
            assertThat(d.motivo()).isEqualTo("dato_sin_fuente");
            assertThat(d.detalle()).isEqualTo("2,3");
        });
    }

    @Test
    @DisplayName("Una fuente citada no puede respaldar datos de otra que no se ha citado")
    void noAceptaDatosDeOtraFuente() {
        String conDatoAjeno = parrafo("Las cuencas internas están al 19 %.");

        BorradorVerificado v = Verificador.verificar(new BorradorSinVerificar("Titular sin cifras",
                List.of(new ParrafoSinVerificar(conDatoAjeno, List.of(1)))), FRAGMENTOS);

        assertThat(v.parrafos()).isEmpty();
        assertThat(v.descartes()).extracting(Descarte::motivo).contains("dato_sin_fuente");
    }

    @Test
    @DisplayName("Descarta un párrafo sin citas: no se le asignan fuentes a posteriori")
    void descartaParrafoSinCitas() {
        BorradorVerificado v = Verificador.verificar(new BorradorSinVerificar("Titular sin cifras",
                List.of(new ParrafoSinVerificar(parrafo(""), List.of()))), FRAGMENTOS);

        assertThat(v.parrafos()).isEmpty();
        assertThat(v.descartes()).extracting(Descarte::motivo).contains("sin_citas");
    }

    @Test
    @DisplayName("Descarta una cita entre comillas que no es literal")
    void descartaCitaNoLiteral() {
        String conCitaFalsa = parrafoGovern("La portavoz dijo que \"el invierno será muy duro\".");

        BorradorVerificado v = Verificador.verificar(new BorradorSinVerificar("Titular sin cifras",
                List.of(new ParrafoSinVerificar(conCitaFalsa, List.of(2)))), FRAGMENTOS);

        assertThat(v.parrafos()).isEmpty();
        assertThat(v.descartes()).extracting(Descarte::motivo).contains("cita_textual_sin_fuente");
    }

    @Test
    @DisplayName("Acepta una cita literal del fragmento citado")
    void aceptaCitaLiteral() {
        String conCitaBuena = parrafoGovern("La portavoz dijo que \"no habrá cortes este invierno\".");

        BorradorVerificado v = Verificador.verificar(new BorradorSinVerificar("Titular sin cifras",
                List.of(new ParrafoSinVerificar(conCitaBuena, List.of(2)))), FRAGMENTOS);

        assertThat(v.parrafos()).hasSize(1);
    }

    @Test
    @DisplayName("El titular con una cifra inventada no se recorta: se descarta")
    void descartaTitularConCifraInventada() {
        BorradorVerificado v = Verificador.verificar(new BorradorSinVerificar(
                "La sequía deja los embalses al 12 %",
                List.of(new ParrafoSinVerificar(parrafo(""), List.of(1)))), FRAGMENTOS);

        assertThat(v.titular()).isEmpty();
        assertThat(v.descartes()).extracting(Descarte::motivo).contains("titular_con_dato_sin_fuente");
    }

    @Test
    @DisplayName("Si todos los párrafos se caen, el titular se queda sin respaldo")
    void titularSinParrafosAceptadosNoVale() {
        String inventado = parrafo("El pantano ha perdido 47 hectómetros cúbicos.");

        BorradorVerificado v = Verificador.verificar(new BorradorSinVerificar(
                "Sau pierde 47 hectómetros cúbicos",
                List.of(new ParrafoSinVerificar(inventado, List.of(1)))), FRAGMENTOS);

        assertThat(v.titular()).isEmpty();
        assertThat(v.parrafos()).isEmpty();
        assertThat(v.descartes()).extracting(Descarte::motivo)
                .contains("dato_sin_fuente", "titular_sin_respaldo");
    }

    @Test
    @DisplayName("Los separadores de millar no convierten una cifra buena en inventada")
    void normalizaSeparadores() {
        assertThat(Verificador.numeros("1.800 vecinos")).containsExactly("1800");
        assertThat(Verificador.numeros("1800 vecinos")).containsExactly("1800");
        assertThat(Verificador.numeros("2,29 millones")).containsExactly("2.29");
        assertThat(Verificador.numeros("1.800.000 de euros")).containsExactly("1800000");
        assertThat(Verificador.numeros("18 %")).containsExactly("18");
    }

    @Test
    @DisplayName("Un párrafo demasiado corto no es una pieza: se descarta")
    void descartaParrafoCorto() {
        BorradorVerificado v = Verificador.verificar(new BorradorSinVerificar("Titular",
                List.of(new ParrafoSinVerificar("Sau al 18 %.", List.of(1)))), FRAGMENTOS);

        assertThat(v.parrafos()).isEmpty();
        assertThat(v.descartes()).extracting(Descarte::motivo).contains("parrafo_corto");
    }

    @Test
    @DisplayName("Una cita con referencias inexistentes se descarta")
    void descartaCitasFueraDeRango() {
        BorradorVerificado v = Verificador.verificar(new BorradorSinVerificar("Titular sin cifras",
                List.of(new ParrafoSinVerificar(parrafo(""), List.of(9)))), FRAGMENTOS);

        assertThat(v.parrafos()).isEmpty();
        assertThat(v.descartes()).extracting(Descarte::motivo).contains("sin_citas");
    }
}
