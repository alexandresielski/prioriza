package br.com.tcc.prioriza.controlador;

import br.com.tcc.prioriza.modelo.Modelos.*;
import br.com.tcc.prioriza.repositorio.RepositorioOperacional;
import br.com.tcc.prioriza.servico.ServicoPlanejamento;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;

@RestController
@RequestMapping("/api/planejamento")
public class ControladorPlanejamento {
    private final RepositorioOperacional repositorio;
    private final ServicoPlanejamento servico;
    public ControladorPlanejamento(RepositorioOperacional repositorio, ServicoPlanejamento servico) {
        this.repositorio = repositorio; this.servico = servico;
    }
    @GetMapping public List<CargaPlanejada> listar() { return repositorio.listarPlanejamento(); }
    @PostMapping public CargaPlanejada criar(@RequestBody PedidoPlanejamento pedido) { return servico.criar(pedido); }
    @PutMapping("/{id}") public CargaPlanejada atualizar(@PathVariable UUID id, @RequestBody PedidoPlanejamento pedido) { return servico.atualizar(id, pedido); }
    @PostMapping("/{id}/cancelar") public CargaPlanejada cancelar(@PathVariable UUID id, @RequestBody PedidoCancelamento pedido) { return servico.cancelar(id, pedido.versao()); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Map<String, String>> tratarEntrada(IllegalArgumentException erro) {
        return ResponseEntity.badRequest().body(Map.of("erro", erro.getMessage()));
    }
}
