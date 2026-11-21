package br.com.tcc.prioriza.repositorio;

import br.com.tcc.prioriza.modelo.Modelos.*;
import br.com.tcc.prioriza.modelo.Cadastros.DadosCliente;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import static br.com.tcc.prioriza.servico.FuncoesDados.*;
import static br.com.tcc.prioriza.servico.TemposHistoricos.normalizarMunicipio;

@Repository
public class RepositorioOperacional {
    private final JdbcTemplate banco;
    private final ObjectMapper conversor;
    private static final Pattern PADRAO_CIDADE = Pattern.compile("^\\s*(.+?)\\s*[-/]\\s*([A-Za-z]{2})(?:\\s*\\(.*)?\\s*$");
    public record RegistroLocal(Local local, List<String> nomes, DadosCliente dados) {
        public RegistroLocal(Local local,List<String> nomes){this(local,nomes,null);}
    }
    public record CorrespondenciaLocal(Local local, int encontrados) { }
    public record PosicaoBanco(String placa, String carreta, String grupo, String situacao, boolean engatada,
            String cidade, Double latitude, Double longitude, LocalDateTime data, String origem,
            String remetente, String destino, String destinatario) { }

    public RepositorioOperacional(JdbcTemplate banco, ObjectMapper conversor) { this.banco = banco; this.conversor = conversor; }

    public void gravarImportacao(String tipo, String nome, byte[] conteudo, String hash, int linhas) {
        banco.update("""
            INSERT INTO importacao_base(tipo,nome_arquivo,conteudo,sha256,quantidade_linhas) VALUES(?,?,?,?,?)
            ON CONFLICT(tipo) DO UPDATE SET nome_arquivo=EXCLUDED.nome_arquivo,conteudo=EXCLUDED.conteudo,
              sha256=EXCLUDED.sha256,quantidade_linhas=EXCLUDED.quantidade_linhas,importado_em=CURRENT_TIMESTAMP
            """, tipo, nome, conteudo, hash, linhas);
    }
    public boolean temImportacao(String tipo) { return banco.queryForObject("SELECT COUNT(*) FROM importacao_base WHERE tipo=?", Integer.class, tipo) > 0; }
    public int contarImportacoes() { return banco.queryForObject("SELECT COUNT(*) FROM importacao_base", Integer.class); }
    public byte[] obterArquivoClientes() {
        var arquivos = banco.query("SELECT conteudo FROM importacao_base WHERE tipo='RELACAOCLIENTESCOORDENADAS' AND versao_clientes=0", (linha, indice) -> linha.getBytes(1));
        return arquivos.isEmpty() ? null : arquivos.getFirst();
    }
    public void atualizarNomesClientes(List<RegistroLocal> registros) {
        bloquearPlanejamento();
        banco.batchUpdate("UPDATE local_atendimento SET nomes_busca=? WHERE id=? AND NOT manual", registros.stream().filter(r -> r.local().id().startsWith("cliente-")).toList(), 300, (comando, registro) -> {
            comando.setArray(1, comando.getConnection().createArrayOf("text", registro.nomes().toArray()));
            comando.setString(2, registro.local().id());
        });
        new RepositorioCadastros(banco,conversor).preencherMetadados(registros);
        marcarClientesAtualizados();
    }
    public void marcarClientesAtualizados(){banco.update("UPDATE importacao_base SET versao_clientes=1 WHERE tipo='RELACAOCLIENTESCOORDENADAS'");}
    public byte[] obterViagensSemMedias() {
        List<byte[]> arquivos = banco.query("SELECT conteudo FROM importacao_base WHERE tipo='RELACAOVIAGENS' AND versao_tempos=0",
                (linha, indice) -> linha.getBytes(1));
        return arquivos.isEmpty() ? null : arquivos.getFirst();
    }
    public void substituirMedias(List<MediaAtendimento> medias) {
        bloquearPlanejamento();
        banco.update("DELETE FROM media_atendimento");
        banco.batchUpdate("INSERT INTO media_atendimento VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", medias, 200, (comando, media) -> {
            comando.setString(1, media.tipo()); comando.setString(2, media.cliente()); comando.setString(3, media.cidade());
            comando.setString(4, normalizarTexto(media.cliente())); comando.setString(5, normalizarMunicipio(media.cidade()));
            comando.setInt(6, media.total()); comando.setInt(7, media.zeros()); comando.setInt(8, media.invalidos());
            comando.setInt(9, media.extremos()); comando.setInt(10, media.utilizados()); comando.setObject(11, media.mediaHoras());
            comando.setObject(12, media.medianaHoras()); comando.setObject(13, media.limiteInferior()); comando.setObject(14, media.limiteSuperior());
        });
    }
    public void marcarMediasAtualizadas() { banco.update("UPDATE importacao_base SET versao_tempos=1 WHERE tipo='RELACAOVIAGENS'"); }
    public List<MediaAtendimento> listarMedias() {
        return banco.query("SELECT * FROM media_atendimento ORDER BY tipo,cliente,cidade", (linha, indice) ->
                new MediaAtendimento(linha.getString("tipo"), linha.getString("cliente"), linha.getString("cidade"),
                        linha.getInt("total"), linha.getInt("zeros"), linha.getInt("invalidos"), linha.getInt("extremos"),
                        linha.getInt("utilizados"), numero(linha, "media_horas"), numero(linha, "mediana_horas"),
                        numero(linha, "limite_inferior"), numero(linha, "limite_superior")));
    }

