package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Modelos.*;
import br.com.tcc.prioriza.repositorio.RepositorioOperacional;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicoPlanejamentoTeste {
    private final RepositorioOperacional repositorio = mock(RepositorioOperacional.class);
    private final ServicoPlanejamento servico = new ServicoPlanejamento(repositorio, new MotorRecomendacao());
    private final Local origem = new Local("a", "Cliente A", "Sao Paulo/SP", -23.55, -46.63, "cliente", "");
    private final Local destino = new Local("b", "Cliente B", "Curitiba/PR", -25.42, -49.27, "cliente", "");
    private Carga carga(String nome, Local entrega, Double peso, Double frete, Double tempo) {
        return new Carga(nome, origem, entrega, "2026-10-23T08:00", "2026-10-26T10:00", peso, frete, tempo, null);
    }
    @Test void criaComHistoricoAutomaticoEFreteAindaAusente() {
        var carga = carga("Carga prevista", destino, 28000d, null, null);
        servico.criar(new PedidoPlanejamento(carga, null)); verify(repositorio).criarPlanejamento(carga);
    }
    @Test void atualizaComVersaoEValoresManuais() {
        var id = UUID.randomUUID(); var carga = carga("Carga revista", destino, 28000d, 4000d, 3d);
        servico.atualizar(id, new PedidoPlanejamento(carga, 2)); verify(repositorio).atualizarPlanejamento(id, 2, carga);
    }
    @Test void cancelaSomenteComVersaoConhecida() {
        var id = UUID.randomUUID(); servico.cancelar(id, 1); verify(repositorio).cancelarPlanejamento(id, 1);
        assertThrows(IllegalArgumentException.class, () -> servico.cancelar(id, null));
    }
    @Test void nomeVazioOuLongoNaoGrava() {
        for (String nome : new String[]{" ", "x".repeat(101)})
            assertThrows(IllegalArgumentException.class, () -> servico.criar(new PedidoPlanejamento(carga(nome, destino, 28000d, null, null), null)));
        verifyNoInteractions(repositorio);
    }
    @Test void exigeDestinoParaPlanejamento() {
        assertThrows(IllegalArgumentException.class, () -> servico.criar(new PedidoPlanejamento(carga("Carga", null, 28000d, null, null), null)));
        verifyNoInteractions(repositorio);
    }
    @Test void impedePesoFreteETemposInvalidos() {
        for (Carga carga : new Carga[]{carga("Carga", destino, 0d, null, null), carga("Carga", destino, Double.NaN, null, null),
                carga("Carga", destino, 28000d, -1d, null), carga("Carga", destino, 28000d, null, 721d)})
            assertThrows(IllegalArgumentException.class, () -> servico.criar(new PedidoPlanejamento(carga, null)));
        verifyNoInteractions(repositorio);
    }
    @Test void impedeEdicaoSemVersao() {
        assertThrows(IllegalArgumentException.class, () -> servico.atualizar(UUID.randomUUID(), new PedidoPlanejamento(carga("Carga", destino, 28000d, null, null), null)));
        verifyNoInteractions(repositorio);
    }
    @Test void dataInvalidaNaoGrava() {
        var carga = new Carga("Carga", origem, destino, "2026-10-23T08:00", "invalida", 28000d, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> servico.criar(new PedidoPlanejamento(carga, null)));
        verifyNoInteractions(repositorio);
    }
}
