package br.com.tcc.prioriza.servico;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class FuncoesDados {
    private FuncoesDados() { }

    public static String texto(Object valor) {
        if (valor == null) return "";
        if (valor instanceof Double numero && numero == Math.rint(numero)) return String.format(Locale.ROOT, "%.0f", numero);
        return valor.toString().strip();
    }

    public static String normalizarTexto(Object valor) {
        return Normalizer.normalize(texto(valor), Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static Double converterNumero(Object valor) {
        if (texto(valor).isEmpty()) return null;
        try {
            double numero = Double.parseDouble(texto(valor).replace(',', '.'));
            return Double.isFinite(numero) ? numero : null;
        } catch (NumberFormatException erro) { return null; }
    }

    public static LocalDateTime converterData(Object valor) {
        if (valor instanceof LocalDateTime data) return data;
        if (valor == null) return null;
        String textoData = texto(valor);
        for (DateTimeFormatter formato : new DateTimeFormatter[]{DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")}) {
            try { return LocalDateTime.parse(textoData, formato); } catch (DateTimeParseException erro) { /* Tenta o proximo formato do layout. */ }
        }
        return null;
    }

    public static String formatarData(LocalDateTime data) {
        return data == null ? null : data.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }
}
