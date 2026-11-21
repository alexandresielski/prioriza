package br.com.tcc.prioriza.controlador;
import br.com.tcc.prioriza.servico.ServicoCenarios;
import br.com.tcc.prioriza.servico.ServicoCenarios.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;
@RestController
@RequestMapping("/api")
public class ControladorCenarios {
 private final ServicoCenarios servico;
 public ControladorCenarios(ServicoCenarios servico){this.servico=servico;}
 @PostMapping("/cenarios") public ResultadoCenario simular(@RequestBody Pedido pedido){return servico.simular(pedido);}
 @GetMapping("/cenarios") public List<Map<String,Object>> listar(){return servico.listar();}
 @GetMapping("/reservas") public List<Map<String,Object>> reservas(){return servico.listarReservas();}
 public record PedidoCancelamentoReservas(List<UUID> reservas) { }
 @PostMapping("/reservas/cancelar-lote") public Map<String,Object> cancelarReservas(@RequestBody PedidoCancelamentoReservas pedido){servico.cancelarReservas(pedido.reservas());return Map.of("canceladas",pedido.reservas().size());}
 @PostMapping("/reservas/{id}/cancelar") public Map<String,Object> cancelarReserva(@PathVariable UUID id){servico.cancelarReserva(id);return Map.of("situacao","CANCELADA");}
 @PostMapping("/cenarios/{id}/confirmar") public Map<String,Object> confirmar(@PathVariable UUID id){return servico.confirmar(id);}
 @PostMapping("/cenarios/{id}/liberar") public Map<String,Object> liberar(@PathVariable UUID id){servico.liberar(id);return Map.of("situacao","CANCELADO");}
 @PostMapping("/atrasos") public Map<String,Object> registrar(@RequestBody Atraso atraso){servico.registrarAtraso(atraso);return Map.of("registrado",true);}
 @GetMapping("/atrasos") public List<Map<String,Object>> atrasos(){return servico.atrasos();}
 @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Map<String,String>> erro(IllegalArgumentException erro){return ResponseEntity.badRequest().body(Map.of("erro",erro.getMessage()));}
}
