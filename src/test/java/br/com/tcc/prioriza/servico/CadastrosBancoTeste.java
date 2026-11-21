package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Cadastros.*;
import br.com.tcc.prioriza.modelo.Modelos.*;
import br.com.tcc.prioriza.repositorio.*;
import br.com.tcc.prioriza.repositorio.RepositorioOperacional.RegistroLocal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="PRIORIZA_TESTE_URL",matches=".+")
class CadastrosBancoTeste {
    private static JdbcTemplate banco,administracao;
    private static String esquema;
    private RepositorioCadastros repositorio;
    private ServicoCadastros servico;
    private RepositorioOperacional operacional;
    @BeforeAll static void preparar(){
        String url=System.getenv("PRIORIZA_TESTE_URL"),usuario=System.getenv("PRIORIZA_TESTE_USUARIO"),senha=System.getenv("PRIORIZA_TESTE_SENHA");
        esquema="teste_cadastros_"+UUID.randomUUID().toString().replace("-","");
        administracao=new JdbcTemplate(new DriverManagerDataSource(url,usuario,senha));administracao.execute("CREATE SCHEMA "+esquema);
        var fonte=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+esquema,usuario,senha);
        Flyway.configure().dataSource(fonte).schemas(esquema).defaultSchema(esquema).locations("classpath:db/migration").load().migrate();banco=new JdbcTemplate(fonte);
    }
    @AfterAll static void limpar(){if(administracao!=null&&esquema!=null&&esquema.matches("teste_cadastros_[a-f0-9]{32}"))administracao.execute("DROP SCHEMA "+esquema+" CASCADE");}
    @BeforeEach void iniciar(){
        banco.execute("TRUNCATE atraso_cliente,alocacao_planejada,cenario_planejamento,carga_planejada,local_atendimento,veiculo,posicao_veiculo,importacao_base");
        var conversor=new ObjectMapper();repositorio=new RepositorioCadastros(banco,conversor);servico=new ServicoCadastros(repositorio);operacional=new RepositorioOperacional(banco,conversor);
    }
    private DadosCliente dadosCliente(String endereco){return new DadosCliente("90000000000185","Cliente teste","Fantasia teste","Itu","SP",endereco,-23.25,-47.34);}
    private DadosVeiculo dadosVeiculo(String placa,double pbt){return new DadosVeiculo(placa,"CARRETA","Ensaio","SIDER","SIDER",pbt,8000d,3);}
    private RegistroLocal importado(DadosCliente d){return new RegistroLocal(new Local("cliente-0",d.nomeFantasia(),d.municipio()+"/"+d.uf(),d.latitude(),d.longitude(),"cliente",d.endereco()),List.of(d.nomeFantasia()),d);}
    private Map<String,Object> importadoVeiculo(String placa,double pbt){return Map.of("Placa",placa,"Classificação","SIDER","Frota","Ensaio","Agrupamento","SIDER","Cap. PBT",pbt,"Tara",8000d,"Número de Eixos",3);}
    @Test void parametrosCombustivelPersistemERejeitamValoresInvalidos(){
        var parametros=new ServicoParametrosCombustivel(banco);
        var salvo=parametros.salvar(new ServicoParametrosCombustivel.Configuracao(6.25,2.5));
        assertEquals(salvo,new ServicoParametrosCombustivel(banco).consultar());
        for(var invalido:List.of(new ServicoParametrosCombustivel.Configuracao(null,2.5),
                new ServicoParametrosCombustivel.Configuracao(6.25,null),new ServicoParametrosCombustivel.Configuracao(0d,2.5),
                new ServicoParametrosCombustivel.Configuracao(6.25,-1d),new ServicoParametrosCombustivel.Configuracao(Double.NaN,2.5),
                new ServicoParametrosCombustivel.Configuracao(6.25,Double.POSITIVE_INFINITY)))
            assertThrows(IllegalArgumentException.class,()->parametros.salvar(invalido));
        assertEquals(salvo,parametros.consultar());
        assertThrows(IllegalArgumentException.class,()->parametros.salvar(null));
        parametros.salvar(new ServicoParametrosCombustivel.Configuracao(null,null));
        assertNull(parametros.consultar().precoCombustivel());assertNull(parametros.consultar().consumoMedio());
    }
    @Test void atrasosSaoDoEstabelecimentoComResumoSeparadoPorEtapa(){
        var a=servico.criarCliente(new PedidoCliente(dadosCliente("Portaria A"),null));
        var b=servico.criarCliente(new PedidoCliente(dadosCliente("Portaria B"),null));
        var atrasos=new ServicoAtrasosCliente(banco,repositorio);
        atrasos.registrar(a.id(),new ServicoAtrasosCliente.PedidoAtraso("CARGA","2020-01-01T10:00","2020-01-01T11:00","Doc A"));
        atrasos.registrar(a.id(),new ServicoAtrasosCliente.PedidoAtraso("CARGA","2020-01-02T10:00","2020-01-02T10:00","No prazo"));
        atrasos.registrar(a.id(),new ServicoAtrasosCliente.PedidoAtraso("CARGA","2020-01-03T10:00","2020-01-03T09:30","Antecipado"));
        atrasos.registrar(a.id(),new ServicoAtrasosCliente.PedidoAtraso("DESCARGA","2020-01-04T10:00","2020-01-04T10:30","Doc D"));
        var historico=atrasos.consultar(a.id());assertEquals(4,historico.registros().size());
        var carga=historico.resumo().stream().filter(r->r.etapa().equals("CARGA")).findFirst().orElseThrow();
        assertEquals(3,carga.registros());assertEquals(1,carga.atrasos());assertEquals(20,carga.mediaMinutos(),1e-8);assertEquals(100d/3,carga.percentual(),1e-8);
        assertTrue(historico.registros().stream().anyMatch(r->r.diferencaMinutos()==-30));
        assertTrue(atrasos.consultar(b.id()).registros().isEmpty());
        var original=new ServicoAtrasosCliente.PedidoAtraso("CARGA","2020-01-01T10:00","2020-01-01T11:00","Duplicado");
        assertThrows(IllegalArgumentException.class,()->atrasos.registrar(a.id(),original));
        atrasos.registrar(b.id(),original);assertEquals(1,atrasos.consultar(b.id()).registros().size());
        var d=a.dados();var alterado=new DadosCliente(d.cnpj(),"Razao alterada","Nome alterado","Curitiba","PR",d.endereco(),-25.4,-49.2);
        var salvo=servico.editarCliente(a.id(),new PedidoCliente(alterado,a.versao()));
        assertEquals(4,atrasos.consultar(a.id()).registros().size());
        servico.situacao("clientes",a.id(),new PedidoSituacao(salvo.versao(),false));
        assertEquals(4,atrasos.consultar(a.id()).registros().size());
        assertThrows(IllegalArgumentException.class,()->atrasos.registrar(a.id(),new ServicoAtrasosCliente.PedidoAtraso("CARGA","2020-01-05T10:00","2020-01-05T11:00","Inativo")));
    }
    @Test void atrasosRejeitamDatasEtapasEReferenciasInvalidas(){
        var cliente=servico.criarCliente(new PedidoCliente(dadosCliente("Portaria"),null));
        var atrasos=new ServicoAtrasosCliente(banco,repositorio);
        for(var pedido:List.of(new ServicoAtrasosCliente.PedidoAtraso("VIAGEM","2020-01-01T10:00","2020-01-01T11:00",""),
                new ServicoAtrasosCliente.PedidoAtraso("CARGA","invalido","2020-01-01T11:00",""),
                new ServicoAtrasosCliente.PedidoAtraso("CARGA","2020-01-01T10:00","9999-01-01T11:00",""),
                new ServicoAtrasosCliente.PedidoAtraso("CARGA","2020-01-01T10:00","2020-01-01T11:00","X".repeat(501))))
            assertThrows(IllegalArgumentException.class,()->atrasos.registrar(cliente.id(),pedido));
        assertThrows(IllegalArgumentException.class,()->atrasos.registrar(cliente.id(),null));
        assertThrows(IllegalArgumentException.class,()->atrasos.consultar("cliente-inexistente"));
        assertTrue(atrasos.consultar(cliente.id()).registros().isEmpty());
    }
    @Test void buscaCombinaNomeEMunicipioEmQualquerOrdem(){
        var dados=new DadosCliente("90000000000185","Empresa Alfa Demonstracao","ALFA","Curitiba","PR","Portaria",-25.4,-49.2);
        var cliente=servico.criarCliente(new PedidoCliente(dados,null));
        for(String consulta:List.of("alfa curitiba","curitiba alfa","  ALFA   CURITIBA  ","alfa curit","álfa curitiba")) {
            assertEquals(List.of(cliente.id()),operacional.buscarLocais(consulta).stream().map(Local::id).toList());
        }
        assertTrue(operacional.buscarLocais("alfa itu").isEmpty());
        assertTrue(operacional.buscarLocais(" % ").isEmpty());
        assertTrue(operacional.buscarLocais(" ").isEmpty());
    }
    @Test void cancelarReservaLiberaSomenteACargaEscolhidaEPreservaHistorico(){
        var cenario=UUID.randomUUID();var cargaA=UUID.randomUUID();var cargaB=UUID.randomUUID();
        var reservaA=UUID.randomUUID();var reservaB=UUID.randomUUID();
        banco.update("INSERT INTO cenario_planejamento(id,situacao,bases,pedido,resultado) VALUES(?,'CONFIRMADO','teste','{}','{}')",cenario);
        for(var carga:List.of(cargaA,cargaB))banco.update("INSERT INTO carga_planejada(id,identificacao,coleta,carga) VALUES(?,'Teste',CURRENT_TIMESTAMP,'{}')",carga);
        banco.update("INSERT INTO alocacao_planejada(id,cenario,carga,placa,carreta) VALUES(?,?,?,'CAVALOA','CARRETAA')",reservaA,cenario,cargaA);
        banco.update("INSERT INTO alocacao_planejada(id,cenario,carga,placa,carreta) VALUES(?,?,?,'CAVALOB','CARRETAB')",reservaB,cenario,cargaB);
        var cenarios=new ServicoCenarios(banco,operacional,new MotorRecomendacao(),new ObjectMapper());
        assertThrows(IllegalArgumentException.class,()->cenarios.cancelarReservas(List.of(reservaA,UUID.randomUUID())));
        assertEquals(2,cenarios.listarReservas().size());
        assertThrows(IllegalArgumentException.class,()->cenarios.cancelarReservas(List.of(reservaA,reservaA)));
        assertThrows(IllegalArgumentException.class,()->cenarios.cancelarReservas(List.of()));
        cenarios.cancelarReserva(reservaA);
        assertEquals(1,cenarios.listarReservas().size());assertEquals(reservaB,cenarios.listarReservas().getFirst().get("id"));
        assertEquals(Set.of("CAVALOB","CARRETAB"),operacional.listarRecursosReservados());
        assertEquals("CONFIRMADO",banco.queryForObject("SELECT situacao FROM cenario_planejamento WHERE id=?",String.class,cenario));
        assertEquals(2,banco.queryForObject("SELECT count(*) FROM alocacao_planejada",Integer.class));
        assertThrows(IllegalArgumentException.class,()->cenarios.cancelarReserva(reservaA));
        var nova=UUID.randomUUID();
        banco.update("INSERT INTO alocacao_planejada(id,cenario,carga,placa,carreta) VALUES(?,?,?,'CAVALOC','CARRETAC')",nova,cenario,cargaA);
        assertThrows(IllegalArgumentException.class,()->cenarios.cancelarReserva(reservaA));
        assertEquals(2,cenarios.listarReservas().size());
        cenarios.cancelarReservas(List.of(nova,reservaB));
        assertTrue(cenarios.listarReservas().isEmpty());
        assertEquals("CANCELADO",banco.queryForObject("SELECT situacao FROM cenario_planejamento WHERE id=?",String.class,cenario));
    }
    @Test void variosLocaisPorCnpjPersistemComBuscaECoordenadas(){
        var a=servico.criarCliente(new PedidoCliente(dadosCliente("Portaria 1"),null));var b=servico.criarCliente(new PedidoCliente(dadosCliente("Portaria 2"),null));
        assertNotEquals(a.id(),b.id());assertEquals(2,repositorio.clientes("90000000000185","TODOS",false,0).total());
        assertTrue(Set.of(a.id(),b.id()).contains(operacional.resolverClienteImportacao("90000000000185","Itu/SP").local().id()));
        assertNotNull(operacional.resolverLocal("Fantasia teste","Itu/SP"));
        assertThrows(IllegalArgumentException.class,()->servico.criarCliente(new PedidoCliente(dadosCliente("Portaria 1"),null)));
    }
    @Test void importacaoPreservaClienteEditadoEPermiteRevisaoExplicita(){
        var a=servico.criarCliente(new PedidoCliente(dadosCliente("Portaria 1"),null));
        var proposta=new DadosCliente(a.dados().cnpj(),a.dados().razaoSocial(),"Nome da planilha","Itu","SP","Portaria 1",-23.3,-47.4);
        repositorio.mesclarLocais(List.of(importado(proposta)));var pendente=repositorio.cliente(a.id());
        assertEquals(a.dados(),pendente.dados());assertNotNull(pendente.proposta());assertTrue(pendente.versao()>a.versao());
        assertThrows(IllegalArgumentException.class,()->servico.editarCliente(a.id(),new PedidoCliente(a.dados(),a.versao())));
        var aplicado=(Cliente)servico.revisar("clientes",a.id(),new PedidoRevisao(pendente.versao(),true));assertEquals(proposta,aplicado.dados());assertNull(aplicado.proposta());
    }
    @Test void importacaoVeiculoNaoApagaAusentesNemReativaInativos(){
        var a=servico.criarVeiculo(new PedidoVeiculo(dadosVeiculo("DMO3001",30000),null));servico.criarVeiculo(new PedidoVeiculo(dadosVeiculo("DMO3002",30000),null));
        servico.situacao("veiculos","DMO3001",new PedidoSituacao(a.versao(),false));
        repositorio.mesclarVeiculos(List.of(importadoVeiculo("DMO3001",35000)));
        assertFalse(repositorio.veiculo("DMO3001").ativo());assertEquals(30000,repositorio.veiculo("DMO3001").dados().pbt());assertNotNull(repositorio.veiculo("DMO3001").proposta());assertTrue(repositorio.temVeiculo("DMO3002"));
        var pendente=repositorio.veiculo("DMO3001");servico.revisar("veiculos","DMO3001",new PedidoRevisao(pendente.versao(),false));assertNull(repositorio.veiculo("DMO3001").proposta());
    }
    @Test void importacaoReordenadaNaoTrocaIdentidadeDoCliente(){
        repositorio.mesclarLocais(List.of(importado(dadosCliente("Local A")),importado(dadosCliente("Local B"))));
        var ids=repositorio.clientes("","TODOS",false,0).itens().stream().map(Cliente::id).sorted().toList();
        repositorio.mesclarLocais(List.of(importado(dadosCliente("Local B")),importado(dadosCliente("Local A"))));
        assertEquals(ids,repositorio.clientes("","TODOS",false,0).itens().stream().map(Cliente::id).sorted().toList());assertEquals(0,repositorio.clientes("","TODOS",true,0).total());
    }
    @Test void inativacaoRemoveClienteDaBuscaEProtegeVersao(){
        var a=servico.criarCliente(new PedidoCliente(dadosCliente("Portaria"),null));servico.situacao("clientes",a.id(),new PedidoSituacao(a.versao(),false));
        assertNull(operacional.resolverClienteImportacao("90000000000185","Itu/SP").local());
        assertEquals(1,repositorio.clientes("","INATIVOS",false,0).total());assertTrue(operacional.buscarLocais("Fantasia").isEmpty());
        assertThrows(IllegalArgumentException.class,()->servico.situacao("clientes",a.id(),new PedidoSituacao(a.versao(),true)));
    }
    @Test void veiculoInativoNaoTemCapacidadeAprovadaEPersistenciaNaoCriaPosicao(){
        var a=servico.criarVeiculo(new PedidoVeiculo(new DadosVeiculo("DMO3001","CAVALO","","","CAVALO MECANICO",23000d,8000d,3),null));
        servico.criarVeiculo(new PedidoVeiculo(dadosVeiculo("DMO3002",30000),null));assertTrue(operacional.listarComposicoes().isEmpty());
        banco.update("INSERT INTO posicao_veiculo(placa,carreta,grupo,situacao,engatada) VALUES('DMO3001','DMO3002','SIDER','Vazio',true)");
        assertNotNull(operacional.listarComposicoes().getFirst().capacidade());servico.situacao("veiculos","DMO3001",new PedidoSituacao(a.versao(),false));assertNull(operacional.listarComposicoes().getFirst().capacidade());
    }
}
