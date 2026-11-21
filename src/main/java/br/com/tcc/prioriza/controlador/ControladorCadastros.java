package br.com.tcc.prioriza.controlador;

import br.com.tcc.prioriza.modelo.Cadastros.*;
import br.com.tcc.prioriza.repositorio.RepositorioCadastros;
import br.com.tcc.prioriza.servico.ServicoCadastros;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/cadastros")
public class ControladorCadastros {
    private final RepositorioCadastros repositorio;private final ServicoCadastros servico;
    public ControladorCadastros(RepositorioCadastros repositorio,ServicoCadastros servico){this.repositorio=repositorio;this.servico=servico;}
    @GetMapping("/clientes") public Pagina<Cliente> clientes(@RequestParam(defaultValue="") String busca,@RequestParam(defaultValue="ATIVOS") String situacao,@RequestParam(defaultValue="false") boolean revisao,@RequestParam(defaultValue="0") int pagina){ServicoCadastros.validarConsulta(situacao,pagina);return repositorio.clientes(busca,situacao,revisao,pagina);}
    @GetMapping("/veiculos") public Pagina<Veiculo> veiculos(@RequestParam(defaultValue="") String busca,@RequestParam(defaultValue="ATIVOS") String situacao,@RequestParam(defaultValue="false") boolean revisao,@RequestParam(defaultValue="0") int pagina){ServicoCadastros.validarConsulta(situacao,pagina);return repositorio.veiculos(busca,situacao,revisao,pagina);}
    @PostMapping("/clientes") public Cliente criar(@RequestBody PedidoCliente pedido){return servico.criarCliente(pedido);}
    @PutMapping("/clientes/{id}") public Cliente editar(@PathVariable String id,@RequestBody PedidoCliente pedido){return servico.editarCliente(id,pedido);}
    @PostMapping("/veiculos") public Veiculo criar(@RequestBody PedidoVeiculo pedido){return servico.criarVeiculo(pedido);}
    @PutMapping("/veiculos/{placa}") public Veiculo editarVeiculo(@PathVariable String placa,@RequestBody PedidoVeiculo pedido){return servico.editarVeiculo(placa,pedido);}
    @PostMapping("/{tipo}/{id}/situacao") public Object situacao(@PathVariable String tipo,@PathVariable String id,@RequestBody PedidoSituacao pedido){return servico.situacao(tipo,id,pedido);}
    @PostMapping("/{tipo}/{id}/revisar") public Object revisar(@PathVariable String tipo,@PathVariable String id,@RequestBody PedidoRevisao pedido){return servico.revisar(tipo,id,pedido);}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Map<String,String>> entrada(IllegalArgumentException erro){return ResponseEntity.badRequest().body(Map.of("erro",erro.getMessage()));}
}