    public List<String> substituirLocais(List<RegistroLocal> registros) { return new RepositorioCadastros(banco,conversor).mesclarLocais(registros); }

    public List<String> substituirVeiculos(List<Map<String, Object>> registros) { return new RepositorioCadastros(banco,conversor).mesclarVeiculos(registros); }

    public void substituirPosicoes(List<PosicaoBanco> registros) {
        bloquearPlanejamento();
        banco.update("DELETE FROM posicao_veiculo");
        banco.batchUpdate("INSERT INTO posicao_veiculo VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", registros, 200, (comando, registro) -> {
            comando.setString(1, registro.placa()); comando.setString(2, registro.carreta()); comando.setString(3, registro.grupo());
            comando.setString(4, registro.situacao()); comando.setBoolean(5, registro.engatada()); comando.setString(6, registro.cidade());
            comando.setObject(7, registro.latitude()); comando.setObject(8, registro.longitude()); comando.setObject(9, registro.data());
            comando.setString(10, registro.origem()); comando.setString(11, registro.remetente()); comando.setString(12, registro.destino());
            comando.setString(13, registro.destinatario());
        });
    }

    public void substituirCargas(List<CargaHistorica> registros) {
        bloquearPlanejamento();
        banco.update("DELETE FROM carga_historica");
        banco.batchUpdate("INSERT INTO carga_historica VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", registros, 300, (comando, registro) -> {
            comando.setString(1, registro.numero()); comando.setString(2, registro.origem()); comando.setString(3, registro.destino());
            comando.setString(4, registro.cidadeOrigem()); comando.setString(5, registro.cidadeDestino()); comando.setObject(6, registro.peso());
            comando.setString(7, registro.coleta()); comando.setString(8, registro.situacao()); comando.setInt(9, registro.linhas());
            comando.setArray(10, comando.getConnection().createArrayOf("text", registro.documentos().toArray()));
            comando.setBoolean(11, registro.revisar());
            comando.setString(12, normalizarTexto(registro.numero() + registro.origem() + registro.destino()));
        });
    }

