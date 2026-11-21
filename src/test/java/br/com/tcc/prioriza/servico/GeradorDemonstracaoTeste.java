package br.com.tcc.prioriza.servico;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GeradorDemonstracaoTeste {
    private static final String ALFA="Cliente Alfa Demonstracao", BETA="Cliente Beta Demonstracao";
    private static final Path PASTA=Path.of(System.getProperty("basedir","."),"target","demonstracao");
    private void planilha(Path destino, Object[][] linhas, boolean segundaAba) throws Exception {
        Files.createDirectories(destino.getParent());
        try(var livro=new XSSFWorkbook()) {
            var aba=livro.createSheet("Dados ficticios");
            if(segundaAba)aba.createRow(0).createCell(0).setCellValue("Historico inteiramente ficticio");
            for(int i=0;i<linhas.length;i++) {
                var linha=aba.createRow(i+(segundaAba?1:0));
                for(int j=0;j<linhas[i].length;j++) {
                    var celula=linha.createCell(j);var valor=linhas[i][j];
                    if(valor instanceof Number n)celula.setCellValue(n.doubleValue());
                    else celula.setCellValue(Objects.toString(valor,""));
                }
            }
            try(var saida=Files.newOutputStream(destino)){livro.write(saida);}
        }
    }
    @Test void gerarPlanilhasInteiramenteFicticias() throws Exception {
        var bases=PASTA.resolve("bases");var hoje=LocalDate.now();String posicao=hoje.atTime(8,0).toString();
        planilha(bases.resolve("RelacaoClientesCoordenadas.xlsx"),new Object[][]{
            {"Razão social","Nome fantasia","Município","U.F.","Latitude","Longitude","Referência geográfica endereço padrão"},
            {ALFA,ALFA,"São Paulo","SP",-23.5505,-46.6333,"Local ficticio Alfa"},
            {BETA,BETA,"Campinas","SP",-22.9056,-47.0608,"Local ficticio Beta"}},false);
        planilha(bases.resolve("RelacaoVeiculos.xlsx"),new Object[][]{
            {"Placa","Classificação","Frota","Agrupamento","Cap. PBT","Tara","Número de Eixos"},
            {"DMO3003","CAVALO MECANICO","Demonstracao","CAVALO",23000,8000,3},
            {"DMO3004","SIDER","Demonstracao","SIDER",30000,7000,3},
            {"DMO3005","CAVALO MECANICO","Demonstracao","CAVALO",23000,8000,3},
            {"DMO3006","SIDER","Demonstracao","SIDER",30000,7000,3},
            {"DMO3007","CAVALO MECANICO","Demonstracao","CAVALO",23000,8000,3},
            {"DMO3008","SIDER","Demonstracao","SIDER",30000,7000,3}},false);
        planilha(bases.resolve("RelacaoPosicaoAtual.xlsx"),new Object[][]{
            {"Placa tração","Placa carreta","Grupo carreta","S. carreta","Engatada","Município referência","Latitude","Longitude","Última posição","Origem","Remetente","Destino","Destinatário"},
            {"DMO3003","DMO3004","SIDER","Vazio","Sim","São Paulo/SP",-23.5505,-46.6333,posicao,"São Paulo/SP",ALFA,"Campinas/SP",BETA},
            {"DMO3005","DMO3006","SIDER","Descarga","Sim","Campinas/SP",-22.9056,-47.0608,posicao,"São Paulo/SP",ALFA,"Campinas/SP",BETA},
            {"DMO3007","DMO3008","SIDER","Viagem","Sim","Campinas/SP",-22.9056,-47.0608,posicao,"Campinas/SP",BETA,"São Paulo/SP",ALFA}},false);
        var viagens=new Object[31][];
        viagens[0]=new Object[]{"Nº Viagem","Placa Tração","Placa Reboque","Remetente","Destinatário","Carregamento","Destino","Grupo","Tempo Carga","Tempo Descarga","Dt. Carga (I)","Dt. Carga (T)","Dt. Descarga (I)","Dt. Descarga (T)","Peso Total","Frete Total","KM Vazio","KM Carregado"};
        for(int i=1;i<viagens.length;i++) {
            var dia=hoje.minusDays(i);
            viagens[i]=new Object[]{"VIAGEM-DEMO-"+i,"DMO3003","DMO3004",ALFA,BETA,"São Paulo/SP","Campinas/SP","SIDER","02:00","01:30",dia.atTime(8,0).toString(),dia.atTime(10,0).toString(),dia.atTime(12,0).toString(),dia.atTime(13,30).toString(),28000,2500,10,110};
        }
        planilha(bases.resolve("RelacaoViagens.xlsx"),viagens,true);
        planilha(bases.resolve("RelacaoCargas.xlsx"),new Object[][]{
            {"Nº carga","Coleta","Entrega","Peso (kg)","Nº documento"},
            {"HIST-DEMO-1",ALFA,BETA,28000,"DOC-DEMO-1"},
            {"HIST-DEMO-1",ALFA,BETA,28000,"DOC-DEMO-COMPLEMENTAR"}},false);
        planilha(PASTA.resolve("cargas-previstas.xlsx"),new Object[][]{
            ImportadorCargasPrevistas.COLUNAS.toArray(),
            {"CARGA-DEMO-1",ALFA,"São Paulo/SP",BETA,"Campinas/SP",hoje.plusDays(1).atTime(10,0).toString(),28000,2500,"Sim"},
            {"CARGA-DEMO-2",ALFA,"São Paulo/SP",BETA,"Campinas/SP",hoje.plusDays(1).atTime(12,0).toString(),26000,2300,"Nao"}},false);
        try(var arquivos=Files.list(bases)){assertEquals(5,arquivos.filter(p->p.toString().endsWith(".xlsx")).count());}
        assertTrue(Files.size(PASTA.resolve("cargas-previstas.xlsx"))>0);
    }
}
