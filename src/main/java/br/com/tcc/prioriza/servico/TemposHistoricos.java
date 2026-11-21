package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Modelos.*;
import java.util.*;
import java.util.regex.Pattern;
import static br.com.tcc.prioriza.servico.FuncoesDados.*;

public final class TemposHistoricos {
    private TemposHistoricos() { }
    private static final Pattern PADRAO_TEMPO = Pattern.compile("\\s*(?:(\\d+)\\s*/\\s*)?(\\d+):(\\d{2})\\s*");
    private static final Pattern PADRAO_MUNICIPIO = Pattern.compile("^\\s*(.+?)\\s*[-/]\\s*([A-Za-z]{2})(?:\\s*\\(.*)?\\s*$");
    private record Chave(String tipo, String cliente, String cidade) { }
    private record Amostra(String cliente, String cidade, Double horas) { }

    public static String normalizarMunicipio(String cidade) {
        var partes = PADRAO_MUNICIPIO.matcher(texto(cidade));
        return partes.matches() ? normalizarTexto(partes.group(1)) + "/" + partes.group(2).toUpperCase(Locale.ROOT) : "";
    }
    public static Double converterHoras(Object valor) {
        var partes = PADRAO_TEMPO.matcher(texto(valor));
        if (!partes.matches()) return null;
        try {
            int minutos = Integer.parseInt(partes.group(3));
            double dias = partes.group(1) == null ? 0 : Double.parseDouble(partes.group(1));
            double horas = dias * 24 + Double.parseDouble(partes.group(2)) + minutos / 60d;
            return minutos < 60 && Double.isFinite(horas) ? horas : null;
        } catch (NumberFormatException erro) { return null; }
    }
    public static List<MediaAtendimento> calcularMedias(List<Map<String, Object>> registros) {
        Map<Chave, List<Amostra>> grupos = new LinkedHashMap<>();
        Set<String> viagens = new HashSet<>();
        for (var registro : registros) {
            String numero = texto(registro.get("Nº Viagem"));
            if (numero.isBlank() || !normalizarTexto(registro.get("Grupo")).equals("SIDER") || !viagens.add(numero)) continue;
            adicionar(grupos, registro, "carga", "Remetente", "Carregamento", "Tempo Carga");
            adicionar(grupos, registro, "descarga", "Destinatário", "Destino", "Tempo Descarga");
        }
        List<MediaAtendimento> medias = new ArrayList<>();
        for (var entrada : grupos.entrySet()) {
            var grupo = entrada.getValue();
            List<Double> positivos = grupo.stream().map(Amostra::horas).filter(horas -> horas != null && horas > 0).sorted().toList();
            Double inferior = null, superior = null;
            if (positivos.size() >= 10) {
                double q1 = percentil(positivos, .25), q3 = percentil(positivos, .75);
                if (q3 > q1) { inferior = Math.max(0, q1 - 3 * (q3 - q1)); superior = q3 + 3 * (q3 - q1); }
            }
            List<Double> utilizados = new ArrayList<>();
            for (double horas : positivos) if (inferior == null || (horas >= inferior && horas <= superior)) utilizados.add(horas);
            int zeros = (int) grupo.stream().filter(item -> item.horas() != null && item.horas() == 0).count();
            int invalidos = (int) grupo.stream().filter(item -> item.horas() == null).count();
            medias.add(new MediaAtendimento(entrada.getKey().tipo(), grupo.getFirst().cliente(), grupo.getFirst().cidade(),
                    grupo.size(), zeros, invalidos, positivos.size() - utilizados.size(), utilizados.size(),
                    utilizados.isEmpty() ? null : utilizados.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
                    utilizados.isEmpty() ? null : percentil(utilizados, .5), inferior, superior));
        }
        return medias;
    }
    private static void adicionar(Map<Chave, List<Amostra>> grupos, Map<String, Object> registro,
            String tipo, String colunaCliente, String colunaCidade, String colunaTempo) {
        String cliente = texto(registro.get(colunaCliente)), cidade = texto(registro.get(colunaCidade));
        String municipio = normalizarMunicipio(cidade);
        if (cliente.isBlank() || municipio.isBlank()) return;
        var chave = new Chave(tipo, normalizarTexto(cliente), municipio);
        grupos.computeIfAbsent(chave, ignorada -> new ArrayList<>()).add(new Amostra(cliente, cidade, converterHoras(registro.get(colunaTempo))));
    }
    private static double percentil(List<Double> valores, double proporcao) {
        double posicao = (valores.size() - 1) * proporcao;
        int inferior = (int) posicao, superior = Math.min(inferior + 1, valores.size() - 1);
        return valores.get(inferior) + (valores.get(superior) - valores.get(inferior)) * (posicao - inferior);
    }
    public static MediaAtendimento buscarMedia(List<MediaAtendimento> medias, String tipo, String cliente, String cidade) {
        String municipio = normalizarMunicipio(cidade), nome = normalizarTexto(cliente);
        if (municipio.isBlank() || nome.isBlank()) return null;
        return medias.stream().filter(media -> media.tipo().equals(tipo) && normalizarTexto(media.cliente()).equals(nome)
                && normalizarMunicipio(media.cidade()).equals(municipio) && media.aplicavel()).findFirst().orElse(null);
    }
    public static TempoAtendimento estimarTempo(List<MediaAtendimento> medias, String tipo, String cliente,
            String cidade, Double manual, double alternativa) {
        if (manual != null) return new TempoAtendimento(manual, "manual", cliente, cidade, 0);
        MediaAtendimento media = buscarMedia(medias, tipo, cliente, cidade);
        return media == null ? new TempoAtendimento(alternativa, "configurado", cliente, cidade, 0)
                : new TempoAtendimento(media.mediaHoras(), "historico", media.cliente(), media.cidade(), media.utilizados());
    }
}