    public void substituirViagens(List<Map<String, Object>> registros) {
        bloquearPlanejamento();
        banco.update("DELETE FROM viagem_historica");
        List<Map<String, Object>> viagens = registros.stream().filter(registro -> !texto(registro.get("Placa Tração")).isEmpty()
                && converterData(registro.get("Dt. Carga (I)")) != null).toList();
        banco.batchUpdate("INSERT INTO viagem_historica VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(numero) DO NOTHING", viagens, 300, (comando, registro) -> {
            LocalDateTime inicio = converterData(registro.get("Dt. Carga (I)")), fim = converterData(registro.get("Dt. Carga (T)"));
            Double peso = converterNumero(registro.get("Peso Total")); boolean assumido = peso != null && peso == 0;
            comando.setString(1, texto(registro.get("Nº Viagem"))); comando.setString(2, texto(registro.get("Carregamento")));
            comando.setString(3, texto(registro.get("Destino"))); comando.setString(4, normalizarTexto(registro.get("Placa Tração")));
            comando.setString(5, normalizarTexto(registro.get("Placa Reboque"))); comando.setObject(6, inicio); comando.setObject(7, fim);
            comando.setObject(8, converterData(registro.get("Dt. Descarga (I)"))); comando.setObject(9, converterData(registro.get("Dt. Descarga (T)")));
            comando.setObject(10, peso); comando.setObject(11, assumido ? 32000d : peso); comando.setBoolean(12, assumido);
            comando.setObject(13, converterNumero(registro.get("Frete Total"))); comando.setObject(14, converterNumero(registro.get("KM Vazio")));
            comando.setObject(15, converterNumero(registro.get("KM Carregado"))); comando.setBoolean(16, fim != null && fim.isAfter(inicio));
        });
    }

    public List<Local> buscarLocais(String consulta) {
        List<String> termos = Arrays.stream(texto(consulta).split("\\s+"))
                .map(termo -> normalizarTexto(termo)).filter(termo -> !termo.isBlank()).distinct().toList();
        if (termos.isEmpty()) return List.of();
        String condicoes = String.join(" AND ", Collections.nCopies(termos.size(), "busca LIKE ?"));
        Object[] parametros = termos.stream().map(termo -> "%" + termo + "%").toArray();
        return banco.query("SELECT * FROM local_atendimento WHERE ativo AND " + condicoes
                + " ORDER BY latitude IS NULL, id NOT LIKE 'municipio-%',nome,id LIMIT 35",
                (linha, indice) -> lerLocal(linha), parametros);
    }

    public Set<String> listarRecursosReservados() {
        Set<String> recursos = new HashSet<>();
        banco.query("SELECT placa,carreta FROM alocacao_planejada WHERE ativa",
                (org.springframework.jdbc.core.RowCallbackHandler) linha -> {
                    recursos.add(normalizarTexto(linha.getString("placa")));
                    recursos.add(normalizarTexto(linha.getString("carreta")));
                });
        recursos.remove("");
        return recursos;
    }

    public Local resolverLocal(String nome, String cidade) {
        if (texto(nome).isBlank()) return null;
        List<Local> locais = banco.query("SELECT * FROM local_atendimento WHERE ativo AND ? = ANY(nomes_busca) AND cidade_busca=? AND latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY length(id),id",
                (linha, indice) -> lerLocal(linha), normalizarTexto(nome), normalizarTexto(cidade));
        Map<String, Local> coordenadas = new LinkedHashMap<>();
        for (Local local : locais) coordenadas.putIfAbsent(local.latitude() + "/" + local.longitude(), local);
        return coordenadas.size() == 1 ? coordenadas.values().iterator().next() : null;
    }

    public Local resolverOperacao(String nome, String cidadeTexto) {
        var correspondencia = PADRAO_CIDADE.matcher(texto(cidadeTexto));
        if (!correspondencia.matches()) return null;
        String cidade = correspondencia.group(1).strip() + "/" + correspondencia.group(2).toUpperCase(Locale.ROOT);
        Local local = resolverLocal(nome, cidade);
        if (local != null) return local;
        List<Local> encontrados = banco.query("SELECT * FROM local_atendimento WHERE id=?", (linha, indice) -> lerLocal(linha), "municipio-" + normalizarTexto(cidade));
        return encontrados.isEmpty() ? null : encontrados.getFirst();
    }

