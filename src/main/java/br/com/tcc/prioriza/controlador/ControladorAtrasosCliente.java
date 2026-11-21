package br.com.tcc.prioriza.controlador;

import br.com.tcc.prioriza.servico.ServicoAtrasosCliente;
import br.com.tcc.prioriza.servico.ServicoAtrasosCliente.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/cadastros/clientes/{id}/atrasos")
public class ControladorAtrasosCliente {
    private final ServicoAtrasosCliente servico;
    public ControladorAtrasosCliente(ServicoAtrasosCliente servico) { this.servico=servico; }
    @GetMapping public Historico consultar(@PathVariable String id) { return servico.consultar(id); }
    @PostMapping public Map<String,Boolean> registrar(@PathVariable String id,@RequestBody PedidoAtraso pedido) {
        servico.registrar(id,pedido);return Map.of("registrado",true);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> entrada(IllegalArgumentException erro) {
        return ResponseEntity.badRequest().body(Map.of("erro",erro.getMessage()));
    }
}
