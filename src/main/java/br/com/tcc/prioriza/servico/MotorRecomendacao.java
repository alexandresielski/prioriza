package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Modelos.*;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import static br.com.tcc.prioriza.servico.FuncoesDados.*;

@Service
public class MotorRecomendacao {
    public boolean validarCoordenadas(Local ponto) {
        return ponto != null && ponto.latitude() != null && ponto.longitude() != null
                && Double.isFinite(ponto.latitude()) && Double.isFinite(ponto.longitude())
                && Math.abs(ponto.latitude()) <= 90 && Math.abs(ponto.longitude()) <= 180
                && (ponto.latitude() != 0 || ponto.longitude() != 0);
    }

    public double calcularDistancia(Local origem, Local destino, double acrescimo) {
        exigir(validarCoordenadas(origem) && validarCoordenadas(destino), "Coordenadas ausentes ou inválidas.");
        double latitudeOrigem = Math.toRadians(origem.latitude()), latitudeDestino = Math.toRadians(destino.latitude());
        double diferencaLatitude = latitudeDestino - latitudeOrigem;
        double diferencaLongitude = Math.toRadians(destino.longitude() - origem.longitude());
        double parcela = Math.pow(Math.sin(diferencaLatitude / 2), 2) + Math.cos(latitudeOrigem)
                * Math.cos(latitudeDestino) * Math.pow(Math.sin(diferencaLongitude / 2), 2);
        return 6371.0088 * 2 * Math.asin(Math.sqrt(Math.clamp(parcela, 0, 1))) * (1 + acrescimo / 100);
    }

    public Map<String, Double> calcularPesos(double horasAteColeta, boolean considerarFaturamento) {
        return calcularPesos(horasAteColeta, considerarFaturamento, "DINAMICO");
    }
    public Map<String, Double> calcularPesos(double horasAteColeta, boolean considerarFaturamento, String cenario) {
        exigir(Set.of("DINAMICO", "PRAZO", "CUSTO", "BALANCEADO").contains(cenario), "Cenário operacional inválido.");
        if (!cenario.equals("DINAMICO")) {
            double prazo = switch (cenario) { case "PRAZO" -> .8; case "CUSTO" -> .2; default -> .5; };
            Map<String, Double> pesos = new LinkedHashMap<>();
            pesos.put("distancia", 1 - prazo); pesos.put("prazo", prazo); pesos.put("faturamento", 0d);
            return pesos;
        }
        double pressao = Math.clamp((24 - horasAteColeta) / 24, 0, 1);
        double prazo = .20 + .40 * pressao, faturamento = considerarFaturamento ? .20 : 0;
        Map<String, Double> pesos = new LinkedHashMap<>();
        pesos.put("distancia", 1 - prazo - faturamento);
        pesos.put("prazo", prazo);
        pesos.put("faturamento", faturamento);
        return pesos;
    }

    public void validarParametros(Parametros parametros) {
        if (parametros.precoCombustivel() != null || parametros.consumoMedio() != null)
            exigir(finito(parametros.precoCombustivel()) && parametros.precoCombustivel() > 0
                    && finito(parametros.consumoMedio()) && parametros.consumoMedio() > 0,
                    "Informe preco do combustivel e consumo medio, ambos maiores que zero, ou deixe ambos em branco.");
        exigir(Set.of("DINAMICO", "PRAZO", "CUSTO", "BALANCEADO").contains(parametros.cenario()), "Cenário operacional inválido.");
        exigir(finito(parametros.velocidade()) && parametros.velocidade() >= 1 && parametros.velocidade() <= 120,
                "Confira a velocidade media.");
        exigir(finito(parametros.acrescimo()) && parametros.acrescimo() >= 0 && parametros.acrescimo() <= 100,
                "Confira o acrescimo de distancia.");
        exigir(finito(parametros.toleranciaPosicao()) && parametros.toleranciaPosicao() >= 0, "Confira a tolerância de posição.");
        validarTempo(parametros.tempoCargaRestante());
        validarTempo(parametros.tempoDescargaRestante());
    }