    public CorrespondenciaLocal resolverClienteImportacao(String entrada, String cidadeTexto) {
        var correspondencia = PADRAO_CIDADE.matcher(texto(cidadeTexto));
        if (!correspondencia.matches()) return new CorrespondenciaLocal(null, 0);
        String cidade = normalizarTexto(correspondencia.group(1) + "/" + correspondencia.group(2));
        String termo = normalizarTexto(entrada);
        if (termo.isBlank()) return new CorrespondenciaLocal(null, 0);
        boolean documento = termo.matches("[0-9]+");
        if (documento && termo.length() <= 14) termo = "0".repeat(14 - termo.length()) + termo;
        String consulta = documento ? "? = ANY(nomes_busca)" : "EXISTS(SELECT 1 FROM unnest(nomes_busca) nome WHERE nome LIKE ?)";
        var encontrados = banco.query("SELECT * FROM local_atendimento WHERE ativo AND id LIKE 'cliente-%' AND cidade_busca=? AND " + consulta
                + " ORDER BY length(id),id", (linha, indice) -> lerLocal(linha), cidade, documento ? termo : "%" + termo + "%");
        Local local=encontrados.isEmpty()?null:encontrados.getFirst();
        if(local!=null&&(local.latitude()==null||local.longitude()==null)){
            Local municipio=resolverOperacao("",cidadeTexto);
            if(municipio!=null)local=new Local(local.id(),local.nome(),local.cidade(),municipio.latitude(),municipio.longitude(),"municipio",local.detalhe());
        }
        return new CorrespondenciaLocal(local,encontrados.size());
    }

    public List<Composicao> listarComposicoes() {
        List<Composicao> composicoes = banco.query("""
            SELECT p.*,c.pbt AS cavalo_pbt,c.tara AS cavalo_tara,c.eixos AS cavalo_eixos,
              r.pbt AS carreta_pbt,r.tara AS carreta_tara,r.eixos AS carreta_eixos,r.classificacao
            FROM posicao_veiculo p LEFT JOIN veiculo c ON c.ativo AND c.placa=regexp_replace(upper(p.placa),'[^A-Z0-9]','','g')
            LEFT JOIN veiculo r ON r.ativo AND r.placa=regexp_replace(upper(p.carreta),'[^A-Z0-9]','','g') ORDER BY p.placa
            """, (linha, indice) -> {
                Double pbtCavalo = numero(linha, "cavalo_pbt"), taraCavalo = numero(linha, "cavalo_tara"),
                        pbtCarreta = numero(linha, "carreta_pbt"), taraCarreta = numero(linha, "carreta_tara"),
                        eixosCavalo = numero(linha, "cavalo_eixos"), eixosCarreta = numero(linha, "carreta_eixos");
                Double capacidade = pbtCavalo != null && taraCavalo != null && pbtCarreta != null && taraCarreta != null
                        && pbtCavalo > taraCavalo && pbtCarreta > taraCarreta ? pbtCavalo - taraCavalo + pbtCarreta - taraCarreta : null;
                return new Composicao(linha.getString("placa"), linha.getString("carreta"), linha.getString("grupo"), linha.getString("situacao"),
                        linha.getBoolean("engatada"), normalizarTexto(linha.getString("classificacao")).contains("SIDER"), linha.getString("cidade"),
                        numero(linha, "latitude"), numero(linha, "longitude"), formatarData(linha.getObject("ultima_posicao", LocalDateTime.class)),
                        linha.getString("origem_atual"), linha.getString("remetente_atual"), linha.getString("destino_atual"), linha.getString("destinatario_atual"),
                        capacidade, eixosCavalo == null || eixosCarreta == null ? null : eixosCavalo + eixosCarreta, null, null);
            });
        return composicoes.stream().map(item -> new Composicao(item.placa(), item.carreta(), item.grupo(), item.situacao(), item.engatada(),
                item.equipamentoSider(), item.cidade(), item.latitude(), item.longitude(), item.ultimaPosicao(), item.origemAtual(), item.remetenteAtual(),
                item.destinoAtual(), item.destinatarioAtual(), item.capacidade(), item.eixos(),
                resolverOperacao(item.remetenteAtual(), item.origemAtual()), resolverOperacao(item.destinatarioAtual(), item.destinoAtual()))).toList();
    }

    public List<CargaHistorica> buscarCargas(String consulta) {
        return banco.query("SELECT * FROM carga_historica WHERE busca LIKE ? ORDER BY length(numero) DESC,numero DESC LIMIT 70",
                (linha, indice) -> lerCarga(linha, false), "%" + normalizarTexto(consulta) + "%");
    }
    public CargaHistorica obterCarga(String numero) {
        List<CargaHistorica> cargas = banco.query("SELECT * FROM carga_historica WHERE numero=?", (linha, indice) -> lerCarga(linha, true), numero);
        if (cargas.isEmpty()) throw new IllegalArgumentException("Carga não encontrada.");
        return cargas.getFirst();
    }

