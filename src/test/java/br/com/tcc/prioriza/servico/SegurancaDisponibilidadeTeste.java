package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Modelos.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SegurancaDisponibilidadeTeste {
    private final MotorRecomendacao motor = new MotorRecomendacao();
    private final Local cliente = new Local("cliente-1", "Cliente A", "Sao Paulo/SP", -23.55, -46.63, "cliente", "");
    private final Local outro = new Local("cliente-2", "Cliente B", "Sao Paulo/SP", -23.551, -46.63, "cliente", "");
    private Composicao veiculo(String placa, String situacao, double latitude, Local destino) {
        return new Composicao(placa, "CAR" + placa, "SIDER", situacao, true, true, "Sao Paulo/SP", latitude, -46.63,
            "2026-11-17T08:00", "Sao Paulo/SP", "Cliente A", "Sao Paulo/SP", "Cliente B", 32000d, 6d, cliente, destino);
    }
    private Carga carga(Local origem, String coleta) { return new Carga("Ensaio", origem, outro, "2026-11-17T08:00", coleta, 26000d, null, null, null); }
    @Test void descargaNoMesmoClienteZeraDeslocamentoMasMantemTempoRestante() {
        var descarga = veiculo("DESCARGA", "Descarga", -23.57, cliente);
        var viagem = veiculo("VIAGEM", "Viagem", -23.65, outro);
        var resultado = motor.avaliarComposicoes(carga(cliente, "2026-11-18T11:00"), List.of(viagem, descarga), null, null);
        var primeira = resultado.aprovadas().getFirst();
        assertEquals("DESCARGA", primeira.composicao().placa()); assertEquals(0, primeira.distancia());
        assertEquals("2026-11-17T10:00", primeira.chegada()); assertEquals(100d, primeira.notas().get("seguranca"));
        assertTrue(primeira.avisos().stream().anyMatch(aviso -> aviso.contains("mesmo local")));
        assertEquals(40d, resultado.aprovadas().get(1).notas().get("seguranca"));
        assertEquals(1d, resultado.pesos().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-10);
        assertEquals(primeira.nota(), primeira.contribuicoes().values().stream().mapToDouble(Double::doubleValue).sum());
    }
    @Test void mesmaCidadeOuReferenciaMunicipalNaoSignificaMesmoCliente() {
        var descarga = veiculo("DESCARGA", "Descarga", -23.57, outro);
        var resultado = motor.avaliarComposicoes(carga(cliente, "2026-11-18T11:00"), List.of(descarga), null, null);
        assertTrue(resultado.aprovadas().getFirst().distancia() > 0); assertEquals(80d, resultado.aprovadas().getFirst().notas().get("seguranca"));
        var municipio = new Local("municipio-SP", "Cliente A", "Sao Paulo/SP", -23.55, -46.63, "municipio", "");
        resultado = motor.avaliarComposicoes(carga(municipio, "2026-11-18T11:00"), List.of(veiculo("D", "Descarga", -23.57, cliente)), null, null);
        assertTrue(resultado.aprovadas().getFirst().distancia() > 0);
    }
    @Test void segurancaNaoAprovaAtrasadoNemSobrepeso() {
        var descarga = veiculo("D", "Descarga", -23.57, cliente);
        assertTrue(motor.avaliarComposicoes(carga(cliente, "2026-11-17T09:00"), List.of(descarga), null, null).aprovadas().isEmpty());
        var pesada = new Carga("Ensaio", cliente, outro, "2026-11-17T08:00", "2026-11-18T11:00", 99999d, null, null, null);
        assertTrue(motor.avaliarComposicoes(pesada, List.of(descarga), null, null).aprovadas().isEmpty());
    }
    @Test void cadastroDuplicadoDoMesmoClienteNoMesmoPontoTambemReconhecido() {
        var duplicado = new Local("cliente-3", cliente.nome(), cliente.cidade(), cliente.latitude(), cliente.longitude(), "cliente", "");
        var resultado = motor.avaliarComposicoes(carga(duplicado, "2026-11-18T11:00"), List.of(veiculo("D", "Descarga", -23.57, cliente)), null, null);
        assertEquals(0d, resultado.aprovadas().getFirst().distancia()); assertEquals(100d, resultado.aprovadas().getFirst().notas().get("seguranca"));
    }
    @Test void foraDaFaixaDeCincoQuilometrosNaoRecebeBonus() {
        var perto = veiculo("PERTO", "Vazio", -23.55, null); var longe = veiculo("LONGE", "Vazio", -23.65, null);
        var resultado = motor.avaliarComposicoes(carga(cliente, "2026-11-18T11:00"), List.of(perto, longe), null, null);
        assertEquals(0d, resultado.aprovadas().get(1).notas().get("seguranca"));
    }
    @Test void liberacaoManualNaoHerdeSegurancaDaSituacaoNemZereDistancia() {
        var descarga = veiculo("D", "Descarga", -23.57, cliente);
        var resultado = motor.avaliarComposicoes(carga(cliente, "2026-11-18T11:00"), List.of(descarga), Map.of("D", new Ajuste("2026-11-17T10:00", outro)), null);
        assertEquals(50d, resultado.aprovadas().getFirst().notas().get("seguranca")); assertTrue(resultado.aprovadas().getFirst().distancia() > 0);
    }
}
