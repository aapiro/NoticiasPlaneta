package com.noticiasplaneta.shared.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Diff por líneas entre dos textos (LCS). Solo separa lo que cambió; no pretende
 * ser un diff de libro, pero dice la verdad sobre qué líneas entraron y salieron.
 */
public final class TextDiff {

    public enum Kind { IGUAL, QUITADA, AÑADIDA }

    public record Line(Kind kind, String text) {
    }

    private TextDiff() {
    }

    public static List<Line> between(String before, String after) {
        String[] a = lineas(before);
        String[] b = lineas(after);

        int[][] lcs = new int[a.length + 1][b.length + 1];
        for (int i = a.length - 1; i >= 0; i--) {
            for (int j = b.length - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j])
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        List<Line> salida = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < a.length && j < b.length) {
            if (a[i].equals(b[j])) {
                salida.add(new Line(Kind.IGUAL, a[i]));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                salida.add(new Line(Kind.QUITADA, a[i]));
                i++;
            } else {
                salida.add(new Line(Kind.AÑADIDA, b[j]));
                j++;
            }
        }
        while (i < a.length) {
            salida.add(new Line(Kind.QUITADA, a[i++]));
        }
        while (j < b.length) {
            salida.add(new Line(Kind.AÑADIDA, b[j++]));
        }
        return salida;
    }

    public static boolean cambia(String before, String after) {
        return !normalizar(before).equals(normalizar(after));
    }

    private static String[] lineas(String texto) {
        return texto == null ? new String[0] : texto.split("\\R", -1);
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
