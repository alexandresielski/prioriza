package br.com.tcc.prioriza.controlador;

import br.com.tcc.prioriza.modelo.Modelos.*;
import br.com.tcc.prioriza.repositorio.RepositorioOperacional;
import br.com.tcc.prioriza.servico.ImportadorPlanilhas;
import br.com.tcc.prioriza.servico.MotorRecomendacao;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ControladorOperacional implements ApplicationRunner {
    private final RepositorioOperacional repositorio;
    private final ImportadorPlanilhas importador;
    private final MotorRecomendacao motor;
    private final JdbcTemplate banco;
    private volatile boolean pronto;

    public ControladorOperacional(RepositorioOperacional repositorio, ImportadorPlanilhas importador, MotorRecomendacao motor, JdbcTemplate banco) {
        this.repositorio = repositorio; this.importador = importador; this.motor = motor; this.banco = banco;
    }
    @Override public void run(ApplicationArguments argumentos) throws Exception {
        importador.carregarBasesIniciais(); importador.atualizarMediasIniciais(); importador.atualizarIdentificadoresClientes(); pronto = true;
    }
    @GetMapping("/tempos-atendimento") public List<MediaAtendimento> listarMedias() { return repositorio.listarMedias(); }
    @GetMapping("/resumo") public Resumo obterResumo() { return repositorio.obterResumo(importador.obterPastaBases()); }
    @GetMapping("/locais") public List<Local> buscarLocais(@RequestParam(defaultValue = "") String q) { return repositorio.buscarLocais(q); }
    @GetMapping("/cargas") public List<CargaHistorica> buscarCargas(@RequestParam(defaultValue = "") String q) { return repositorio.buscarCargas(q); }
    @GetMapping("/carga/{numero}") public CargaHistorica obterCarga(@PathVariable String numero) { return repositorio.obterCarga(numero); }
    @GetMapping("/simulacoes") public List<Map<String, Object>> listarSimulacoes() { return repositorio.listarSimulacoes(); }
    @GetMapping("/saude") public Map<String, Object> conferirSaude() {
        return Map.of("aplicacao", "Java 21 / Spring Boot", "banco", "PostgreSQL", "conectado", banco.queryForObject("SELECT 1", Integer.class) == 1, "pronto", pronto);
    }
    @PostMapping("/simular") public Resultado simular(@RequestBody PedidoSimulacao pedido) {
        Resultado resultado = motor.avaliarComposicoes(pedido.carga(), repositorio.listarComposicoes(), pedido.ajustes(), pedido.parametros(), repositorio.listarMedias(), repositorio.listarRecursosReservados());
        repositorio.salvarSimulacao(pedido, resultado);
        return resultado;
    }
    @PostMapping("/disponibilidades") public Map<String, Previsao> calcularDisponibilidades(@RequestBody PedidoDisponibilidade pedido) {
        Map<String, Previsao> previsoes = new LinkedHashMap<>();
        List<MediaAtendimento> medias = repositorio.listarMedias();
        for (Composicao item : repositorio.listarComposicoes()) if (item.grupo().equals("SIDER")) {
            previsoes.put(item.placa(), motor.preverDisponibilidade(item, pedido.referencia(), pedido.parametros(),
                    pedido.ajustes() == null ? null : pedido.ajustes().get(item.placa()), medias));
        }
        return previsoes;
    }
    @PostMapping("/importar") public Importacao importar(@RequestBody PedidoImportacao pedido) throws IOException {
        if (pedido.nome() == null || pedido.conteudo() == null) throw new IllegalArgumentException("Informe nome e conteúdo da planilha.");
        List<String> avisos = importador.importar(pedido.nome(), Base64.getDecoder().decode(pedido.conteudo()));
        return new Importacao(obterResumo(), avisos);
    }
    @PostMapping("/recarregar") public Resumo recarregar() throws IOException { importador.recarregarBases(); return obterResumo(); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Map<String, String>> tratarEntrada(IllegalArgumentException erro) {
        return ResponseEntity.badRequest().body(Map.of("erro", erro.getMessage() == null ? "Entrada inválida." : erro.getMessage()));
    }
    @ExceptionHandler(IOException.class) public ResponseEntity<Map<String, String>> tratarPlanilha(IOException erro) {
        return ResponseEntity.badRequest().body(Map.of("erro", "Não foi possível ler a planilha XLSX."));
    }
    @ExceptionHandler(Exception.class) public ResponseEntity<Map<String, String>> tratarFalha(Exception erro) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Falha na operacao", erro);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("erro", "Não foi possível concluir a operação. Consulte o registro do servidor."));
    }
}
