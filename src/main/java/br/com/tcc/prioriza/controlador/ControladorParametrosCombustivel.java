package br.com.tcc.prioriza.controlador;

import br.com.tcc.prioriza.servico.ServicoParametrosCombustivel;
import br.com.tcc.prioriza.servico.ServicoParametrosCombustivel.Configuracao;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/parametros/combustivel")
public class ControladorParametrosCombustivel {
    private final ServicoParametrosCombustivel servico;
    public ControladorParametrosCombustivel(ServicoParametrosCombustivel servico) { this.servico=servico; }
    @GetMapping public Configuracao consultar() { return servico.consultar(); }
    @PutMapping public Configuracao salvar(@RequestBody Configuracao configuracao) { return servico.salvar(configuracao); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> entrada(IllegalArgumentException erro) {
        return ResponseEntity.badRequest().body(Map.of("erro",erro.getMessage()));
    }
}
