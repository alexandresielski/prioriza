package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Cadastros.*;
import br.com.tcc.prioriza.repositorio.RepositorioCadastros;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicoCadastrosTeste {
    private final RepositorioCadastros repositorio=mock(RepositorioCadastros.class);
    private final ServicoCadastros servico=new ServicoCadastros(repositorio);
    private DadosCliente cliente(){return new DadosCliente("90.000.000/0001-85","Razao","Fantasia","Itu","sp","Portaria 1",-23.25,-47.34);}
    private DadosVeiculo veiculo(){return new DadosVeiculo("DMO-3014","CAVALO","1","Frota","Cavalo mecanico",23000d,8000d,3);}
    @Test void normalizaDocumentoUfEPlaca(){assertEquals("90000000000185",servico.validarCliente(cliente()).cnpj());assertEquals("SP",servico.validarCliente(cliente()).uf());assertEquals("DMO3014",servico.validarVeiculo(veiculo()).placa());}
    @Test void recusaCoordenadasInvalidas(){for(Double latitude:new Double[]{null,Double.NaN,91d})assertThrows(IllegalArgumentException.class,()->servico.validarCliente(new DadosCliente("","A","","Itu","SP","Portaria",latitude,-47d)));verifyNoInteractions(repositorio);}
    @Test void recusaCnpjUfENomeInvalidos(){
        assertThrows(IllegalArgumentException.class,()->servico.validarCliente(new DadosCliente("123","A","","Itu","SP","Portaria",-23d,-47d)));
        assertThrows(IllegalArgumentException.class,()->servico.validarCliente(new DadosCliente("","A","","Itu","XX","Portaria",-23d,-47d)));
        assertThrows(IllegalArgumentException.class,()->servico.validarCliente(new DadosCliente("","","","Itu","SP","Portaria",-23d,-47d)));
    }
    @Test void recusaPesoEixosETipoInvalidos(){
        for(var d:new DadosVeiculo[]{new DadosVeiculo("DMO3011","CAVALO","","","SIDER",8000d,8000d,3),new DadosVeiculo("DMO3011","CAVALO","","","SIDER",23000d,-1d,3),new DadosVeiculo("DMO3011","CAVALO","","","SIDER",23000d,8000d,0),new DadosVeiculo("DMO3011","OUTRO","","","SIDER",23000d,8000d,3)})assertThrows(IllegalArgumentException.class,()->servico.validarVeiculo(d));
    }
    @Test void naoCriaPlacaDuplicada(){when(repositorio.temVeiculo("DMO3014")).thenReturn(true);assertThrows(IllegalArgumentException.class,()->servico.criarVeiculo(new PedidoVeiculo(veiculo(),null)));verify(repositorio,never()).salvarVeiculo(any(),anyBoolean(),anyBoolean());}
    @Test void versaoAntigaImpedeAlteracao(){when(repositorio.veiculo("DMO3014")).thenReturn(new Veiculo(2,true,true,servico.validarVeiculo(veiculo()),null));assertThrows(IllegalArgumentException.class,()->servico.editarVeiculo("DMO3014",new PedidoVeiculo(veiculo(),1)));verify(repositorio,never()).salvarVeiculo(any(),anyBoolean(),anyBoolean());}
    @Test void naoPermiteMudarPlaca(){assertThrows(IllegalArgumentException.class,()->servico.editarVeiculo("DMO3015",new PedidoVeiculo(veiculo(),1)));verifyNoInteractions(repositorio);}
    @Test void filtrosSaoValidados(){assertThrows(IllegalArgumentException.class,()->ServicoCadastros.validarConsulta("INVALIDO",0));assertThrows(IllegalArgumentException.class,()->ServicoCadastros.validarConsulta("ATIVOS",-1));}
}
