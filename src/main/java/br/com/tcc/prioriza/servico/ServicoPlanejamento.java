package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Modelos.*;
import br.com.tcc.prioriza.repositorio.RepositorioOperacional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static br.com.tcc.prioriza.servico.FuncoesDados.*;

@Service
public class ServicoPlanejamento {
    private final RepositorioOperacional repositorio;
    private final MotorRecomendacao motor;
    public ServicoPlanejamento(RepositorioOperacional repositorio, MotorRecomendacao motor) {
        this.repositorio = repositorio; this.motor = motor;
    }
    public void validarCarga(Carga carga) {
        exigir(carga != null, "Informe a carga prevista.");
        exigir(!texto(carga.identificacao()).isBlank() && carga.identificacao().length() <= 100, "Informe uma identificação com até 100 caracteres.");
        exigir(motor.validarCoordenadas(carga.origem()), "Selecione uma origem com coordenadas válidas.");
        exigir(motor.validarCoordenadas(carga.destino()), "Selecione um destino com coordenadas válidas para salvar a carga.");
        exigir(converterData(carga.coleta()) != null, "Informe a data e o horário previstos da coleta.");
        exigir(carga.peso() != null && Double.isFinite(carga.peso()) && carga.peso() > 0, "Informe o peso da carga, maior que zero.");
        exigir(carga.frete() == null || (Double.isFinite(carga.frete()) && carga.frete() >= 0), "O frete não pode ser negativo ou inválido.");
        for (Double tempo : new Double[]{carga.tempoCarga(), carga.tempoDescarga()})
            exigir(tempo == null || (Double.isFinite(tempo) && tempo >= 0 && tempo <= 720), "Os tempos devem estar entre 0 e 720 horas.");
    }
    @Transactional public CargaPlanejada criar(PedidoPlanejamento pedido) {
        exigir(pedido != null, "Informe a carga prevista."); validarCarga(pedido.carga());
        return repositorio.criarPlanejamento(pedido.carga());
    }
    @Transactional public CargaPlanejada atualizar(UUID id, PedidoPlanejamento pedido) {
        exigir(pedido != null, "Informe a carga prevista."); validarCarga(pedido.carga()); validarVersao(pedido.versao());
        return repositorio.atualizarPlanejamento(id, pedido.versao(), pedido.carga());
    }
    @Transactional public CargaPlanejada cancelar(UUID id, Integer versao) {
        validarVersao(versao); return repositorio.cancelarPlanejamento(id, versao);
    }
    private void validarVersao(Integer versao) { exigir(versao != null && versao > 0, "Atualize a lista para obter a versão atual da carga."); }
    private void exigir(boolean condicao, String mensagem) { if (!condicao) throw new IllegalArgumentException(mensagem); }
}
