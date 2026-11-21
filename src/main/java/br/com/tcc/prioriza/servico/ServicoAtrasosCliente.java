package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Cadastros.Cliente;
import br.com.tcc.prioriza.repositorio.RepositorioCadastros;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static br.com.tcc.prioriza.servico.FuncoesDados.*;

@Service
public class ServicoAtrasosCliente {
    public record PedidoAtraso(String etapa, String previsto, String executado, String observacao) { }
    public record Registro(UUID id, String etapa, LocalDateTime previsto, LocalDateTime executado,
            String observacao, double diferencaMinutos) { }
    public record Resumo(String etapa, int registros, int atrasos, double mediaMinutos, double percentual) { }
    public record Historico(Cliente cliente, List<Registro> registros, List<Resumo> resumo) { }
    private final JdbcTemplate banco;
    private final RepositorioCadastros cadastros;
    public ServicoAtrasosCliente(JdbcTemplate banco, RepositorioCadastros cadastros) {
        this.banco=banco; this.cadastros=cadastros;
    }
    @Transactional(readOnly=true)
    public Historico consultar(String id) {
        var cliente=cadastros.cliente(id);
        var registros=banco.query("""
            SELECT id,etapa,previsto,executado,observacao,
              extract(epoch FROM(executado-previsto))/60 AS diferenca
            FROM atraso_cliente WHERE cliente_id=? ORDER BY executado DESC,registrado_em DESC,id LIMIT 100
            """,(linha,indice)->new Registro(linha.getObject("id",UUID.class),linha.getString("etapa"),
                linha.getTimestamp("previsto").toLocalDateTime(),linha.getTimestamp("executado").toLocalDateTime(),
                linha.getString("observacao"),linha.getDouble("diferenca")),id);
        var resumo=banco.query("""
            SELECT etapa,count(*) AS registros,count(*) FILTER(WHERE executado>previsto) AS atrasos,
              avg(greatest(0,extract(epoch FROM(executado-previsto))/60)) AS media,
              100.0*count(*) FILTER(WHERE executado>previsto)/count(*) AS percentual
            FROM atraso_cliente WHERE cliente_id=? GROUP BY etapa ORDER BY etapa
            """,(linha,indice)->new Resumo(linha.getString("etapa"),linha.getInt("registros"),
                linha.getInt("atrasos"),linha.getDouble("media"),linha.getDouble("percentual")),id);
        return new Historico(cliente,registros,resumo);
    }
    @Transactional
    public void registrar(String id, PedidoAtraso pedido) {
        cadastros.bloquear();
        var cliente=cadastros.cliente(id);
        if(!cliente.ativo())throw new IllegalArgumentException("Cliente inativo: histórico disponível apenas para consulta.");
        if(pedido==null||!Set.of("CARGA","DESCARGA").contains(texto(pedido.etapa())))
            throw new IllegalArgumentException("Selecione a etapa de carga ou descarga.");
        var previsto=converterData(pedido.previsto());var executado=converterData(pedido.executado());
        if(previsto==null||executado==null||previsto.getYear()<1||previsto.getYear()>9999||executado.getYear()<1||executado.getYear()>9999)
            throw new IllegalArgumentException("Informe datas e horários válidos, com ano de quatro dígitos.");
        if(executado.isAfter(LocalDateTime.now(ZoneId.of("America/Sao_Paulo"))))
            throw new IllegalArgumentException("O horário executado não pode estar no futuro.");
        String observacao=texto(pedido.observacao());
        if(observacao.length()>500)throw new IllegalArgumentException("A referência da ocorrência deve ter até 500 caracteres.");
        var dados=cliente.dados();
        String nome=texto(dados.nomeFantasia()).isBlank()?dados.razaoSocial():dados.nomeFantasia();
        try {
            banco.update("INSERT INTO atraso_cliente(id,cliente_id,cliente,municipio,etapa,previsto,executado,observacao) VALUES(?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(),id,normalizarTexto(nome),TemposHistoricos.normalizarMunicipio(dados.municipio()+"/"+dados.uf()),
                    pedido.etapa(),previsto,executado,observacao);
        } catch(DuplicateKeyException erro) {
            throw new IllegalArgumentException("Esta ocorrência já foi registrada para este estabelecimento.");
        }
    }
}