    public Previsao preverDisponibilidade(Composicao composicao, String referenciaTexto, Parametros configuracao, Ajuste ajuste) {
        return preverDisponibilidade(composicao, referenciaTexto, configuracao, ajuste, List.of());
    }
    public Previsao preverDisponibilidade(Composicao composicao, String referenciaTexto, Parametros configuracao,
            Ajuste ajuste, List<MediaAtendimento> medias) {
        Parametros parametros = configuracao == null ? Parametros.padrao() : configuracao;
        validarParametros(parametros);
        LocalDateTime referencia = converterData(referenciaTexto);
        exigir(referencia != null, "Informe a referência da simulação.");
        List<String> motivos = new ArrayList<>(), avisos = new ArrayList<>();
        List<Etapa> etapas = new ArrayList<>();
        if (ajuste != null && (!texto(ajuste.disponivelEm()).isBlank() || ajuste.local() != null)) {
            LocalDateTime dataManual = converterData(ajuste.disponivelEm());
            if (dataManual == null || !validarCoordenadas(ajuste.local())) {
                motivos.add("Previsão manual de liberação incompleta.");
                return pendente(motivos, avisos);
            }
            avisos.add("Local e horário de liberação informados manualmente.");
            if (!"cliente".equals(ajuste.local().fonte())) avisos.add("Liberação com coordenadas aproximadas ou manuais.");
            return new Previsao(true, "manual", formatarData(dataManual.isBefore(referencia) ? referencia : dataManual),
                    ajuste.local(), etapas, motivos, avisos);
        }
        LocalDateTime cargaAgendada = validarAgendamento(ajuste == null ? null : ajuste.cargaAgendadaEm());
        LocalDateTime descargaAgendada = validarAgendamento(ajuste == null ? null : ajuste.descargaAgendadaEm());
        LocalDateTime ultimaPosicao = converterData(composicao.ultimaPosicao());
        if (ultimaPosicao == null || ultimaPosicao.isAfter(referencia)) motivos.add("Sem posição conhecida na data de referência.");
        else if (calcularHoras(ultimaPosicao, referencia) > parametros.toleranciaPosicao()) {
            if (parametros.aceitarDesatualizadas()) avisos.add("Posição antiga aceita explicitamente na simulação.");
            else motivos.add("Posição desatualizada para a referência da simulação.");
        }
        Local posicao = new Local(null, composicao.cidade(), composicao.cidade(), composicao.latitude(),
                composicao.longitude(), "posicao", "Posicao importada do veiculo");
        if (!validarCoordenadas(posicao)) motivos.add("Sem coordenadas válidas do local de partida.");
        if (!motivos.isEmpty()) return pendente(motivos, avisos);
        String situacao = normalizarTexto(composicao.situacao());
        if (situacao.equals("VAZIO")) return new Previsao(true, "vazio", formatarData(referencia), posicao, etapas, motivos, avisos);
        if (!parametros.considerarOcupados()) {
            motivos.add("Disponibilidade automática de ocupados desativada; informe uma previsão manual.");
            return pendente(motivos, avisos);
        }
        if (!Set.of("DESTINADO", "CARGA", "VIAGEM", "DESCARGA").contains(situacao)) {
            motivos.add("Situação sem regra de previsão; informe a liberação manualmente.");
            return pendente(motivos, avisos);
        }
        Local origem = composicao.localOrigemAtual(), destino = composicao.localDestinoAtual();
        if (!situacao.equals("DESCARGA") && !validarCoordenadas(destino)) motivos.add("Destino da operação atual sem coordenadas; informe a liberação manualmente.");
        if (situacao.equals("DESTINADO") && !validarCoordenadas(origem)) motivos.add("Coleta da operação atual sem coordenadas; informe a liberação manualmente.");
        if (!motivos.isEmpty()) return pendente(motivos, avisos);
        Local partidaViagem = posicao;
        if (cargaAgendada != null && !situacao.equals("DESTINADO"))
            avisos.add("Agendamento de carga ignorado: etapa já iniciada ou concluída na situação atual.");
        if (descargaAgendada != null && situacao.equals("DESCARGA"))
            avisos.add("Agendamento de descarga ignorado: atendimento já iniciado; considera somente o tempo restante.");
        if (situacao.equals("DESTINADO")) {
            double distancia = calcularDistancia(posicao, origem, parametros.acrescimo());
            etapas.add(new Etapa("Deslocamento até a coleta atual", distancia / parametros.velocidade(), distancia));
            partidaViagem = origem;
            esperarAgendamento(etapas, referencia, cargaAgendada, "carga", avisos);
        }
        if (situacao.equals("DESTINADO")) {
            TempoAtendimento tempo = TemposHistoricos.estimarTempo(medias, "carga", composicao.remetenteAtual(),
                    composicao.origemAtual(), null, parametros.tempoCargaRestante());
            adicionarAtendimento(etapas, avisos, "Carga atual", tempo);
        } else if (situacao.equals("CARGA")) {
            etapas.add(new Etapa("Carga restante configurada", parametros.tempoCargaRestante(), 0));
            avisos.add("Carga já iniciada: usa tempo restante configurado, não a média da duração total.");
        }
        if (!situacao.equals("DESCARGA")) {
            double distancia = calcularDistancia(partidaViagem, destino, parametros.acrescimo());
            etapas.add(new Etapa("Percurso restante até a entrega atual", distancia / parametros.velocidade(), distancia));
            esperarAgendamento(etapas, referencia, descargaAgendada, "descarga", avisos);
        }
        if (situacao.equals("DESCARGA")) {
            etapas.add(new Etapa("Descarga restante configurada", parametros.tempoDescargaRestante(), 0));
            avisos.add("Descarga já iniciada: usa tempo restante configurado, não a média da duração total.");
        } else {
            TempoAtendimento tempo = TemposHistoricos.estimarTempo(medias, "descarga", composicao.destinatarioAtual(),
                    composicao.destinoAtual(), null, parametros.tempoDescargaRestante());
            adicionarAtendimento(etapas, avisos, "Descarga atual", tempo);
        }
        if ((situacao.equals("DESTINADO") && !"cliente".equals(origem.fonte()))
                || (!situacao.equals("DESCARGA") && !"cliente".equals(destino.fonte()))) avisos.add("Operação atual usa coordenada municipal aproximada.");
        avisos.add("Fila e espera operacional no cliente não estimadas. A espera pelo agendamento e calculada separadamente. Sem pausas de jornada.");
        long segundos = (long) Math.ceil(etapas.stream().mapToDouble(Etapa::horas).sum() * 3600);
        return new Previsao(true, "automatica", referencia.plusSeconds(segundos).toString(),
                situacao.equals("DESCARGA") ? posicao : destino, etapas, motivos, avisos);
    }

