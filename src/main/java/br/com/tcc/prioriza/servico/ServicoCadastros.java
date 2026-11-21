package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Cadastros.*;
import br.com.tcc.prioriza.repositorio.RepositorioCadastros;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static br.com.tcc.prioriza.servico.FuncoesDados.*;

@Service
public class ServicoCadastros {
    private final RepositorioCadastros repositorio;
    private static final Set<String> UFS=Set.of("AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO");
    public ServicoCadastros(RepositorioCadastros repositorio){this.repositorio=repositorio;}
    public DadosCliente validarCliente(DadosCliente dados){
        exigir(dados!=null,"Informe os dados do cliente.");
        String cnpj=texto(dados.cnpj()).replaceAll("[.\\-/\\s]","");
        exigir(cnpj.isBlank()||cnpj.matches("[0-9]{14}"),"CNPJ deve conter 14 digitos, com ou sem pontuação.");
        exigir(!texto(dados.razaoSocial()).isBlank()||!texto(dados.nomeFantasia()).isBlank(),"Informe razão social ou nome fantasia.");
        exigir(texto(dados.razaoSocial()).length()<=200&&texto(dados.nomeFantasia()).length()<=200,"Os nomes devem ter até 200 caracteres.");
        exigir(!texto(dados.municipio()).isBlank()&&texto(dados.municipio()).length()<=120,"Informe o município, com até 120 caracteres.");
        String uf=texto(dados.uf()).toUpperCase(Locale.ROOT);exigir(UFS.contains(uf),"Selecione uma UF válida.");
        exigir(!texto(dados.endereco()).isBlank()&&texto(dados.endereco()).length()<=1000,"Informe o endereço ou identificação do local, com até 1000 caracteres.");
        exigir(dados.latitude()!=null&&dados.longitude()!=null&&Double.isFinite(dados.latitude())&&Double.isFinite(dados.longitude())
            &&Math.abs(dados.latitude())<=90&&Math.abs(dados.longitude())<=180&&(dados.latitude()!=0||dados.longitude()!=0),"Informe latitude e longitude validas do cliente.");
        return new DadosCliente(cnpj,texto(dados.razaoSocial()),texto(dados.nomeFantasia()),texto(dados.municipio()),uf,texto(dados.endereco()),dados.latitude(),dados.longitude());
    }
    public DadosVeiculo validarVeiculo(DadosVeiculo dados){
        exigir(dados!=null,"Informe os dados do veículo.");String placa=normalizarTexto(dados.placa());
        exigir(placa.matches("[A-Z]{3}[0-9][A-Z0-9][0-9]{2}"),"Informe uma placa brasileira válida.");
        String tipo=texto(dados.tipo()).toUpperCase(Locale.ROOT);exigir(Set.of("CAVALO","CARRETA").contains(tipo),"Selecione cavalo ou carreta.");
        exigir(!texto(dados.classificacao()).isBlank()&&texto(dados.classificacao()).length()<=100,"Informe a classificação do equipamento.");
        exigir(texto(dados.frota()).length()<=100&&texto(dados.agrupamento()).length()<=100,"Frota e agrupamento devem ter até 100 caracteres.");
        exigir(dados.pbt()!=null&&dados.tara()!=null&&Double.isFinite(dados.pbt())&&Double.isFinite(dados.tara())
            &&dados.pbt()>dados.tara()&&dados.tara()>=0&&dados.pbt()<=200000,"Informe PBT maior que a tara, em kg, ate 200.000 kg.");
        exigir(dados.eixos()!=null&&dados.eixos()>=1&&dados.eixos()<=12,"Informe de 1 a 12 eixos.");
        return new DadosVeiculo(placa,tipo,texto(dados.frota()),texto(dados.agrupamento()),texto(dados.classificacao()),dados.pbt(),dados.tara(),dados.eixos());
    }
    public static void validarConsulta(String situacao,int pagina){exigir(Set.of("TODOS","ATIVOS","INATIVOS").contains(situacao)&&pagina>=0&&pagina<=100000,"Filtro ou página inválida.");}
    @Transactional public Cliente criarCliente(PedidoCliente pedido){
        var dados=validarCliente(pedido==null?null:pedido.dados());repositorio.bloquear();
        exigir(!repositorio.clienteDuplicado(dados,""),"Cliente/local já cadastrado. Edite ou reative o existente.");
        return repositorio.salvarCliente("cliente-"+UUID.randomUUID(),dados,true,true);
    }
    @Transactional public Cliente editarCliente(String id,PedidoCliente pedido){
        var dados=validarCliente(pedido==null?null:pedido.dados());repositorio.bloquear();var atual=repositorio.cliente(id);versao(atual.versao(),pedido.versao());
        exigir(RepositorioCadastros.chaveCliente(atual.dados()).equals(RepositorioCadastros.chaveCliente(dados))||!repositorio.clienteDuplicado(dados,id),"Já existe outro cadastro com esse cliente, município e endereço.");
        return repositorio.salvarCliente(id,dados,false,true);
    }
    @Transactional public Veiculo criarVeiculo(PedidoVeiculo pedido){
        var dados=validarVeiculo(pedido==null?null:pedido.dados());repositorio.bloquear();exigir(!repositorio.temVeiculo(dados.placa()),"Placa já cadastrada. Edite ou reative o existente.");return repositorio.salvarVeiculo(dados,true,true);
    }
    @Transactional public Veiculo editarVeiculo(String placa,PedidoVeiculo pedido){
        var dados=validarVeiculo(pedido==null?null:pedido.dados());exigir(dados.placa().equals(placa),"A placa não pode ser alterada. Cadastre outro veículo.");
        repositorio.bloquear();versao(repositorio.veiculo(placa).versao(),pedido.versao());return repositorio.salvarVeiculo(dados,false,true);
    }
    @Transactional public Object situacao(String tipo,String id,PedidoSituacao pedido){
        tipo(tipo);exigir(pedido!=null&&pedido.ativo()!=null,"Informe a situação desejada.");repositorio.bloquear();
        int atual=tipo.equals("clientes")?repositorio.cliente(id).versao():repositorio.veiculo(id).versao();versao(atual,pedido.versao());
        repositorio.situacao(tipo,id,pedido.ativo());return tipo.equals("clientes")?repositorio.cliente(id):repositorio.veiculo(id);
    }
    @Transactional public Object revisar(String tipo,String id,PedidoRevisao pedido){
        tipo(tipo);exigir(pedido!=null&&pedido.aceitar()!=null,"Informe se deseja aplicar a proposta.");repositorio.bloquear();
        if(tipo.equals("clientes")){
            var atual=repositorio.cliente(id);versao(atual.versao(),pedido.versao());exigir(atual.proposta()!=null,"Não existe proposta pendente.");
            if(pedido.aceitar()){var dados=validarCliente(atual.proposta());exigir(RepositorioCadastros.chaveCliente(atual.dados()).equals(RepositorioCadastros.chaveCliente(dados))||!repositorio.clienteDuplicado(dados,id),"A proposta corresponde a outro cliente/local. Revise o cadastro.");return repositorio.salvarCliente(id,dados,false,true);}
        }else{
            var atual=repositorio.veiculo(id);versao(atual.versao(),pedido.versao());exigir(atual.proposta()!=null,"Não existe proposta pendente.");
            if(pedido.aceitar())return repositorio.salvarVeiculo(validarVeiculo(atual.proposta()),false,true);
        }
        repositorio.descartarProposta(tipo,id);return tipo.equals("clientes")?repositorio.cliente(id):repositorio.veiculo(id);
    }
    private static void tipo(String tipo){exigir(Set.of("clientes","veiculos").contains(tipo),"Cadastro desconhecido.");}
    private static void versao(int atual,Integer recebida){exigir(recebida!=null&&recebida==atual,"Cadastro alterado em outra sessão. Atualize a lista antes de salvar.");}
    private static void exigir(boolean condicao,String mensagem){if(!condicao)throw new IllegalArgumentException(mensagem);}
}