    public Resumo obterResumo(String pasta) {
        List<Composicao> composicoes = listarComposicoes();
        Map<String, Long> contagens = new LinkedHashMap<>();
        for (Composicao item : composicoes) if (item.grupo().equals("SIDER")) contagens.merge(item.situacao(), 1L, Long::sum);
        List<Base> bases = new ArrayList<>();
        for (String[] configuracao : List.of(new String[]{"Clientes e coordenadas", "RELACAOCLIENTESCOORDENADAS"},
                new String[]{"Veículos", "RELACAOVEICULOS"}, new String[]{"Posição atual", "RELACAOPOSICAOATUAL"},
                new String[]{"Cargas históricas", "RELACAOCARGAS"}, new String[]{"Viagens históricas", "RELACAOVIAGENS"})) {
            List<Integer> linhas = banco.query("SELECT quantidade_linhas FROM importacao_base WHERE tipo=?", (linha, indice) -> linha.getInt(1), configuracao[1]);
            int quantidade=configuracao[1].equals("RELACAOCLIENTESCOORDENADAS")?banco.queryForObject("SELECT count(*) FROM local_atendimento WHERE id LIKE 'cliente-%'",Integer.class):
                configuracao[1].equals("RELACAOVEICULOS")?banco.queryForObject("SELECT count(*) FROM veiculo",Integer.class):
                configuracao[1].equals("RELACAOPOSICAOATUAL")?composicoes.size():linhas.isEmpty()?0:linhas.getFirst();
            bases.add(new Base(configuracao[0],quantidade,configuracao[1],!linhas.isEmpty()||quantidade>0));
        }
        LocalDateTime ultima = banco.queryForObject("SELECT MAX(ultima_posicao) FROM posicao_veiculo", LocalDateTime.class);
        String referencia = formatarData(ultima == null ? LocalDateTime.now().withSecond(0).withNano(0) : ultima.withSecond(0).withNano(0).plusMinutes(1));
        return new Resumo(referencia, bases, composicoes, contagens, banco.queryForObject("SELECT COUNT(*) FROM carga_historica", Integer.class), pasta, "Java 21 / Spring Boot", "PostgreSQL");
    }