    public Resultado avaliarComposicoes(Carga carga, List<Composicao> composicoes, Map<String, Ajuste> correcoes, Parametros configuracao) {
        return avaliarComposicoes(carga, composicoes, correcoes, configuracao, List.of());
    }
    public Resultado avaliarComposicoes(Carga carga, List<Composicao> composicoes, Map<String, Ajuste> correcoes,
            Parametros configuracao, List<MediaAtendimento> medias) {
        return avaliarComposicoes(carga, composicoes, correcoes, configuracao, medias, Set.of());
    }
    public Resultado avaliarComposicoes(Carga carga, List<Composicao> composicoes, Map<String, Ajuste> correcoes,
            Parametros configuracao, List<MediaAtendimento> medias, Set<String> recursosReservados) {
        exigir(carga != null, "Informe a carga.");
        Parametros parametros = configuracao == null ? Parametros.padrao() : configuracao;
        Map<String, Ajuste> ajustes = correcoes == null ? Map.of() : correcoes;
        validarParametros(parametros);
        LocalDateTime referencia = converterData(carga.referencia()), coleta = converterData(carga.coleta());
        exigir(referencia != null && coleta != null && !coleta.isBefore(referencia), "A coleta deve ocorrer a partir da referência da simulação.");
        exigir(finito(carga.peso()) && carga.peso() > 0, "Informe o peso da carga em kg, maior que zero.");
        exigir(validarCoordenadas(carga.origem()), "Selecione um local de coleta com coordenadas ou informe as coordenadas.");
        exigir(carga.destino() == null || validarCoordenadas(carga.destino()), "O destino selecionado não possui coordenadas válidas.");
        exigir(carga.frete() == null || (finito(carga.frete()) && carga.frete() >= 0), "O frete não pode ser negativo.");
        TempoAtendimento estimativaCarga = TemposHistoricos.estimarTempo(medias, "carga", carga.origem().nome(),
                carga.origem().cidade(), carga.tempoCarga(), parametros.tempoCargaRestante());
        TempoAtendimento estimativaDescarga = TemposHistoricos.estimarTempo(medias, "descarga",
                carga.destino() == null ? "" : carga.destino().nome(), carga.destino() == null ? "" : carga.destino().cidade(),
                carga.tempoDescarga(), parametros.tempoDescargaRestante());
        double tempoCarga = estimativaCarga.horas(), tempoDescarga = estimativaDescarga.horas();
        validarTempo(tempoCarga); validarTempo(tempoDescarga);
        Double distanciaCarregada = carga.destino() == null ? null : calcularDistancia(carga.origem(), carga.destino(), parametros.acrescimo());
        boolean financeiro = carga.frete() != null && carga.frete() > 0 && carga.destino() != null;
        Map<String, Double> pesos = calcularPesos(calcularHoras(referencia, coleta), financeiro, parametros.cenario());
        List<Alternativa> aprovadas = new ArrayList<>(), excluidas = new ArrayList<>();
        for (Composicao composicao : composicoes) {
            if (!normalizarTexto(composicao.grupo()).contains("SIDER")) continue;
            List<String> motivos = new ArrayList<>();
            if (recursosReservados.contains(normalizarTexto(composicao.placa())))
                motivos.add("Cavalo já reservado em cenário confirmado.");
            if (recursosReservados.contains(normalizarTexto(composicao.carreta())))
                motivos.add("Carreta já reservada em cenário confirmado.");
            if (texto(composicao.carreta()).isBlank() || !composicao.engatada()) motivos.add("Sem carreta engatada confirmada.");
            if (!composicao.equipamentoSider()) motivos.add("Classificação SIDER da carreta não confirmada no cadastro.");
            if (composicao.capacidade() == null || composicao.capacidade() <= 0) motivos.add("Cadastro de PBT ou tara incompleto.");
            else if (carga.peso() > composicao.capacidade()) motivos.add("Peso supera capacidade da composição.");
            Previsao previsao = preverDisponibilidade(composicao, carga.referencia(), parametros, ajustes.get(composicao.placa()), medias);
            motivos.addAll(previsao.motivos());
            if (!motivos.isEmpty()) { excluidas.add(excluir(composicao, previsao, motivos, null, null)); continue; }
            boolean mesmoCliente = normalizarTexto(composicao.situacao()).equals("DESCARGA")
                    && !previsao.tipo().equals("manual") && mesmoCliente(composicao.localDestinoAtual(), carga.origem());
            double distancia = mesmoCliente ? 0 : calcularDistancia(previsao.local(), carga.origem(), parametros.acrescimo());
            List<String> avisosAlternativa = new ArrayList<>(previsao.avisos());
            if (mesmoCliente) avisosAlternativa.add("Já descarregando no cliente da nova coleta: termina a descarga e pode carregar no mesmo local; deslocamento vazio zero. Mantida a espera até o horário da coleta.");
            double horasDeslocamento = distancia / parametros.velocidade();
            LocalDateTime chegada = adicionarHoras(converterData(previsao.disponivelEm()), horasDeslocamento);
            double folga = calcularHoras(chegada, coleta);
            if (folga < 0) {
                excluidas.add(excluir(composicao, previsao, List.of("Chegada estimada depois do horário de coleta."), distancia, formatarData(chegada)));
                continue;
            }
            double horasViagem = distanciaCarregada == null ? 0 : distanciaCarregada / parametros.velocidade();
            LocalDateTime liberacao = adicionarHoras(coleta, tempoCarga + horasViagem + tempoDescarga);
            double horasOcupacao = calcularHoras(referencia, liberacao);
            Double receitaDiaria = financeiro && horasOcupacao > 0 ? carga.frete() * 24 / horasOcupacao : null;
            Map<String, Double> notas = new LinkedHashMap<>();
            notas.put("distancia", 100 / (1 + distancia / 100));
            notas.put("prazo", 100 * Math.min(1, folga / 4));
            notas.put("faturamento", receitaDiaria == null ? 0 : 100 * receitaDiaria / (receitaDiaria + 2000));
            Map<String, Double> contribuicoes = new LinkedHashMap<>();
            for (String criterio : pesos.keySet()) contribuicoes.put(criterio, notas.get(criterio) * pesos.get(criterio));
            aprovadas.add(new Alternativa(composicao, previsao, List.of(), avisosAlternativa, distancia, horasDeslocamento,
                    formatarData(chegada), folga, previsao.disponivelEm(), previsao.local(), notas, contribuicoes,
                    contribuicoes.values().stream().mapToDouble(Double::doubleValue).sum(),
                    carga.destino() == null ? null : formatarData(liberacao), carga.destino() == null ? null : horasOcupacao, receitaDiaria));
        }
        // A seguranca so diferencia alternativas a ate 5 km da menor distancia viavel.
        double menorDistancia = aprovadas.stream().mapToDouble(Alternativa::distancia).min().orElse(0);
        pesos.replaceAll((criterio, peso) -> peso * .95);
        pesos.put("seguranca", .05);
        List<Alternativa> pontuadas = new ArrayList<>();
        for (Alternativa alternativa : aprovadas) {
            boolean proxima = alternativa.distancia() <= menorDistancia + 5;
            double seguranca = proxima ? pontuarSeguranca(alternativa, carga.origem()) : 0;
            Map<String, Double> notas = new LinkedHashMap<>(alternativa.notas()); notas.put("seguranca", seguranca);
            Map<String, Double> contribuicoes = new LinkedHashMap<>();
            for (String criterio : pesos.keySet()) contribuicoes.put(criterio, notas.get(criterio) * pesos.get(criterio));
            List<String> avisosAlternativa = new ArrayList<>(alternativa.avisos());
            avisosAlternativa.add(proxima ? "Segurança operacional: " + seguranca + "/100; alternativa a até 5 km da menor distância viável. Peso de 5%, heuristica de etapas restantes, não probabilidade de atraso."
                    : "Seguranca sem pontos: distancia superior a menor distancia viavel + 5 km.");
            pontuadas.add(new Alternativa(alternativa.composicao(), alternativa.previsaoDisponibilidade(), alternativa.motivos(), avisosAlternativa,
                alternativa.distancia(), alternativa.horasDeslocamento(), alternativa.chegada(), alternativa.folga(), alternativa.disponivelEm(), alternativa.partida(),
                notas, contribuicoes, contribuicoes.values().stream().mapToDouble(Double::doubleValue).sum(), alternativa.liberacao(), alternativa.horasOcupacao(), alternativa.faturamentoDia()));
        }
        aprovadas = pontuadas;
        aprovadas.sort(Comparator.comparing(Alternativa::nota).reversed().thenComparing(Alternativa::distancia).thenComparing(item -> item.composicao().placa()));
        List<String> avisos = new ArrayList<>(List.of("Distâncias por Haversine com acrescimo percentual; não são rotas rodoviárias.",
                "Fila e espera operacional no cliente nao estimadas. Tempos historicos medem somente atendimento, sem fila.",
                "Tempos estimados sem pausas de jornada; liberacoes automaticas e manuais sao hipoteses operacionais."));
        avisos.add(descreverTempo("Carga da nova operação", estimativaCarga));
        if (carga.destino() != null) avisos.add(descreverTempo("Descarga da nova operação", estimativaDescarga));
        if (!"cliente".equals(carga.origem().fonte())) avisos.add("Coleta: coordenada de referência municipal ou manual, não do estabelecimento.");
        if (carga.destino() != null && !"cliente".equals(carga.destino().fonte())) avisos.add("Entrega: coordenada de referência municipal ou manual, não do estabelecimento.");
        avisos.add(financeiro ? "Para uma unica carga com coleta fixa, o faturamento/dia pode ser igual entre os candidatos."
                : "Faturamento fora da pontuacao: informe frete e destino. Piso minimo ainda nao calculado.");
        avisos.add("Cenário operacional: " + parametros.cenario() + ". Custo e representado pelo deslocamento vazio estimado; não é custo financeiro nem lucro.");
        avisos.add("Segurança: 5% da nota; demais critérios mantem proporções nos 95% restantes. Até 5 km da menor distância viável: vazio ou descarga no mesmo cliente=100, descarga=80, manual=50, viagem=40, carga=20, destinado=10. Fora da faixa=0. Não altera as validações de prazo, capacidade e equipamento.");
        if (!parametros.cenario().equals("DINAMICO")) avisos.add("Cenário com pesos fixos de distância e prazo. Faturamento permanece informativo, sem peso na classificação.");
        Map<String, Double> custosCombustivel = new LinkedHashMap<>();
        if (parametros.precoCombustivel() != null) {
            for (List<Alternativa> alternativas : List.of(aprovadas, excluidas))
                for (Alternativa alternativa : alternativas) if (alternativa.distancia() != null)
                    custosCombustivel.put(alternativa.composicao().placa(), calcularCustoCombustivel(alternativa.distancia(), parametros));
            avisos.add("Combustível estimado somente no deslocamento vazio até a nova coleta. Não inclui pedágio, manutenção, espera nem o percurso da operação atual. Consumo unico para a frota: a ordem por custo equivale a ordem por distância.");
        } else avisos.add("Custo de combustível não calculado: informe preço por litro e consumo medio nos parâmetros.");
        return new Resultado(aprovadas, excluidas, pesos, distanciaCarregada, avisos, parametros, estimativaCarga, estimativaDescarga, custosCombustivel);
    }
    public double calcularCustoCombustivel(double distancia, Parametros parametros) {
        validarParametros(parametros);
        exigir(Double.isFinite(distancia) && distancia >= 0 && parametros.precoCombustivel() != null, "Dados insuficientes para calcular combustível.");
        double custo = distancia / parametros.consumoMedio() * parametros.precoCombustivel();
        exigir(Double.isFinite(custo), "Parâmetros de combustível resultam em custo inválido.");
        return custo;
    }

