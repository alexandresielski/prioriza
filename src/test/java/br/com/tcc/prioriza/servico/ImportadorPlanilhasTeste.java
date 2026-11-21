package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Modelos.CargaHistorica;
import br.com.tcc.prioriza.repositorio.RepositorioOperacional;
import br.com.tcc.prioriza.repositorio.RepositorioOperacional.RegistroLocal;
import br.com.tcc.prioriza.repositorio.RepositorioOperacional.PosicaoBanco;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.io.ByteArrayOutputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImportadorPlanilhasTeste {
    private final RepositorioOperacional repositorio = mock(RepositorioOperacional.class);
    private final ImportadorPlanilhas importador = new ImportadorPlanilhas(repositorio, new MotorRecomendacao(), new ObjectMapper(), "Bases");

    private byte[] planilha(Object[][] linhas) throws Exception {
        try (var arquivo = new XSSFWorkbook(); var saida = new ByteArrayOutputStream()) {
            var aba = arquivo.createSheet("Dados");
            for (int indice = 0; indice < linhas.length; indice++) {
                var linha = aba.createRow(indice);
                for (int coluna = 0; coluna < linhas[indice].length; coluna++) {
                    Object valor = linhas[indice][coluna];
                    if (valor == null) continue;
                    var celula = linha.createCell(coluna);
                    if (valor instanceof Number numero) celula.setCellValue(numero.doubleValue());
                    else celula.setCellValue(valor.toString());
                }
            }
            arquivo.write(saida); return saida.toByteArray();
        }
    }
    @Test void nomeDesconhecidoNaoAlteraBanco() {
        assertThrows(IllegalArgumentException.class, () -> importador.importar("outro.xlsx", new byte[0]));
        verifyNoInteractions(repositorio);
    }
    @Test void layoutInvalidoNaoAlteraBanco() throws Exception {
        byte[] dados = planilha(new Object[][]{{"Coluna errada"}, {"Valor"}});
        assertThrows(IllegalArgumentException.class, () -> importador.importar("RelaçãoVeículos.xlsx", dados));
        verifyNoInteractions(repositorio);
    }
    @Test @SuppressWarnings("unchecked") void documentosComplementaresNaoDuplicamPeso() throws Exception {
        byte[] dados = planilha(new Object[][]{{"Nº carga", "Coleta", "Entrega", "Peso (kg)", "Nº documento"},
                {10, "Origem", "Destino", 28000, "CTE-1"}, {10, "Origem", "Destino", 28000, "CTE-COMPLEMENTAR"}});
        importador.importar("RelaçãoCargas.xlsx", dados);
        ArgumentCaptor<List<CargaHistorica>> argumento = ArgumentCaptor.forClass(List.class);
        verify(repositorio).substituirCargas(argumento.capture());
        var cargas = argumento.getValue();
        assertEquals(1, cargas.size()); assertEquals(28000, cargas.getFirst().peso());
        assertEquals(2, cargas.getFirst().linhas()); assertTrue(cargas.getFirst().revisar());
        assertEquals(2, cargas.getFirst().documentos().size());
    }
    @Test @SuppressWarnings("unchecked") void clienteSemPontoUsaMunicipioIdentificado() throws Exception {
        byte[] dados = planilha(new Object[][]{{"Razão social", "Nome fantasia", "Latitude", "Longitude", "Município", "U.F."},
                {"Cliente A", "A", -23.5, -46.6, "Sao Paulo", "SP"}, {"Cliente B", "B", null, null, "Sao Paulo", "SP"}});
        importador.importar("RelaçãoClientesCoordenadas.xlsx", dados);
        ArgumentCaptor<List<RegistroLocal>> argumento = ArgumentCaptor.forClass(List.class);
        verify(repositorio).substituirLocais(argumento.capture());
        var local = argumento.getValue().stream().map(RegistroLocal::local).filter(item -> item.nome().equals("B")).findFirst().orElseThrow();
        assertEquals("municipio", local.fonte()); assertEquals(-23.5, local.latitude());
    }
    private byte[] planilhaViagens() throws Exception {
        return planilha(new Object[][]{{"Relatorio"},
                {"Nº Viagem", "Frete Total", "Remetente", "Destinatário", "Carregamento", "Destino", "Grupo", "Tempo Carga", "Tempo Descarga"},
                {10, 1000, "Cliente A", "Cliente B", "Curitiba/PR", "Sao Paulo/SP", "SIDER", "01:00", "02:00"}});
    }
    @Test void viagensAtualizamHistoricoEMediasSemTocarPosicoes() throws Exception {
        importador.importar("RelaçãoViagens.xlsx", planilhaViagens());
        verify(repositorio).substituirViagens(anyList()); verify(repositorio).substituirMedias(anyList());
        verify(repositorio).marcarMediasAtualizadas(); verify(repositorio, never()).substituirPosicoes(anyList());
        verify(repositorio, never()).substituirVeiculos(anyList());
    }
    @Test void inicializacaoDasMediasUsaArquivoJaPersistido() throws Exception {
        when(repositorio.obterViagensSemMedias()).thenReturn(planilhaViagens());
        importador.atualizarMediasIniciais();
        verify(repositorio).substituirMedias(anyList()); verify(repositorio).marcarMediasAtualizadas();
        verify(repositorio, never()).substituirViagens(anyList()); verify(repositorio, never()).substituirPosicoes(anyList());
    }
    @Test void viagensSemColunasDeTemposNaoApagamMedias() throws Exception {
        var dados = planilha(new Object[][]{{"Relatorio"}, {"Nº Viagem", "Frete Total"}, {10, 1000}});
        assertThrows(IllegalArgumentException.class, () -> importador.importar("RelaçãoViagens.xlsx", dados));
        verifyNoInteractions(repositorio);
    }
    private byte[] planilhaPosicoes(Object[]... registros) throws Exception {
        Object[][] linhas = new Object[registros.length + 1][];
        linhas[0] = new Object[]{"Placa tração", "Placa carreta", "Grupo carreta", "S. carreta", "Latitude", "Última posição", "Engatada", "Remetente"};
        System.arraycopy(registros, 0, linhas, 1, registros.length);
        return planilha(linhas);
    }
    @Test @SuppressWarnings("unchecked") void duplicadaMantemMaisRecenteSemMisturarCarretaEOperacao() throws Exception {
        var antiga = new Object[]{"DMO-3009", "DMO3010", "SIDER", "Viagem", -25d, "21/10/2026 12:03:58", "Sim", "Cliente antigo"};
        var atual = new Object[]{"dmo3009", "", "GERAL", "Vazio", -26d, "21/10/2026 17:52:30", "Nao", ""};
        var avisos = importador.importar("RelaçãoPosiçãoAtual.xlsx", planilhaPosicoes(atual, antiga));
        ArgumentCaptor<List<PosicaoBanco>> argumento = ArgumentCaptor.forClass(List.class);
        verify(repositorio).substituirPosicoes(argumento.capture());
        var posicoes = argumento.getValue(); assertEquals(1, posicoes.size());
        var posicao = posicoes.getFirst(); assertEquals("DMO3009", posicao.placa()); assertEquals("GERAL", posicao.grupo());
        assertEquals("Vazio", posicao.situacao()); assertEquals("", posicao.carreta()); assertEquals("", posicao.remetente());
        assertFalse(posicao.engatada()); assertEquals(-26d, posicao.latitude());
        assertTrue(avisos.getFirst().contains("mais recente"));
    }
    @Test void conflitoNoMesmoHorarioNaoAlteraBase() throws Exception {
        var primeira = new Object[]{"DMO3011", "DMO3012", "SIDER", "Viagem", -25d, "21/10/2026 12:00:00", "Sim", "Cliente"};
        var segunda = new Object[]{"DMO3011", "", "GERAL", "Vazio", -25d, "21/10/2026 12:00:00", "Nao", ""};
        var erro = assertThrows(IllegalArgumentException.class, () -> importador.importar("RelaçãoPosiçãoAtual.xlsx", planilhaPosicoes(primeira, segunda)));
        assertTrue(erro.getMessage().contains("DMO3011")); assertTrue(erro.getMessage().contains("conflitantes"));
        verifyNoInteractions(repositorio);
    }
    @Test @SuppressWarnings("unchecked") void duplicatasIdenticasSemDataSaoConsolidadas() throws Exception {
        var linha = new Object[]{"DMO3011", "DMO3012", "SIDER", "Viagem", -25d, null, "Sim", "Cliente"};
        importador.importar("RelaçãoPosiçãoAtual.xlsx", planilhaPosicoes(linha, linha));
        ArgumentCaptor<List<PosicaoBanco>> argumento = ArgumentCaptor.forClass(List.class);
        verify(repositorio).substituirPosicoes(argumento.capture()); assertEquals(1, argumento.getValue().size());
    }
    @Test void duplicatasSemDataConflitantesSaoRecusadas() throws Exception {
        var primeira = new Object[]{"DMO3011", "DMO3012", "SIDER", "Viagem", -25d, null, "Sim", "Cliente"};
        var segunda = new Object[]{"DMO3011", "DMO3013", "SIDER", "Viagem", -25d, null, "Sim", "Cliente"};
        assertThrows(IllegalArgumentException.class, () -> importador.importar("RelaçãoPosiçãoAtual.xlsx", planilhaPosicoes(primeira, segunda)));
        verifyNoInteractions(repositorio);
    }
    @Test @SuppressWarnings("unchecked") void linhaComDataTemPrioridadeSobreLinhaSemData() throws Exception {
        var semData = new Object[]{"DMO3011", "DMO3012", "SIDER", "Viagem", -25d, null, "Sim", "Cliente"};
        var comData = new Object[]{"DMO3011", "DMO3013", "SIDER", "Viagem", -25d, "21/10/2026 12:00:00", "Sim", "Cliente"};
        importador.importar("RelaçãoPosiçãoAtual.xlsx", planilhaPosicoes(semData, comData));
        ArgumentCaptor<List<PosicaoBanco>> argumento = ArgumentCaptor.forClass(List.class);
        verify(repositorio).substituirPosicoes(argumento.capture()); assertEquals("DMO3013", argumento.getValue().getFirst().carreta());
    }
    @Test void placaAusenteNaoApagaBase() throws Exception {
        var linha = new Object[]{"", "DMO3012", "SIDER", "Viagem", -25d, "21/10/2026 12:00:00", "Sim", "Cliente"};
        assertThrows(IllegalArgumentException.class, () -> importador.importar("RelaçãoPosiçãoAtual.xlsx", planilhaPosicoes(linha)));
        verifyNoInteractions(repositorio);
    }
}