    public List<CargaPlanejada> listarPlanejamento() {
        return banco.query("SELECT c.*,EXISTS(SELECT 1 FROM alocacao_planejada a WHERE a.carga=c.id AND a.ativa) AS alocada FROM carga_planejada c ORDER BY coleta,id", (linha, indice) -> lerPlanejamento(linha));
    }
    public CargaPlanejada obterPlanejamento(UUID id) {
        var encontrados = banco.query("SELECT c.*,EXISTS(SELECT 1 FROM alocacao_planejada a WHERE a.carga=c.id AND a.ativa) AS alocada FROM carga_planejada c WHERE c.id=?", (linha, indice) -> lerPlanejamento(linha), id);
        if (encontrados.isEmpty()) throw new IllegalArgumentException("Carga planejada não encontrada.");
        return encontrados.getFirst();
    }
    public CargaPlanejada criarPlanejamento(Carga carga) {
        UUID id = UUID.randomUUID();
        banco.update("INSERT INTO carga_planejada(id,identificacao,coleta,carga) VALUES(?,?,?,?::jsonb)",
                id, carga.identificacao(), converterData(carga.coleta()), json(carga));
        return obterPlanejamento(id);
    }
    public CargaPlanejada atualizarPlanejamento(UUID id, int versao, Carga carga) {
        conferirCargaLivre(id);
        int alteradas = banco.update("""
            UPDATE carga_planejada SET identificacao=?,coleta=?,carga=?::jsonb,versao=versao+1,atualizada_em=CURRENT_TIMESTAMP
            WHERE id=? AND versao=? AND situacao='PENDENTE'
            """, carga.identificacao(), converterData(carga.coleta()), json(carga), id, versao);
        conferirAlteracaoPlanejamento(alteradas);
        return obterPlanejamento(id);
    }
    public CargaPlanejada cancelarPlanejamento(UUID id, int versao) {
        conferirCargaLivre(id);
        int alteradas = banco.update("""
            UPDATE carga_planejada SET situacao='CANCELADA',versao=versao+1,atualizada_em=CURRENT_TIMESTAMP
            WHERE id=? AND versao=? AND situacao='PENDENTE'
            """, id, versao);
        conferirAlteracaoPlanejamento(alteradas);
        return obterPlanejamento(id);
    }
    private void conferirAlteracaoPlanejamento(int alteradas) {
        if (alteradas != 1) throw new IllegalArgumentException("Carga alterada, cancelada ou removida em outra sessão. Atualize a lista antes de continuar.");
    }
    public void bloquearPlanejamento() { banco.execute("SELECT pg_advisory_xact_lock(741116)"); }
    private void conferirCargaLivre(UUID id) {
        bloquearPlanejamento();
        if(Boolean.TRUE.equals(banco.queryForObject("SELECT EXISTS(SELECT 1 FROM alocacao_planejada WHERE carga=? AND ativa)",Boolean.class,id)))
            throw new IllegalArgumentException("Carga com alocação ativa. Libere o cenário antes de editar ou cancelar.");
    }
    private CargaPlanejada lerPlanejamento(ResultSet linha) throws SQLException {
        try {
            return new CargaPlanejada(linha.getObject("id", UUID.class), linha.getInt("versao"), linha.getString("situacao"),
                    conversor.readValue(linha.getString("carga"), Carga.class), linha.getString("criada_em"), linha.getString("atualizada_em"), linha.getBoolean("alocada"));
        } catch (JsonProcessingException erro) { throw new IllegalStateException("Falha ao ler carga planejada.", erro); }
    }

    public UUID salvarSimulacao(PedidoSimulacao pedido, Resultado resultado) {
        UUID identificador = UUID.randomUUID();
        banco.update("INSERT INTO simulacao(id,identificacao,carga,parametros,ajustes,resultado) VALUES(?,?,?::jsonb,?::jsonb,?::jsonb,?::jsonb)",
                identificador, texto(pedido.carga().identificacao()), json(pedido.carga()), json(resultado.parametros()),
                json(pedido.ajustes() == null ? Map.of() : pedido.ajustes()), json(resultado));
        return identificador;
    }
    public List<Map<String, Object>> listarSimulacoes() {
        return banco.queryForList("SELECT id,criada_em,identificacao,jsonb_array_length(resultado->'aprovadas') AS viaveis FROM simulacao ORDER BY criada_em DESC LIMIT 50");
    }
    private String json(Object valor) {
        try { return conversor.writeValueAsString(valor); } catch (JsonProcessingException erro) { throw new IllegalStateException("Falha ao registrar a simulação.", erro); }
    }
    private Local lerLocal(ResultSet linha) throws SQLException {
        return new Local(linha.getString("id"), linha.getString("nome"), linha.getString("cidade"), numero(linha, "latitude"),
                numero(linha, "longitude"), linha.getString("fonte"), linha.getString("detalhe"));
    }
    private CargaHistorica lerCarga(ResultSet linha, boolean resolver) throws SQLException {
        String origem = linha.getString("origem"), destino = linha.getString("destino"), cidadeOrigem = linha.getString("cidade_origem"), cidadeDestino = linha.getString("cidade_destino");
        List<String> documentos = Arrays.asList((String[]) linha.getArray("documentos").getArray());
        return new CargaHistorica(linha.getString("numero"), origem, destino, cidadeOrigem, cidadeDestino, numero(linha, "peso"), linha.getString("coleta"),
                linha.getString("situacao"), linha.getInt("linhas"), documentos, linha.getBoolean("revisar"), resolver ? resolverLocal(origem, cidadeOrigem) : null,
                resolver ? resolverLocal(destino, cidadeDestino) : null);
    }
    private Double numero(ResultSet linha, String campo) throws SQLException { Object valor = linha.getObject(campo); return valor == null ? null : ((Number) valor).doubleValue(); }
}