    private boolean mesmoCliente(Local a, Local b) {
        return a != null && b != null && "cliente".equals(a.fonte()) && "cliente".equals(b.fonte())
                && a.id() != null && b.id() != null && a.id().startsWith("cliente-") && b.id().startsWith("cliente-")
                && normalizarTexto(a.cidade()).equals(normalizarTexto(b.cidade()))
                && (a.id().equals(b.id()) || (normalizarTexto(a.nome()).equals(normalizarTexto(b.nome()))
                    && Objects.equals(a.latitude(), b.latitude()) && Objects.equals(a.longitude(), b.longitude())));
    }
    private double pontuarSeguranca(Alternativa alternativa, Local origem) {
        if (alternativa.previsaoDisponibilidade().tipo().equals("manual")) return 50;
        return switch (normalizarTexto(alternativa.composicao().situacao())) {
            case "VAZIO" -> 100;
            case "DESCARGA" -> mesmoCliente(alternativa.composicao().localDestinoAtual(), origem) ? 100 : 80;
            case "VIAGEM" -> 40;
            case "CARGA" -> 20;
            case "DESTINADO" -> 10;
            default -> 0;
        };
    }

    private String descreverTempo(String etapa, TempoAtendimento tempo) {
        return etapa + ": " + (tempo.fonte().equals("historico")
                ? "média histórica de " + tempo.cliente() + " / " + tempo.cidade() + ", " + tempo.amostras() + " atendimentos."
                : tempo.fonte().equals("manual") ? "tempo informado manualmente."
                : "tempo configurado; sem media historica suficiente para este cliente e municipio.");
    }
    private void adicionarAtendimento(List<Etapa> etapas, List<String> avisos, String nome, TempoAtendimento tempo) {
        etapas.add(new Etapa(nome + (tempo.fonte().equals("historico") ? " / média histórica" : " / tempo configurado"), tempo.horas(), 0));
        avisos.add(descreverTempo(nome, tempo));
    }

