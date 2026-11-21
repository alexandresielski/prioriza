package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Modelos.*;
import br.com.tcc.prioriza.repositorio.RepositorioOperacional;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImportadorCargasPrevistasTeste {
    private final RepositorioOperacional repositorio = mock(RepositorioOperacional.class);
    private final ImportadorCargasPrevistas servico = new ImportadorCargasPrevistas(repositorio, new ServicoPlanejamento(repositorio, new MotorRecomendacao()));
    private final Local local = new Local("municipio-SAO PAULO", "Sao Paulo", "Sao Paulo/SP", -23.55, -46.63, "municipio", "");
    ImportadorCargasPrevistasTeste() {
        when(repositorio.listarPlanejamento()).thenReturn(List.of());
        when(repositorio.resolverOperacao(anyString(), anyString())).thenReturn(local);
    }
    private ImportadorCargasPrevistas.Pedido pedido(Object[]... dados) throws Exception {
        try (var livro = new XSSFWorkbook(); var saida = new ByteArrayOutputStream()) {
            var aba = livro.createSheet(); var cabecalho = aba.createRow(0);
            for (int i = 0; i < ImportadorCargasPrevistas.COLUNAS.size(); i++) cabecalho.createCell(i).setCellValue(ImportadorCargasPrevistas.COLUNAS.get(i));
            for (int i = 0; i < dados.length; i++) {
                var linha = aba.createRow(i + 1);
                for (int j = 0; j < dados[i].length; j++) {
                    var celula = linha.createCell(j); Object valor = dados[i][j];
                    if (valor instanceof Number numero) celula.setCellValue(numero.doubleValue());
                    else celula.setCellValue(Objects.toString(valor, ""));
                }
            }
            livro.write(saida);
            return new ImportadorCargasPrevistas.Pedido("cargas.xlsx", Base64.getEncoder().encodeToString(saida.toByteArray()), "2026-11-17T08:00");
        }
    }
    private Object[] dados(String id) { return new Object[]{id, "Cliente A", "Sao Paulo/SP", "Cliente B", "Curitiba/PR", "18/11/2026 10:00", 32000d, "", "Sim"}; }
    @Test void confereSemGravarEUsaHistorico() throws Exception {
        var resultado = servico.conferir(pedido(dados("Carga 1")));
        assertEquals(1, resultado.novas()); assertEquals(0, resultado.erros());
        var carga = resultado.linhas().getFirst().carga(); assertEquals(32000d, carga.peso()); assertTrue(carga.obrigatoria()); assertNull(carga.tempoCarga()); assertNull(carga.frete());
        assertEquals(2, resultado.linhas().getFirst().avisos().size()); verify(repositorio, never()).criarPlanejamento(any());
    }
    @Test void importaTodasSomenteDepoisDaValidacao() throws Exception {
        UUID id = UUID.randomUUID();
        when(repositorio.criarPlanejamento(any())).thenAnswer(inv -> new CargaPlanejada(id, 1, "PENDENTE", inv.getArgument(0), "", "", false));
        assertEquals(List.of(id), servico.importar(pedido(dados("Carga 1"))).cargas()); verify(repositorio).bloquearPlanejamento();
    }
    @Test void erroEmUmaLinhaImpedeGravarTodas() throws Exception {
        Object[] invalida = dados("Carga 2"); invalida[6] = 0;
        var pedido = pedido(dados("Carga 1"), invalida);
        assertEquals(1, servico.conferir(pedido).erros()); assertThrows(IllegalArgumentException.class, () -> servico.importar(pedido)); verify(repositorio, never()).criarPlanejamento(any());
    }
    @Test void repetidasNoArquivoSaoApontadas() throws Exception { assertEquals(1, servico.conferir(pedido(dados("A"), dados("A"))).erros()); }
    @Test void reimportacaoIdenticaNaoDuplica() throws Exception {
        var pedido = pedido(dados("A")); var carga = servico.conferir(pedido).linhas().getFirst().carga();
        when(repositorio.listarPlanejamento()).thenReturn(List.of(new CargaPlanejada(UUID.randomUUID(), 1, "PENDENTE", carga, "", "", false)));
        assertEquals(1, servico.conferir(pedido).existentes()); assertThrows(IllegalArgumentException.class, () -> servico.importar(pedido)); verify(repositorio, never()).criarPlanejamento(any());
    }
    @Test void identificacaoExistenteComDivergenciaNaoSobrescreve() throws Exception {
        var carga = servico.conferir(pedido(dados("A"))).linhas().getFirst().carga();
        when(repositorio.listarPlanejamento()).thenReturn(List.of(new CargaPlanejada(UUID.randomUUID(), 1, "PENDENTE", carga, "", "", false)));
        Object[] alterada = dados("A"); alterada[6] = 28000;
        assertEquals(1, servico.conferir(pedido(alterada)).erros());
    }
    @Test void verificaDatasLocaisFreteObrigatoriaEPeso() throws Exception {
        for (int indice : new int[]{5, 6, 7, 8}) {
            Object[] linha = dados("A"); linha[indice] = switch (indice) { case 5 -> "16/11/2026 10:00"; case 6 -> "invalido"; case 7 -> -1; default -> "talvez"; };
            assertEquals(1, servico.conferir(pedido(linha)).erros());
        }
        when(repositorio.resolverOperacao(anyString(), anyString())).thenReturn(null);
        assertEquals(1, servico.conferir(pedido(dados("A"))).erros());
    }
    @Test void aceitaNumeroBrasileiroEClienteVazio() throws Exception {
        Object[] linha = dados("A"); linha[1] = ""; linha[6] = "32.000,00"; linha[7] = "4.500,50";
        var carga = servico.conferir(pedido(linha)).linhas().getFirst().carga(); assertEquals(32000d, carga.peso()); assertEquals(4500.5, carga.frete());
    }
    @Test void aceitaNomeParcialOuCnpjNaMesmaCidadeEUsaPrimeiro() throws Exception {
        var cliente = new Local("cliente-1", "Heineken", "Itu/SP", -23.25, -47.34, "cliente", "");
        when(repositorio.resolverClienteImportacao("Heineken", "Itu/SP")).thenReturn(new RepositorioOperacional.CorrespondenciaLocal(cliente, 2));
        when(repositorio.resolverClienteImportacao("90000000000266", "Itu/SP")).thenReturn(new RepositorioOperacional.CorrespondenciaLocal(cliente, 1));
        for (Object entrada : new Object[]{"Heineken", 90000000000266d}) {
            Object[] linha = dados("A"); linha[1] = entrada; linha[2] = "Itu/SP";
            var resultado = servico.conferir(pedido(linha)); assertEquals(0, resultado.erros());
            assertEquals("cliente-1", resultado.linhas().getFirst().carga().origem().id());
            if (entrada instanceof String) assertTrue(resultado.linhas().getFirst().avisos().stream().anyMatch(aviso -> aviso.contains("primeiro cadastro")));
        }
    }
    @Test void modeloNovoTemColunasESemCargasDeExemplo() throws Exception {
        try (var livro = new XSSFWorkbook(new ByteArrayInputStream(servico.modelo()))) {
            assertEquals(2, livro.getNumberOfSheets()); assertEquals(0, livro.getSheetAt(0).getLastRowNum());
            assertEquals("Coleta", livro.getSheetAt(0).getRow(0).getCell(5).getStringCellValue());
        }
    }
    @Test void aceitaDataNativaDoExcel() throws Exception {
        var pedido = pedido(dados("A"));
        try (var livro = new XSSFWorkbook(new ByteArrayInputStream(Base64.getDecoder().decode(pedido.conteudo()))); var saida = new ByteArrayOutputStream()) {
            var celula = livro.getSheetAt(0).getRow(1).getCell(5);
            celula.setCellValue(java.time.LocalDateTime.of(2026, 11, 18, 10, 0));
            var estilo = livro.createCellStyle(); estilo.setDataFormat(livro.createDataFormat().getFormat("dd/mm/yyyy hh:mm")); celula.setCellStyle(estilo);
            livro.write(saida);
            assertEquals(0, servico.conferir(new ImportadorCargasPrevistas.Pedido("cargas.xlsx", Base64.getEncoder().encodeToString(saida.toByteArray()), pedido.referencia())).erros());
        }
    }
    @Test void rejeitaArquivoVazioEMaisDeCemCargas() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> servico.conferir(pedido()));
        Object[][] linhas = new Object[101][]; for (int i = 0; i < linhas.length; i++) linhas[i] = dados("Carga " + i);
        assertThrows(IllegalArgumentException.class, () -> servico.conferir(pedido(linhas)));
        assertThrows(IllegalArgumentException.class, () -> servico.conferir(new ImportadorCargasPrevistas.Pedido("cargas.csv", "", "2026-11-17T08:00")));
    }
    @Test void preparaArquivosDeEnsaioParaInterface() throws Exception {
        var pasta = java.nio.file.Path.of(System.getProperty("basedir", "."), "target", "ensaio-importacao"); java.nio.file.Files.createDirectories(pasta);
        String prefixo = "VALIDACAO IMPORTACAO " + System.currentTimeMillis();
        var valido = pedido(dados(prefixo + " A"), dados(prefixo + " B"));
        Object[] invalida = dados(prefixo + " ERRO"); invalida[6] = 0;
        var invalido = pedido(dados(prefixo + " C"), invalida);
        java.nio.file.Files.write(pasta.resolve("validas.xlsx"), Base64.getDecoder().decode(valido.conteudo()));
        java.nio.file.Files.write(pasta.resolve("invalidas.xlsx"), Base64.getDecoder().decode(invalido.conteudo()));
        java.nio.file.Files.writeString(pasta.resolve("prefixo.txt"), prefixo);
        Object[][] clientes = new Object[4][];
        Object[] entradas = {"Cliente Alfa", "90.000.000/0001-85", "90000000000185", 90000000000185d};
        for (int i = 0; i < entradas.length; i++) {
            clientes[i] = dados(prefixo + " CLIENTE " + i); clientes[i][1] = entradas[i]; clientes[i][2] = "Itu/SP";
            clientes[i][3] = entradas[i]; clientes[i][4] = "Itu/SP";
        }
        java.nio.file.Files.write(pasta.resolve("clientes.xlsx"), Base64.getDecoder().decode(pedido(clientes).conteudo()));
        assertTrue(java.nio.file.Files.size(pasta.resolve("validas.xlsx")) > 0);
    }
}
