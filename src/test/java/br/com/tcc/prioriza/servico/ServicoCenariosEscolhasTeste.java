package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Modelos.*;
import br.com.tcc.prioriza.repositorio.RepositorioOperacional;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ServicoCenariosEscolhasTeste {
    private final JdbcTemplate banco=mock(JdbcTemplate.class);
    private final RepositorioOperacional repositorio=mock(RepositorioOperacional.class);
    private final ServicoCenarios servico=new ServicoCenarios(banco,repositorio,new MotorRecomendacao(),new ObjectMapper());
    private final UUID id126=UUID.randomUUID(),id129=UUID.randomUUID();
    private final Local origem=new Local("cliente-1","Cliente coleta","Itu/SP",-23.25,-47.34,"cliente","");
    ServicoCenariosEscolhasTeste(){
        when(banco.queryForObject(startsWith("SELECT md5"),eq(String.class))).thenReturn("assinatura");
        when(banco.queryForObject(contains("EXISTS"),eq(Boolean.class),any(UUID.class))).thenReturn(false);
        when(repositorio.listarMedias()).thenReturn(List.of());
        when(repositorio.obterPlanejamento(id126)).thenReturn(carga(id126,"126","2026-11-20T22:00"));
        when(repositorio.obterPlanejamento(id129)).thenReturn(carga(id129,"129","2026-11-21T11:00"));
        when(repositorio.listarComposicoes()).thenReturn(List.of(veiculo("DMO3016","DMO3017",-23.25,32000d),veiculo("DMO3018","DMO3019",-23.3,32000d)));
    }
    private CargaPlanejada carga(UUID id,String nome,String coleta){return new CargaPlanejada(id,1,"PENDENTE",new Carga(nome,origem,origem,"2026-11-20T11:47",coleta,26000d,null,null,null),"","",false);}
    private Composicao veiculo(String placa,String carreta,double latitude,double capacidade){return new Composicao(placa,carreta,"SIDER","Vazio",true,true,"Itu/SP",latitude,-47.34,"2026-11-20T11:46","","","","",capacidade,6d,null,null);}
    private ServicoCenarios.Pedido pedido(Map<UUID,String> escolhas){return new ServicoCenarios.Pedido(List.of(id126,id129),"2026-11-20T11:47",Parametros.padrao(),Map.of(),escolhas);}
    @Test void fluxoCompletoRedistribuiSemFixarAMaoACarga126(){
        var resultado=servico.simular(pedido(Map.of(id129,"dmo-3016")));
        assertEquals("DMO3018",resultado.recomendacoes().stream().filter(o->o.carga().equals(id126)).findFirst().orElseThrow().placa());
        assertEquals("DMO3016",resultado.recomendacoes().stream().filter(o->o.carga().equals(id129)).findFirst().orElseThrow().placa());
        assertEquals("DMO3016",resultado.escolhas().get(id129));assertEquals(2,resultado.recomendacoes().stream().map(ServicoCenarios.Opcao::placa).distinct().count());
    }
    @Test void conflitoManualNaoGravaPropostaParcial(){
        var erro=assertThrows(IllegalArgumentException.class,()->servico.simular(pedido(Map.of(id126,"DMO3016",id129,"DMO3016"))));
        assertTrue(erro.getMessage().contains("126")&&erro.getMessage().contains("129"));
        verify(banco,never()).update(startsWith("INSERT INTO cenario_planejamento"),any(),any(),any(),any());
    }
    @Test void manualNaoIgnoraCapacidade(){
        when(repositorio.listarComposicoes()).thenReturn(List.of(veiculo("DMO3016","DMO3017",-23.25,20000d),veiculo("DMO3018","DMO3019",-23.3,32000d)));
        assertThrows(IllegalArgumentException.class,()->servico.simular(pedido(Map.of(id129,"DMO3016"))));
        verify(banco,never()).update(startsWith("INSERT INTO cenario_planejamento"),any(),any(),any(),any());
    }
}