    private Alternativa excluir(Composicao composicao, Previsao previsao, List<String> motivos, Double distancia, String chegada) {
        return new Alternativa(composicao, previsao, motivos, previsao.avisos(), distancia, null, chegada, null,
                previsao.disponivelEm(), previsao.local(), null, null, null, null, null, null);
    }
    private LocalDateTime validarAgendamento(String textoData) {
        if (texto(textoData).isBlank()) return null;
        LocalDateTime data = converterData(textoData);
        exigir(data != null, "Data de agendamento da operação atual inválida.");
        return data;
    }
    private void esperarAgendamento(List<Etapa> etapas, LocalDateTime referencia, LocalDateTime agendamento,
            String operacao, List<String> avisos) {
        if (agendamento == null) {
            avisos.add("Sem agendamento de " + operacao + " atual: considera atendimento ao chegar.");
            return;
        }
        LocalDateTime chegada = adicionarHoras(referencia, etapas.stream().mapToDouble(Etapa::horas).sum());
        avisos.add("Agendamento de " + operacao + " atual: " + formatarData(agendamento) + ".");
        if (chegada.isBefore(agendamento))
            etapas.add(new Etapa("Espera pelo agendamento de " + operacao + " atual", calcularHoras(chegada, agendamento), 0));
        else avisos.add("Chegada no horário ou após o agendamento de " + operacao
                + ": sem espera adicional; eventual necessidade de reagendamento nao estimada.");
    }
    private Previsao pendente(List<String> motivos, List<String> avisos) {
        return new Previsao(false, "indisponivel", null, null, List.of(), motivos, avisos);
    }
    private boolean finito(Double valor) { return valor != null && Double.isFinite(valor); }
    private void validarTempo(Double tempo) { exigir(finito(tempo) && tempo >= 0 && tempo <= 720, "Os tempos devem estar entre 0 e 720 horas."); }
    private void exigir(boolean condicao, String mensagem) { if (!condicao) throw new IllegalArgumentException(mensagem); }
    private double calcularHoras(LocalDateTime inicio, LocalDateTime fim) { return Duration.between(inicio, fim).toNanos() / 3_600_000_000_000d; }
    private LocalDateTime adicionarHoras(LocalDateTime inicio, double horas) { return inicio.plusNanos(Math.round(horas * 3_600_000_000_000d)); }
}
