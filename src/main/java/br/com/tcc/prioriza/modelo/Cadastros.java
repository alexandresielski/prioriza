package br.com.tcc.prioriza.modelo;

import java.util.List;

public final class Cadastros {
    private Cadastros() { }
    public record DadosCliente(String cnpj, String razaoSocial, String nomeFantasia, String municipio, String uf,
                               String endereco, Double latitude, Double longitude) { }
    public record Cliente(String id, int versao, boolean ativo, boolean manual, DadosCliente dados, String fonte, DadosCliente proposta) { }
    public record DadosVeiculo(String placa, String tipo, String frota, String agrupamento, String classificacao,
                               Double pbt, Double tara, Integer eixos) { }
    public record Veiculo(int versao, boolean ativo, boolean manual, DadosVeiculo dados, DadosVeiculo proposta) { }
    public record Pagina<T>(List<T> itens, long total, int pagina, int tamanho) { }
    public record PedidoCliente(DadosCliente dados, Integer versao) { }
    public record PedidoVeiculo(DadosVeiculo dados, Integer versao) { }
    public record PedidoSituacao(Integer versao, Boolean ativo) { }
    public record PedidoRevisao(Integer versao, Boolean aceitar) { }
}
