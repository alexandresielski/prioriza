package br.com.tcc.prioriza.controlador;

import br.com.tcc.prioriza.servico.ImportadorCargasPrevistas;
import br.com.tcc.prioriza.servico.ImportadorCargasPrevistas.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/planejamento/importacao")
public class ControladorImportacaoCargas {
    private final ImportadorCargasPrevistas servico;
    public ControladorImportacaoCargas(ImportadorCargasPrevistas servico) { this.servico = servico; }
    @GetMapping("/modelo") public ResponseEntity<byte[]> modelo() {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Modelo_cargas_previstas.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(servico.modelo());
    }
    @PostMapping("/conferir") public Conferencia conferir(@RequestBody Pedido pedido) { return servico.conferir(pedido); }
    @PostMapping public Importadas importar(@RequestBody Pedido pedido) { return servico.importar(pedido); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Map<String,String>> entrada(IllegalArgumentException erro) {
        return ResponseEntity.badRequest().body(Map.of("erro", erro.getMessage()));
    }
}
