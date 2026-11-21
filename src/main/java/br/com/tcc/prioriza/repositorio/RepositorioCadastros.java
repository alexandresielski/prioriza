package br.com.tcc.prioriza.repositorio;

import br.com.tcc.prioriza.modelo.Cadastros.*;
import br.com.tcc.prioriza.modelo.Modelos.Local;
import br.com.tcc.prioriza.repositorio.RepositorioOperacional.RegistroLocal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.*;
import static br.com.tcc.prioriza.servico.FuncoesDados.*;

@Repository
public class RepositorioCadastros {
    private final JdbcTemplate banco;
    private final ObjectMapper conversor;
    public RepositorioCadastros(JdbcTemplate banco, ObjectMapper conversor) { this.banco=banco; this.conversor=conversor; }
    public void bloquear() { banco.execute("SELECT pg_advisory_xact_lock(741116)"); }
    public void registrarAlteracao() { banco.update("UPDATE controle_cadastro SET revisao=revisao+1 WHERE id=1"); }
    private String json(Object objeto) { try { return conversor.writeValueAsString(objeto); } catch(Exception erro) { throw new IllegalStateException("Falha ao gravar cadastro.",erro); } }
    private <T> T lerJson(String json, Class<T> tipo) { try { return json==null?null:conversor.readValue(json,tipo); } catch(Exception erro) { throw new IllegalStateException("Falha ao ler proposta de importação.",erro); } }
    private static Double numero(ResultSet linha,String coluna) throws SQLException { return linha.getObject(coluna)==null?null:linha.getDouble(coluna); }
    private Cliente lerCliente(ResultSet linha,int indice) throws SQLException {
        String cidade=linha.getString("cidade"); int separador=cidade.lastIndexOf('/');
        var dados=new DadosCliente(linha.getString("cnpj"),linha.getString("razao_social"),linha.getString("nome_fantasia"),
            separador<0?cidade:cidade.substring(0,separador),separador<0?"":cidade.substring(separador+1),linha.getString("detalhe"),numero(linha,"latitude"),numero(linha,"longitude"));
        return new Cliente(linha.getString("id"),linha.getInt("versao"),linha.getBoolean("ativo"),linha.getBoolean("manual"),dados,linha.getString("fonte"),lerJson(linha.getString("proposta_importacao"),DadosCliente.class));
    }
    private Veiculo lerVeiculo(ResultSet linha,int indice) throws SQLException {
        Integer eixos=linha.getObject("eixos")==null?null:linha.getInt("eixos");
        var dados=new DadosVeiculo(linha.getString("placa"),linha.getString("tipo"),texto(linha.getString("frota")),texto(linha.getString("agrupamento")),texto(linha.getString("classificacao")),numero(linha,"pbt"),numero(linha,"tara"),eixos);
        return new Veiculo(linha.getInt("versao"),linha.getBoolean("ativo"),linha.getBoolean("manual"),dados,lerJson(linha.getString("proposta_importacao"),DadosVeiculo.class));
    }
    public Cliente cliente(String id) {
        var itens=banco.query("SELECT * FROM local_atendimento WHERE id=? AND id LIKE 'cliente-%'",this::lerCliente,id);
        if(itens.isEmpty())throw new IllegalArgumentException("Cliente não encontrado."); return itens.getFirst();
    }
    public Veiculo veiculo(String placa) {
        var itens=banco.query("SELECT * FROM veiculo WHERE placa=?",this::lerVeiculo,placa);
        if(itens.isEmpty())throw new IllegalArgumentException("Veículo não encontrado."); return itens.getFirst();
    }
    public Pagina<Cliente> clientes(String busca,String situacao,boolean revisao,int pagina) {
        String termo="%"+normalizarTexto(busca)+"%";
        String filtro="id LIKE 'cliente-%' AND (busca LIKE ? OR regexp_replace(cnpj,'[^0-9]','','g') LIKE ?) AND (?='TODOS' OR ativo=(?='ATIVOS')) AND (NOT ? OR proposta_importacao IS NOT NULL)";
        var args=new Object[]{termo,termo,situacao,situacao,revisao};
        long total=banco.queryForObject("SELECT count(*) FROM local_atendimento WHERE "+filtro,Long.class,args);
        List<Object> lista=new ArrayList<>(Arrays.asList(args));lista.add(pagina*50);
        return new Pagina<>(banco.query("SELECT * FROM local_atendimento WHERE "+filtro+" ORDER BY nome,id LIMIT 50 OFFSET ?",this::lerCliente,lista.toArray()),total,pagina,50);
    }
    public Pagina<Veiculo> veiculos(String busca,String situacao,boolean revisao,int pagina) {
        String filtro="(regexp_replace(upper(placa||coalesce(classificacao,'')||coalesce(frota,'')),'[^A-Z0-9]','','g') LIKE ?) AND (?='TODOS' OR ativo=(?='ATIVOS')) AND (NOT ? OR proposta_importacao IS NOT NULL)";
        var args=new Object[]{"%"+normalizarTexto(busca)+"%",situacao,situacao,revisao};
        long total=banco.queryForObject("SELECT count(*) FROM veiculo WHERE "+filtro,Long.class,args);
        List<Object> lista=new ArrayList<>(Arrays.asList(args));lista.add(pagina*50);
        return new Pagina<>(banco.query("SELECT * FROM veiculo WHERE "+filtro+" ORDER BY placa LIMIT 50 OFFSET ?",this::lerVeiculo,lista.toArray()),total,pagina,50);
    }
    public static String chaveCliente(DadosCliente d) {
        return (!texto(d.cnpj()).isBlank()?normalizarTexto(d.cnpj()):normalizarTexto(texto(d.razaoSocial()).isBlank()?d.nomeFantasia():d.razaoSocial()))+"|"+normalizarTexto(d.municipio()+"/"+d.uf())+"|"+normalizarTexto(d.endereco());
    }
    public boolean clienteDuplicado(DadosCliente dados,String exceto) {
        return banco.query("SELECT * FROM local_atendimento WHERE id LIKE 'cliente-%'",this::lerCliente).stream()
            .anyMatch(c->!c.id().equals(exceto)&&chaveCliente(c.dados()).equals(chaveCliente(dados)));
    }
    public Cliente salvarCliente(String id,DadosCliente dados,boolean novo,boolean manual) {
        String nome=texto(dados.nomeFantasia()).isBlank()?dados.razaoSocial():dados.nomeFantasia();
        String cidade=dados.municipio()+"/"+dados.uf();
        String fonte=dados.latitude()==null||dados.longitude()==null?"ausente":"cliente";
        var nomes=List.of(normalizarTexto(dados.razaoSocial()),normalizarTexto(dados.nomeFantasia()),normalizarTexto(dados.cnpj())).stream().filter(v->!v.isBlank()).distinct().toArray(String[]::new);
        if(novo)banco.update("INSERT INTO local_atendimento(id,nome,cidade,fonte,detalhe,nomes_busca,cidade_busca,busca) VALUES(?,?,?,'ausente','',ARRAY[]::text[],?,'')",id,nome,cidade,normalizarTexto(cidade));
        banco.update(conexao->{
            var comando=conexao.prepareStatement("UPDATE local_atendimento SET nome=?,cidade=?,latitude=?,longitude=?,fonte=?,detalhe=?,nomes_busca=?,cidade_busca=?,busca=?,cnpj=?,razao_social=?,nome_fantasia=?,manual=?,proposta_importacao=NULL,versao=versao+? WHERE id=?");
            comando.setString(1,nome);comando.setString(2,cidade);comando.setObject(3,dados.latitude());comando.setObject(4,dados.longitude());comando.setString(5,fonte);comando.setString(6,dados.endereco());
            comando.setArray(7,conexao.createArrayOf("text",nomes));comando.setString(8,normalizarTexto(cidade));comando.setString(9,normalizarTexto(nome+dados.razaoSocial()+cidade+dados.cnpj()+dados.endereco()));
            comando.setString(10,dados.cnpj());comando.setString(11,dados.razaoSocial());comando.setString(12,dados.nomeFantasia());comando.setBoolean(13,manual);comando.setInt(14,novo?0:1);comando.setString(15,id);return comando;
        });
        if(dados.latitude()!=null&&dados.longitude()!=null)banco.update("INSERT INTO local_atendimento(id,nome,cidade,latitude,longitude,fonte,detalhe,nomes_busca,cidade_busca,busca) VALUES(?,?,?,?,?,'municipio','Referencia municipal aproximada',ARRAY[]::text[],?,?) ON CONFLICT(id) DO NOTHING",
            "municipio-"+normalizarTexto(cidade),cidade,cidade,dados.latitude(),dados.longitude(),normalizarTexto(cidade),normalizarTexto(cidade));
        registrarAlteracao(); return cliente(id);
    }
    public Veiculo salvarVeiculo(DadosVeiculo dados,boolean novo,boolean manual) {
        if(novo)banco.update("INSERT INTO veiculo(placa) VALUES(?)",dados.placa());
        banco.update("UPDATE veiculo SET tipo=?,frota=?,agrupamento=?,classificacao=?,pbt=?,tara=?,eixos=?,manual=?,proposta_importacao=NULL,versao=versao+? WHERE placa=?",
            dados.tipo(),dados.frota(),dados.agrupamento(),dados.classificacao(),dados.pbt(),dados.tara(),dados.eixos(),manual,novo?0:1,dados.placa());
        registrarAlteracao();return veiculo(dados.placa());
    }
    public boolean temVeiculo(String placa) {return banco.queryForObject("SELECT EXISTS(SELECT 1 FROM veiculo WHERE placa=?)",Boolean.class,placa);}
    public void situacao(String tipo,String id,boolean ativo) {
        banco.update(tipo.equals("clientes")?"UPDATE local_atendimento SET ativo=?,versao=versao+1,manual=true WHERE id=?":"UPDATE veiculo SET ativo=?,versao=versao+1,manual=true WHERE placa=?",ativo,id);registrarAlteracao();
    }
    public void descartarProposta(String tipo,String id) {
        banco.update(tipo.equals("clientes")?"UPDATE local_atendimento SET proposta_importacao=NULL,versao=versao+1 WHERE id=?":"UPDATE veiculo SET proposta_importacao=NULL,versao=versao+1 WHERE placa=?",id);
    }
    private void propor(String tipo,String id,Object dados) {
        String sql=tipo.equals("clientes")?"UPDATE local_atendimento SET proposta_importacao=?::jsonb,versao=versao+1 WHERE id=? AND proposta_importacao IS DISTINCT FROM ?::jsonb":"UPDATE veiculo SET proposta_importacao=?::jsonb,versao=versao+1 WHERE placa=? AND proposta_importacao IS DISTINCT FROM ?::jsonb";
        String proposta=json(dados);banco.update(sql,proposta,id,proposta);
    }
    public void preencherMetadados(List<RegistroLocal> registros) {
        for(var registro:registros)if(registro.dados()!=null)banco.update("UPDATE local_atendimento SET cnpj=?,razao_social=?,nome_fantasia=?,detalhe=?,busca=? WHERE id=? AND NOT manual",
            registro.dados().cnpj(),registro.dados().razaoSocial(),registro.dados().nomeFantasia(),registro.dados().endereco(),normalizarTexto(registro.local().nome()+registro.dados().razaoSocial()+registro.local().cidade()+registro.dados().cnpj()+registro.dados().endereco()),registro.local().id());
    }
    public List<String> mesclarLocais(List<RegistroLocal> registros) {
        bloquear();Map<String,Cliente> existentes=new LinkedHashMap<>();
        for(var cliente:banco.query("SELECT * FROM local_atendimento WHERE id LIKE 'cliente-%' ORDER BY length(id),id",this::lerCliente))existentes.putIfAbsent(chaveCliente(cliente.dados()),cliente);
        int novos=0,divergencias=0;
        for(var registro:registros){
            if(registro.dados()==null){
                Local l=registro.local();banco.update("INSERT INTO local_atendimento(id,nome,cidade,latitude,longitude,fonte,detalhe,nomes_busca,cidade_busca,busca) VALUES(?,?,?,?,?,?,?,ARRAY[]::text[],?,?) ON CONFLICT(id) DO NOTHING",l.id(),l.nome(),l.cidade(),l.latitude(),l.longitude(),l.fonte(),l.detalhe(),normalizarTexto(l.cidade()),normalizarTexto(l.cidade()));continue;
            }
            var dados=registro.dados();var atual=existentes.get(chaveCliente(dados));
            if(atual==null){var salvo=salvarCliente("cliente-"+UUID.randomUUID(),dados,true,false);existentes.put(chaveCliente(dados),salvo);novos++;}
            else if(!atual.dados().equals(dados)){propor("clientes",atual.id(),dados);divergencias++;}
        }
        return List.of(novos+" clientes/locais incluídos. "+divergencias+" divergências aguardam revisão em Bases de dados > Clientes. Cadastros existentes e inativos preservados.");
    }
    public List<String> mesclarVeiculos(List<Map<String,Object>> registros) {
        bloquear();Map<String,Veiculo> existentes=new HashMap<>();for(var veiculo:banco.query("SELECT * FROM veiculo",this::lerVeiculo))existentes.put(veiculo.dados().placa(),veiculo);
        int novos=0,divergencias=0;
        for(var registro:registros){
            String placa=normalizarTexto(registro.get("Placa"));if(placa.isBlank())throw new IllegalArgumentException("Veículo sem placa na importação.");
            var atual=existentes.get(placa);String classificacao=texto(registro.get("Classificação"));String tipo=atual==null?"":atual.dados().tipo();
            if(tipo.isBlank())tipo=normalizarTexto(classificacao).contains("CAVALO")?"CAVALO":normalizarTexto(classificacao).matches(".*(SIDER|CARRETA|REBOQUE|BAU).*")?"CARRETA":"";
            Double eixos=converterNumero(registro.get("Número de Eixos"));
            var dados=new DadosVeiculo(placa,tipo,texto(registro.get("Frota")),texto(registro.get("Agrupamento")),classificacao,converterNumero(registro.get("Cap. PBT")),converterNumero(registro.get("Tara")),eixos==null?null:eixos.intValue());
            if(atual==null){var salvo=salvarVeiculo(dados,true,false);existentes.put(placa,salvo);novos++;}
            else if(!atual.dados().equals(dados)){propor("veiculos",placa,dados);divergencias++;}
        }
        return List.of(novos+" veículos incluídos. "+divergencias+" divergências aguardam revisão em Bases de dados > Veículos. Cadastros existentes e inativos preservados.");
    }
}
