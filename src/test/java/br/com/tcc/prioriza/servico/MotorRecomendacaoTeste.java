package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Modelos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MotorRecomendacaoTeste {
    private final MotorRecomendacao motor = new MotorRecomendacao();
    private final Local saoPaulo = new Local("sp", "Sao Paulo", "Sao Paulo/SP", -23.55, -46.63, "cliente", "");
    private final Local pontaGrossa = new Local("pg", "Ponta Grossa", "Ponta Grossa/PR", -25.09, -50.16, "cliente", "");

    private Carga carga(double peso, String coleta) {
        return new Carga("Teste", saoPaulo, pontaGrossa, "2026-10-20T08:00", coleta, peso, 4000d, 2d, 2d);
    }
    private Composicao composicao(String placa, String situacao, Double latitude, String data, Local origem, Local destino) {
        return new Composicao(placa, "CARRETA", "SIDER", situacao, true, true, "Sao Paulo/SP", latitude, -46.63, data,
                "Sao Paulo/SP", "Cliente", "Ponta Grossa/PR", "Cliente", 32000d, 6d, origem, destino);
    }
    private Composicao vazio() { return composicao("VAZIO", "Vazio", -23.55, "2026-10-20T08:00", null, null); }

    @Test void reservasExcluemCavaloOuCarretaMesmoComLiberacaoManual() {
        var carga = carga(28000, "2026-10-20T12:00");
        for (String recurso : List.of("VAZIO", "CARRETA")) {
            var resultado = motor.avaliarComposicoes(carga, List.of(vazio()),
                    Map.of("VAZIO", new Ajuste("2026-10-20T08:00", saoPaulo)), null, List.of(), java.util.Set.of(recurso));
            assertTrue(resultado.aprovadas().isEmpty());
            assertEquals(1, resultado.excluidas().size());
            assertTrue(resultado.excluidas().getFirst().motivos().stream().anyMatch(m -> m.contains("reservad")));
        }
        assertEquals(1, motor.avaliarComposicoes(carga, List.of(vazio()), null, null, List.of(), java.util.Set.of()).aprovadas().size());
        assertEquals(1, motor.avaliarComposicoes(carga, List.of(vazio()), null, null, List.of(), java.util.Set.of("OUTRA")).aprovadas().size());
    }

    @Test void cenariosMudamRankingSemAlterarViabilidade() {
        var perto = composicao("PERTO", "Vazio", -23.55, "2026-10-20T08:00", null, null);
        var longe = composicao("LONGE", "Vazio", -24.05, "2026-10-20T08:00", null, null);
        var ajustes = Map.of("PERTO", new Ajuste("2026-10-20T11:55", saoPaulo));
        for (String cenario : List.of("PRAZO", "CUSTO", "BALANCEADO")) {
            var parametros = new Parametros(null, null, null, null, null, null, null, cenario);
            var resultado = motor.avaliarComposicoes(carga(28000, "2026-10-20T12:00"), List.of(perto, longe), ajustes, parametros);
            assertEquals(2, resultado.aprovadas().size());
            assertEquals(cenario.equals("CUSTO") ? "PERTO" : "LONGE", resultado.aprovadas().getFirst().composicao().placa());
            assertEquals(0, resultado.pesos().get("faturamento"));
            assertEquals(1, resultado.pesos().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-10);
        }
    }
    @Test void cenarioNaoAprovaAtrasoOuSobrepeso() {
        for (String cenario : List.of("PRAZO", "CUSTO", "BALANCEADO", "DINAMICO")) {
            var parametros = new Parametros(null, null, null, null, null, null, null, cenario);
            assertTrue(motor.avaliarComposicoes(carga(32001, "2026-10-20T12:00"), List.of(vazio()), null, parametros).aprovadas().isEmpty());
            assertTrue(motor.avaliarComposicoes(carga(28000, "2026-10-20T12:00"), List.of(vazio()),
                    Map.of("VAZIO", new Ajuste("2026-10-20T13:00", saoPaulo)), parametros).aprovadas().isEmpty());
        }
    }
    @Test void cenarioInvalidoRecusadoEPedidoAntigoPreservado() throws Exception {
        var parametros = new ObjectMapper().readValue("{}", Parametros.class);
        assertEquals("DINAMICO", parametros.cenario());
        assertEquals(motor.calcularPesos(12, true), motor.calcularPesos(12, true, parametros.cenario()));
        assertThrows(IllegalArgumentException.class, () -> motor.validarParametros(new Parametros(null, null, null, null, null, null, null, "ERRADO")));
    }
    @Test void pesosDosCenariosIndependemDeFreteEDeUrgencia() {
        for (String cenario : List.of("PRAZO", "CUSTO", "BALANCEADO"))
            assertEquals(motor.calcularPesos(1, true, cenario), motor.calcularPesos(48, false, cenario));
        assertEquals(.8, motor.calcularPesos(1, false, "PRAZO").get("prazo"));
        assertEquals(.8, motor.calcularPesos(1, false, "CUSTO").get("distancia"));
        assertEquals(.5, motor.calcularPesos(1, false, "BALANCEADO").get("prazo"));
    }
    @Test void combustivelCalculadoEmReaisSemInventarPrecoOuConsumo() {
        var parametros = new Parametros(null, null, null, null, null, null, null, "CUSTO", 6d, 3d);
        assertEquals(200, motor.calcularCustoCombustivel(100, parametros));
        assertEquals(0, motor.calcularCustoCombustivel(0, parametros));
        assertThrows(IllegalArgumentException.class, () -> motor.calcularCustoCombustivel(-1, parametros));
        assertThrows(IllegalArgumentException.class, () -> motor.validarParametros(new Parametros(null,null,null,null,null,null,null,"CUSTO",6d,null)));
        assertThrows(IllegalArgumentException.class, () -> motor.validarParametros(new Parametros(null,null,null,null,null,null,null,"CUSTO",6d,0d)));
        assertThrows(IllegalArgumentException.class, () -> motor.validarParametros(new Parametros(null,null,null,null,null,null,null,"CUSTO",Double.NaN,3d)));
    }
    @Test void custoCombustivelIntegradoAoResultadoSemMudarOrdemPorDistancia() {
        var longe = composicao("LONGE", "Vazio", -24.05, "2026-10-20T08:00", null, null);
        var parametros = new Parametros(null,null,null,null,null,null,null,"CUSTO",6d,3d);
        var resultado = motor.avaliarComposicoes(carga(28000,"2026-10-21T12:00"),List.of(vazio(),longe),null,parametros);
        assertEquals("VAZIO",resultado.aprovadas().getFirst().composicao().placa());
        assertEquals(0,resultado.custosCombustivel().get("VAZIO"));
        assertEquals(resultado.aprovadas().get(1).distancia()*2,resultado.custosCombustivel().get("LONGE"),1e-9);
        assertTrue(motor.avaliarComposicoes(carga(28000,"2026-10-21T12:00"),List.of(vazio()),null,null).custosCombustivel().isEmpty());
    }
    @Test void distanciaConhecidaEAcrescimo() {
        Local origem = new Local(null, "", "", 0d, 1d, "manual", ""), destino = new Local(null, "", "", 0d, 2d, "manual", "");
        assertEquals(111.195, motor.calcularDistancia(origem, destino, 0), .01);
        assertEquals(138.994, motor.calcularDistancia(origem, destino, 25), .01);
        assertEquals(0, motor.calcularDistancia(origem, origem, 25));
    }
    @Test void coordenadasInvalidasSaoRecusadas() {
        assertFalse(motor.validarCoordenadas(new Local(null, "", "", 0d, 0d, "manual", "")));
        assertFalse(motor.validarCoordenadas(new Local(null, "", "", 91d, 1d, "manual", "")));
        assertThrows(IllegalArgumentException.class, () -> motor.calcularDistancia(null, saoPaulo, 25));
    }
    @Test void pesoNoLimiteEPesoExcedente() {
        assertEquals(1, motor.avaliarComposicoes(carga(32000, "2026-10-21T10:00"), List.of(vazio()), null, null).aprovadas().size());
        assertEquals(1, motor.avaliarComposicoes(carga(32001, "2026-10-21T10:00"), List.of(vazio()), null, null).excluidas().size());
    }
    @Test void ocupadoSemDestinoFicaPendente() {
        var veiculo = composicao("OCUPADO", "Viagem", -23.55, "2026-10-20T08:00", null, null);
        assertFalse(motor.preverDisponibilidade(veiculo, "2026-10-20T08:00", null, null).disponivel());
    }
    @Test void viagemSomaPercursoEDescarga() {
        var veiculo = composicao("OCUPADO", "Viagem", -24d, "2026-10-20T08:00", null, saoPaulo);
        Previsao previsao = motor.preverDisponibilidade(veiculo, "2026-10-20T08:00", null, null);
        assertEquals("automatica", previsao.tipo()); assertEquals(2, previsao.etapas().size());
        assertEquals(saoPaulo, previsao.local());
    }
    @Test void cargaSomaAtendimentoRestante() {
        var veiculo = composicao("OCUPADO", "Carga", -23.55, "2026-10-20T08:00", null, saoPaulo);
        var parametros = new Parametros(null, null, null, null, null, 3d, 1d);
        assertEquals("2026-10-20T12:00", motor.preverDisponibilidade(veiculo, "2026-10-20T08:00", parametros, null).disponivelEm());
    }
    @Test void descargaMantemLocalAtual() {
        var veiculo = composicao("OCUPADO", "Descarga", -23.55, "2026-10-20T08:00", null, null);
        Previsao previsao = motor.preverDisponibilidade(veiculo, "2026-10-20T08:00", new Parametros(null, null, null, null, null, null, .5), null);
        assertEquals("2026-10-20T08:30", previsao.disponivelEm()); assertEquals(1, previsao.etapas().size());
        assertEquals(-23.55, previsao.local().latitude());
    }
    @Test void destinadoExigeColetaEEntrega() {
        var veiculo = composicao("OCUPADO", "Destinado", -23.55, "2026-10-20T08:00", null, pontaGrossa);
        assertFalse(motor.preverDisponibilidade(veiculo, "2026-10-20T08:00", null, null).disponivel());
        veiculo = composicao("OCUPADO", "Destinado", -23.55, "2026-10-20T08:00", saoPaulo, pontaGrossa);
        assertEquals(4, motor.preverDisponibilidade(veiculo, "2026-10-20T08:00", null, null).etapas().size());
    }
    @Test void correcaoManualTemPrioridade() {
        var veiculo = composicao("OCUPADO", "Viagem", -23.55, "2026-10-20T08:00", null, pontaGrossa);
        Previsao previsao = motor.preverDisponibilidade(veiculo, "2026-10-20T08:00", null, new Ajuste("2026-10-21T07:00", saoPaulo));
        assertEquals("manual", previsao.tipo()); assertEquals(saoPaulo, previsao.local());
    }
    @Test void posicaoAntigaOuFuturaBloqueia() {
        for (String data : List.of("2026-10-19T08:00", "2026-10-20T09:00")) {
            var veiculo = composicao("OCUPADO", "Viagem", -23.55, data, null, saoPaulo);
            assertFalse(motor.preverDisponibilidade(veiculo, "2026-10-20T08:00", null, null).disponivel());
        }
    }
    @Test void atrasoExclui() {
        var resultado = motor.avaliarComposicoes(carga(28000, "2026-10-20T09:00"), List.of(vazio()),
                Map.of("VAZIO", new Ajuste("2026-10-20T10:00", saoPaulo)), null);
        assertTrue(resultado.aprovadas().isEmpty()); assertTrue(resultado.excluidas().getFirst().motivos().getFirst().contains("depois"));
    }
    @Test void ocupadoPodeSuperarVazioMaisDistante() {
        var ocupado = composicao("OCUPADO", "Viagem", -23.56, "2026-10-20T08:00", null, saoPaulo);
        var distante = composicao("VAZIO", "Vazio", -24.5, "2026-10-20T08:00", null, null);
        var resultado = motor.avaliarComposicoes(carga(28000, "2026-10-20T12:00"), List.of(distante, ocupado), null, null);
        assertEquals("OCUPADO", resultado.aprovadas().getFirst().composicao().placa());
        assertEquals(0, resultado.aprovadas().getFirst().distancia());
    }
    @Test void velocidadeInicial53() {
        var veiculo = composicao("VAZIO", "Vazio", -24.5, "2026-10-20T08:00", null, null);
        var item = motor.avaliarComposicoes(carga(28000, "2026-10-21T10:00"), List.of(veiculo), null, null).aprovadas().getFirst();
        assertEquals(item.distancia() / 53, item.horasDeslocamento(), 1e-9);
    }
    @Test void pesosSomamUmESaoContextuais() {
        for (double horas : List.of(0d, 3d, 12d, 24d, 48d)) for (boolean financeiro : List.of(true, false)) {
            var pesos = motor.calcularPesos(horas, financeiro);
            assertEquals(1, pesos.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-10);
        }
        assertTrue(motor.calcularPesos(2, true).get("prazo") > motor.calcularPesos(24, true).get("prazo"));
    }
    @Test void contratoJsonMantemInterface() {
        var resultado = motor.avaliarComposicoes(carga(28000, "2026-10-21T10:00"), List.of(vazio()), null, null);
        var json = new ObjectMapper().valueToTree(resultado);
        assertEquals("VAZIO", json.path("aprovadas").get(0).path("placa").asText());
        assertTrue(json.path("aprovadas").get(0).has("previsaoDisponibilidade"));
    }
    @Test void chegadaAntecipadaEsperaAgendamentoDaDescarga() {
        var veiculo = composicao("OCUPADO", "Viagem", -23.55, "2026-10-20T06:00", null, saoPaulo);
        var previsao = motor.preverDisponibilidade(veiculo, "2026-10-20T06:00", null,
                new Ajuste(null, null, null, "2026-10-20T10:00"));
        assertEquals("2026-10-20T12:00", previsao.disponivelEm());
        assertEquals("automatica", previsao.tipo());
        assertEquals(4, previsao.etapas().stream().filter(etapa -> etapa.nome().startsWith("Espera pelo")).findFirst().orElseThrow().horas());
    }
    @Test void chegadaAposAgendamentoNaoEspera() {
        var veiculo = composicao("OCUPADO", "Viagem", -23.55, "2026-10-20T11:00", null, saoPaulo);
        var previsao = motor.preverDisponibilidade(veiculo, "2026-10-20T11:00", null,
                new Ajuste(null, null, null, "2026-10-20T10:00"));
        assertEquals("2026-10-20T13:00", previsao.disponivelEm());
        assertEquals(2, previsao.etapas().size());
        assertTrue(previsao.avisos().stream().anyMatch(aviso -> aviso.contains("reagendamento")));
    }
    @Test void destinadoEncadeiaAgendamentosDeCargaEDescarga() {
        var veiculo = composicao("OCUPADO", "Destinado", -23.55, "2026-10-20T06:00", saoPaulo, saoPaulo);
        var previsao = motor.preverDisponibilidade(veiculo, "2026-10-20T06:00", null,
                new Ajuste(null, null, "2026-10-20T08:00", "2026-10-20T14:00"));
        assertEquals("2026-10-20T16:00", previsao.disponivelEm());
        assertEquals(6, previsao.etapas().size());
        assertEquals(10, previsao.etapas().stream().mapToDouble(Etapa::horas).sum());
    }
    @Test void percursoConsomeParteDaEsperaAteAgendamento() {
        var veiculo = composicao("OCUPADO", "Viagem", -24d, "2026-10-20T06:00", null, saoPaulo);
        var previsao = motor.preverDisponibilidade(veiculo, "2026-10-20T06:00", null,
                new Ajuste(null, null, null, "2026-10-20T10:00"));
        assertEquals("2026-10-20T12:00", previsao.disponivelEm());
        assertEquals(6, previsao.etapas().stream().mapToDouble(Etapa::horas).sum(), 1e-9);
    }
    @Test void atendimentoIniciadoNaoRepeteEsperaAgendada() {
        for (String situacao : List.of("Carga", "Descarga")) {
            var veiculo = composicao("OCUPADO", situacao, -23.55, "2026-10-20T06:00", saoPaulo, saoPaulo);
            var ajuste = situacao.equals("Carga") ? new Ajuste(null, null, "2026-10-21T10:00", null)
                    : new Ajuste(null, null, null, "2026-10-21T10:00");
            var previsao = motor.preverDisponibilidade(veiculo, "2026-10-20T06:00", null, ajuste);
            assertEquals(situacao.equals("Carga") ? "2026-10-20T10:00" : "2026-10-20T08:00", previsao.disponivelEm());
            assertTrue(previsao.avisos().stream().anyMatch(aviso -> aviso.contains("ignorado")));
        }
    }
    @Test void liberacaoManualContinuaPrioritariaComAgendamento() {
        var veiculo = composicao("OCUPADO", "Viagem", -23.55, "2026-10-20T06:00", null, saoPaulo);
        var previsao = motor.preverDisponibilidade(veiculo, "2026-10-20T06:00", null,
                new Ajuste("2026-10-20T07:00", saoPaulo, null, "2026-10-21T10:00"));
        assertEquals("manual", previsao.tipo()); assertEquals("2026-10-20T07:00", previsao.disponivelEm());
    }
    @Test void ajusteVazioMantemPrevisaoAutomatica() {
        var veiculo = composicao("OCUPADO", "Viagem", -23.55, "2026-10-20T08:00", null, saoPaulo);
        assertEquals(motor.preverDisponibilidade(veiculo, "2026-10-20T08:00", null, null),
                motor.preverDisponibilidade(veiculo, "2026-10-20T08:00", null, new Ajuste(null, null, null, null)));
    }
    @Test void agendamentoInvalidoNaoEViradoEmAtendimentoImediato() {
        assertThrows(IllegalArgumentException.class, () -> motor.preverDisponibilidade(vazio(), "2026-10-20T08:00",
                null, new Ajuste(null, null, null, "data errada")));
    }
    @Test void esperaAgendadaPodeInviabilizarNovaColeta() {
        var veiculo = composicao("OCUPADO", "Viagem", -23.55, "2026-10-20T08:00", null, saoPaulo);
        var resultado = motor.avaliarComposicoes(carga(28000, "2026-10-20T12:00"), List.of(veiculo),
                Map.of("OCUPADO", new Ajuste(null, null, null, "2026-10-20T11:00")), null);
        assertTrue(resultado.aprovadas().isEmpty());
        assertTrue(resultado.excluidas().getFirst().motivos().getFirst().contains("depois"));
    }
    @Test void contratoJsonAceitaAjusteAntigoEAgendamento() throws Exception {
        var mapeador = new ObjectMapper();
        assertNull(mapeador.readValue("{\"disponivelEm\":null,\"local\":null}", Ajuste.class).cargaAgendadaEm());
        var ajuste = new Ajuste(null, null, null, "2026-10-20T10:00");
        assertEquals(ajuste, mapeador.readValue(mapeador.writeValueAsString(ajuste), Ajuste.class));
    }
    private List<MediaAtendimento> mediasAtendimento() {
        return List.of(new MediaAtendimento("carga", "Cliente", "Sao Paulo/SP", 20, 0, 0, 0, 20, 1d, 1d, null, null),
                new MediaAtendimento("descarga", "Cliente", "Ponta Grossa/PR", 20, 0, 0, 0, 20, .5, .5, null, null));
    }
    @Test void operacaoFuturaUsaHistoricoSemInventarFila() {
        var veiculo = composicao("OCUPADO", "Viagem", -23.55, "2026-10-20T08:00", null, pontaGrossa);
        var previsao = motor.preverDisponibilidade(veiculo, "2026-10-20T08:00", null, null, mediasAtendimento());
        assertEquals(.5, previsao.etapas().getLast().horas());
        assertTrue(previsao.etapas().getLast().nome().contains("histórica"));
        assertTrue(previsao.avisos().stream().anyMatch(aviso -> aviso.contains("Fila e espera operacional no cliente não estimadas")));
    }
    @Test void atendimentoIniciadoNaoUsaMediaTotalComoRestante() {
        for (String situacao : List.of("Carga", "Descarga")) {
            var veiculo = composicao("OCUPADO", situacao, -23.55, "2026-10-20T08:00", saoPaulo, pontaGrossa);
            var previsao = motor.preverDisponibilidade(veiculo, "2026-10-20T08:00", null, null, mediasAtendimento());
            Etapa etapa = situacao.equals("Carga") ? previsao.etapas().getFirst() : previsao.etapas().getLast();
            assertEquals(2, etapa.horas()); assertTrue(etapa.nome().contains("restante configurada"));
        }
    }
    @Test void novaCargaResolveAutomaticoMasPreservaTempoManual() {
        var origem = new Local(null, "Cliente", "Sao Paulo/SP", -23.55, -46.63, "cliente", "");
        var destino = new Local(null, "Cliente", "Ponta Grossa/PR", -25.09, -50.16, "cliente", "");
        var carga = new Carga("Teste", origem, destino, "2026-10-20T08:00", "2026-10-21T10:00", 28000d, 4000d, null, 4d);
        var resultado = motor.avaliarComposicoes(carga, List.of(vazio()), null, null, mediasAtendimento());
        assertEquals("historico", resultado.tempoCarga().fonte()); assertEquals(1, resultado.tempoCarga().horas());
        assertEquals("manual", resultado.tempoDescarga().fonte()); assertEquals(4, resultado.tempoDescarga().horas());
    }
    @Test void agendamentoEsperaAntesDoAtendimentoHistorico() {
        var veiculo = composicao("OCUPADO", "Viagem", -23.55, "2026-10-20T06:00", null, saoPaulo);
        var medias = List.of(new MediaAtendimento("descarga", "Cliente", "Ponta Grossa/PR", 20, 0, 0, 0, 20, .5, .5, null, null));
        var previsao = motor.preverDisponibilidade(veiculo, "2026-10-20T06:00", null,
                new Ajuste(null, null, null, "2026-10-20T10:00"), medias);
        assertEquals("2026-10-20T10:30", previsao.disponivelEm());
        assertEquals(4, previsao.etapas().get(1).horas()); assertEquals(.5, previsao.etapas().getLast().horas());
    }
    @Test void temposInvalidosSaoRecusados() {
        assertThrows(IllegalArgumentException.class, () -> motor.preverDisponibilidade(vazio(), "2026-10-20T08:00",
                new Parametros(null, null, null, null, null, -1d, null), null));
    }
}
