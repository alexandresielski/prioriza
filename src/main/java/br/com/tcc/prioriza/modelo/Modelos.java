package br.com.tcc.prioriza.modelo;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;
import java.util.Map;

public final class Modelos {
    private Modelos() { }

    public record Local(String id, String nome, String cidade, Double latitude, Double longitude, String fonte, String detalhe) { }
    public record Composicao(String placa, String carreta, String grupo, String situacao, boolean engatada,
            boolean equipamentoSider, String cidade, Double latitude, Double longitude, String ultimaPosicao,
            String origemAtual, String remetenteAtual, String destinoAtual, String destinatarioAtual,
            Double capacidade, Double eixos, Local localOrigemAtual, Local localDestinoAtual) { }
    public record Carga(String identificacao, Local origem, Local destino, String referencia, String coleta,
            Double peso, Double frete, Double tempoCarga, Double tempoDescarga, Boolean obrigatoria) {
        public Carga(String identificacao, Local origem, Local destino, String referencia, String coleta, Double peso,
                Double frete, Double tempoCarga, Double tempoDescarga) {
            this(identificacao, origem, destino, referencia, coleta, peso, frete, tempoCarga, tempoDescarga, false);
        }
        public Carga { obrigatoria = Boolean.TRUE.equals(obrigatoria); }
    }
    public record Parametros(Double velocidade, Double acrescimo, Double toleranciaPosicao,
            Boolean aceitarDesatualizadas, Boolean considerarOcupados, Double tempoCargaRestante, Double tempoDescargaRestante, String cenario,
            Double precoCombustivel, Double consumoMedio) {
        public Parametros(Double velocidade, Double acrescimo, Double toleranciaPosicao, Boolean aceitarDesatualizadas,
                Boolean considerarOcupados, Double tempoCargaRestante, Double tempoDescargaRestante, String cenario) {
            this(velocidade, acrescimo, toleranciaPosicao, aceitarDesatualizadas, considerarOcupados, tempoCargaRestante, tempoDescargaRestante, cenario, null, null);
        }
        public Parametros(Double velocidade, Double acrescimo, Double toleranciaPosicao, Boolean aceitarDesatualizadas,
                Boolean considerarOcupados, Double tempoCargaRestante, Double tempoDescargaRestante) {
            this(velocidade, acrescimo, toleranciaPosicao, aceitarDesatualizadas, considerarOcupados, tempoCargaRestante, tempoDescargaRestante, null);
        }
        public Parametros {
            cenario = cenario == null ? "DINAMICO" : cenario.strip().toUpperCase(java.util.Locale.ROOT);
            velocidade = velocidade == null ? 53d : velocidade;
            acrescimo = acrescimo == null ? 25d : acrescimo;
            toleranciaPosicao = toleranciaPosicao == null ? 6d : toleranciaPosicao;
            aceitarDesatualizadas = Boolean.TRUE.equals(aceitarDesatualizadas);
            considerarOcupados = considerarOcupados == null || considerarOcupados;
            tempoCargaRestante = tempoCargaRestante == null ? 2d : tempoCargaRestante;
            tempoDescargaRestante = tempoDescargaRestante == null ? 2d : tempoDescargaRestante;
        }
        public static Parametros padrao() { return new Parametros(null, null, null, null, null, null, null); }
    }
    public record Ajuste(String disponivelEm, Local local, String cargaAgendadaEm, String descargaAgendadaEm) {
        public Ajuste(String disponivelEm, Local local) { this(disponivelEm, local, null, null); }
    }
    public record Etapa(String nome, double horas, double distancia) { }
    public record MediaAtendimento(String tipo, String cliente, String cidade, int total, int zeros, int invalidos,
            int extremos, int utilizados, Double mediaHoras, Double medianaHoras, Double limiteInferior, Double limiteSuperior) {
        public boolean aplicavel() { return utilizados >= 10 && mediaHoras != null && mediaHoras > 0; }
    }
    public record TempoAtendimento(double horas, String fonte, String cliente, String cidade, int amostras) { }
    public record Previsao(boolean disponivel, String tipo, String disponivelEm, Local local,
            List<Etapa> etapas, List<String> motivos, List<String> avisos) { }
    public record Alternativa(@JsonUnwrapped Composicao composicao, Previsao previsaoDisponibilidade,
            List<String> motivos, List<String> avisos, Double distancia, Double horasDeslocamento,
            String chegada, Double folga, String disponivelEm, Local partida, Map<String, Double> notas,
            Map<String, Double> contribuicoes, Double nota, String liberacao, Double horasOcupacao, Double faturamentoDia) { }
    public record Resultado(List<Alternativa> aprovadas, List<Alternativa> excluidas, Map<String, Double> pesos,
            Double distanciaCarregada, List<String> avisos, Parametros parametros,
            TempoAtendimento tempoCarga, TempoAtendimento tempoDescarga, Map<String, Double> custosCombustivel) { }
    public record PedidoSimulacao(Carga carga, Parametros parametros, Map<String, Ajuste> ajustes) { }
    public record PedidoPlanejamento(Carga carga, Integer versao) { }
    public record PedidoCancelamento(Integer versao) { }
    public record CargaPlanejada(java.util.UUID id, int versao, String situacao, Carga carga, String criadaEm, String atualizadaEm, boolean alocada) { }
    public record PedidoDisponibilidade(String referencia, Parametros parametros, Map<String, Ajuste> ajustes) { }
    public record PedidoImportacao(String nome, String conteudo) { }
    public record Importacao(@JsonUnwrapped Resumo resumo, List<String> avisosImportacao) { }
    public record Base(String nome, Integer registros, String chave, boolean carregada) { }
    public record Resumo(String referencia, List<Base> bases, List<Composicao> composicoes,
            Map<String, Long> contagens, int cargasConsolidadas, String pasta, String tecnologia, String banco) { }
    public record CargaHistorica(String numero, String origem, String destino, String cidadeOrigem,
            String cidadeDestino, Double peso, String coleta, String situacao, int linhas,
            List<String> documentos, boolean revisar, Local localOrigem, Local localDestino) { }
}
