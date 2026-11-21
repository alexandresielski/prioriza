package br.com.tcc.prioriza.servico;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoParametrosCombustivel {
    public record Configuracao(Double precoCombustivel, Double consumoMedio) { }
    private final JdbcTemplate banco;
    public ServicoParametrosCombustivel(JdbcTemplate banco) { this.banco=banco; }
    public Configuracao consultar() {
        return banco.queryForObject("SELECT preco_combustivel,consumo_medio FROM parametro_combustivel WHERE id=1",
                (linha,indice)->new Configuracao((Double)linha.getObject(1),(Double)linha.getObject(2)));
    }
    @Transactional
    public Configuracao salvar(Configuracao configuracao) {
        if(configuracao==null)throw new IllegalArgumentException("Informe os parâmetros de combustível.");
        var preco=configuracao.precoCombustivel();var consumo=configuracao.consumoMedio();
        if((preco!=null||consumo!=null)&&(preco==null||consumo==null||!Double.isFinite(preco)||!Double.isFinite(consumo)||preco<=0||consumo<=0))
            throw new IllegalArgumentException("Informe preço do combustível e consumo médio maiores que zero, ou deixe ambos em branco.");
        banco.update("UPDATE parametro_combustivel SET preco_combustivel=?,consumo_medio=? WHERE id=1",preco,consumo);
        return consultar();
    }
}
