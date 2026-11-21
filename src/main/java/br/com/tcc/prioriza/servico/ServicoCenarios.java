package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Modelos.*;
import br.com.tcc.prioriza.repositorio.RepositorioOperacional;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static br.com.tcc.prioriza.servico.FuncoesDados.*;

@Service
public class ServicoCenarios {
    public record Pedido(List<UUID> cargas, String referencia, Parametros parametros, Map<String,Ajuste> ajustes, Map<UUID,String> escolhas) { }
    public record Avaliacao(String disponivelEm, String chegada, String tipoDisponibilidade,
            Map<String,Double> notas, Map<String,Double> contribuicoes, Map<String,Double> pesos, List<String> avisos,
            String cidade, Double capacidade, String liberacao, Double faturamentoDia, Previsao previsaoDisponibilidade) {
        public Avaliacao(String disponivelEm, String chegada, String tipoDisponibilidade,
                Map<String,Double> notas, Map<String,Double> contribuicoes, Map<String,Double> pesos, List<String> avisos) {
            this(disponivelEm,chegada,tipoDisponibilidade,notas,contribuicoes,pesos,avisos,null,null,null,null,null);
        }
    }
    public record Opcao(UUID carga, int versao, String identificacao, boolean obrigatoria, String coleta,
            String placa, String carreta, Double nota, Double distancia, Double folga, Double combustivel,
            int alternativas, String motivo, Avaliacao avaliacao) {
        public Opcao(UUID carga, int versao, String identificacao, boolean obrigatoria, String coleta,
                String placa, String carreta, Double nota, Double distancia, Double folga, Double combustivel,
                int alternativas, String motivo) {
            this(carga,versao,identificacao,obrigatoria,coleta,placa,carreta,nota,distancia,folga,combustivel,alternativas,motivo,null);
        }
    }
    public record Comparacao(String metodo, int atendidas, int obrigatoriasAtendidas, int obrigatoriasPendentes,
            double distanciaVazia, Double combustivel, Double menorFolga) { }
    public record ResultadoCenario(UUID id, String situacao, List<Opcao> recomendacoes, List<Comparacao> comparacao, List<String> avisos,
            List<Opcao> alternativas, Map<UUID,String> escolhas) { }
    public record Atraso(String cliente, String municipio, String etapa, String previsto, String executado, String observacao) { }
    private final JdbcTemplate banco;
    private final RepositorioOperacional repositorio;
    private final MotorRecomendacao motor;
    private final ObjectMapper conversor;
    public ServicoCenarios(JdbcTemplate banco, RepositorioOperacional repositorio, MotorRecomendacao motor, ObjectMapper conversor) {
        this.banco=banco;this.repositorio=repositorio;this.motor=motor;this.conversor=conversor;
    }
    private String json(Object valor) { try{return conversor.writeValueAsString(valor);}catch(Exception erro){throw new IllegalArgumentException("Dados de cenário inválidos.");} }
    private String bases() {return banco.queryForObject("SELECT md5(COALESCE(string_agg(tipo||sha256,',' ORDER BY tipo),'')) || ':' || (SELECT revisao FROM controle_cadastro WHERE id=1) FROM importacao_base",String.class);}
    private void bloquear() {banco.execute("SELECT pg_advisory_xact_lock(741116)");}
    public Set<String> recursosReservados() {
        Set<String> recursos=new HashSet<>();
        banco.query("SELECT placa,carreta FROM alocacao_planejada WHERE ativa", (org.springframework.jdbc.core.RowCallbackHandler) linha->{recursos.add(normalizarTexto(linha.getString(1)));recursos.add(normalizarTexto(linha.getString(2)));});
        return recursos;
    }
    public static List<Opcao> selecionar(List<Opcao> candidatas, Set<String> ocupados) {
        Set<String> usados=new HashSet<>(ocupados); Set<UUID> atendidas=new HashSet<>(); List<Opcao> resultado=new ArrayList<>();
        for(Opcao opcao:candidatas) {
            if(atendidas.contains(opcao.carga()))continue;
            if(opcao.placa()!=null&&!usados.contains(normalizarTexto(opcao.placa()))&&!usados.contains(normalizarTexto(opcao.carreta()))) {
                usados.add(normalizarTexto(opcao.placa()));usados.add(normalizarTexto(opcao.carreta()));atendidas.add(opcao.carga());resultado.add(opcao);
            }
        }
        return resultado;
    }
    private static final Comparator<Opcao> ORDEM = Comparator.comparing(Opcao::obrigatoria).reversed()
            .thenComparing(Opcao::coleta).thenComparing(Opcao::carga).thenComparing(Opcao::nota,Comparator.reverseOrder()).thenComparing(Opcao::distancia);
    public static List<Opcao> recomendar(List<Opcao> candidatas, Set<String> ocupados) {
        return recomendar(candidatas,ocupados,Map.of());
    }
    private record GrupoCarga(Opcao melhor, int quantidade, double perda) { }
    private static List<Opcao> recomendar(List<Opcao> candidatas, Set<String> ocupados, Map<UUID,String> criadasEm) {
        Set<String> usados=new HashSet<>();for(String recurso:ocupados)usados.add(normalizarTexto(recurso));
        Set<UUID> atendidas=new HashSet<>();List<Opcao> resultado=new ArrayList<>();
        Comparator<Opcao> porNota=Comparator.comparing(Opcao::nota,Comparator.reverseOrder())
                .thenComparing(Opcao::distancia).thenComparing(Opcao::placa).thenComparing(Opcao::carreta);
        Comparator<GrupoCarga> prioridade=Comparator.comparing((GrupoCarga g)->g.melhor().obrigatoria()).reversed()
                .thenComparing(g->g.melhor().coleta()).thenComparingInt(GrupoCarga::quantidade)
                .thenComparing(Comparator.comparingDouble(GrupoCarga::perda).reversed())
                .thenComparing(g->criadasEm.getOrDefault(g.melhor().carga(),""))
                .thenComparing(g->g.melhor().carga());
        while(true) {
            Map<UUID,Map<String,Opcao>> livres=new HashMap<>();
            for(Opcao opcao:candidatas) {
                String cavalo=normalizarTexto(opcao.placa()),carreta=normalizarTexto(opcao.carreta());
                if(atendidas.contains(opcao.carga())||cavalo.isBlank()||carreta.isBlank()||usados.contains(cavalo)||usados.contains(carreta))continue;
                livres.computeIfAbsent(opcao.carga(),id->new HashMap<>()).merge(cavalo+"/"+carreta,opcao,
                        (a,b)->porNota.compare(a,b)<=0?a:b);
            }
            var grupo=livres.values().stream().map(opcoes->{
                var ordenadas=opcoes.values().stream().sorted(porNota).toList();
                double perda=ordenadas.size()>1?ordenadas.getFirst().nota()-ordenadas.get(1).nota():ordenadas.getFirst().nota();
                return new GrupoCarga(ordenadas.getFirst(),ordenadas.size(),perda);
            }).min(prioridade);
            if(grupo.isEmpty())break;
            var escolhido=grupo.get().melhor();
            usados.add(normalizarTexto(escolhido.placa()));usados.add(normalizarTexto(escolhido.carreta()));
            atendidas.add(escolhido.carga());resultado.add(escolhido);
        }
        return resultado;
    }
    public static List<Opcao> recomendarComEscolhas(List<Opcao> candidatas, Set<String> ocupados, Map<UUID,String> escolhas) {
        return recomendarComEscolhas(candidatas,ocupados,escolhas,Map.of());
    }
    static List<Opcao> recomendarComEscolhas(List<Opcao> candidatas, Set<String> ocupados, Map<UUID,String> escolhas, Map<UUID,String> criadasEm) {
        Map<UUID,String> manuais=escolhas==null?Map.of():escolhas;
        Set<String> usados=new HashSet<>();for(String recurso:ocupados)usados.add(normalizarTexto(recurso));
        Map<String,Opcao> donos=new HashMap<>();List<Opcao> fixadas=new ArrayList<>();
        var entradas=manuais.entrySet().stream().sorted(Comparator.comparing(e->String.valueOf(e.getKey()))).toList();
        // As escolhas explicitas ocupam seus recursos na proposta antes da distribuicao automatica.
        for(var escolha:entradas){
            Opcao opcao=candidatas.stream().filter(o->o.carga().equals(escolha.getKey())&&normalizarTexto(o.placa()).equals(normalizarTexto(escolha.getValue())))
                .sorted(ORDEM).findFirst().orElseThrow(()->new IllegalArgumentException("Composição escolhida não é viável para a carga "+escolha.getKey()+". Reavalie as alternativas."));
            String cavalo=normalizarTexto(opcao.placa()),carreta=normalizarTexto(opcao.carreta());
            if(cavalo.isBlank()||carreta.isBlank()||cavalo.equals(carreta))throw new IllegalArgumentException("Composição manual inválida para a carga "+opcao.identificacao()+".");
            for(String recurso:List.of(cavalo,carreta)){
                Opcao anterior=donos.get(recurso);
                if(anterior!=null)throw new IllegalArgumentException("Conflito entre escolhas manuais: o recurso "+recurso+" foi escolhido para as cargas "+anterior.identificacao()+" e "+opcao.identificacao()+". Escolha composições distintas.");
                if(usados.contains(recurso))throw new IllegalArgumentException("Escolha manual da carga "+opcao.identificacao()+": o recurso "+recurso+" já está reservado em outro cenário. Libere a reserva ou escolha outra composição.");
            }
            usados.add(cavalo);usados.add(carreta);donos.put(cavalo,opcao);donos.put(carreta,opcao);
            fixadas.add(new Opcao(opcao.carga(),opcao.versao(),opcao.identificacao(),opcao.obrigatoria(),opcao.coleta(),opcao.placa(),opcao.carreta(),opcao.nota(),opcao.distancia(),opcao.folga(),opcao.combustivel(),opcao.alternativas(),opcao.motivo()+" Escolha manual fixada na proposta.",opcao.avaliacao()));
        }
        var automaticas=candidatas.stream().filter(o->!manuais.containsKey(o.carga())).toList();
        fixadas.addAll(recomendar(automaticas,usados,criadasEm));fixadas.sort(ORDEM);return fixadas;
    }
    @Transactional public ResultadoCenario simular(Pedido pedido) {
        if(pedido==null||pedido.cargas()==null||pedido.cargas().isEmpty()||pedido.cargas().size()>100||new HashSet<>(pedido.cargas()).size()!=pedido.cargas().size())
            throw new IllegalArgumentException("Selecione de 1 a 100 cargas distintas.");
        if(converterData(pedido.referencia())==null)throw new IllegalArgumentException("Informe a referência do cenário.");
        bloquear(); String assinatura=bases();
        List<CargaPlanejada> cargas=new ArrayList<>(); List<Opcao> candidatas=new ArrayList<>();
        var composicoes=repositorio.listarComposicoes(); var medias=repositorio.listarMedias();
        Set<String> ocupados=recursosReservados();
        if(pedido.escolhas()!=null)for(var escolha:pedido.escolhas().entrySet()) {
            String placa=normalizarTexto(escolha.getValue());
            var composicao=composicoes.stream().filter(item->normalizarTexto(item.placa()).equals(placa)).findFirst();
            if(ocupados.contains(placa)||composicao.map(item->ocupados.contains(normalizarTexto(item.carreta()))).orElse(false))
                throw new IllegalArgumentException("A composição "+placa+" já possui cavalo ou carreta reservado em outra carga. Cancele a reserva existente ou escolha outra composição.");
        }
        for(UUID id:pedido.cargas()) {
            var registro=repositorio.obterPlanejamento(id);if(!registro.situacao().equals("PENDENTE"))throw new IllegalArgumentException("Carga cancelada na seleção.");
            if(Boolean.TRUE.equals(banco.queryForObject("SELECT EXISTS(SELECT 1 FROM alocacao_planejada WHERE carga=? AND ativa)",Boolean.class,id)))throw new IllegalArgumentException("Carga já possui alocação ativa.");
            cargas.add(registro); var c=registro.carga();
            if(converterData(c.coleta()).isBefore(converterData(pedido.referencia())))throw new IllegalArgumentException("Coleta anterior a referência: "+c.identificacao());
            var atual=new Carga(c.identificacao(),c.origem(),c.destino(),pedido.referencia(),c.coleta(),c.peso(),c.frete(),c.tempoCarga(),c.tempoDescarga(),c.obrigatoria());
            var resultado=motor.avaliarComposicoes(atual,composicoes,pedido.ajustes(),pedido.parametros(),medias,ocupados);
            for(var alternativa:resultado.aprovadas()) {
                var avaliacao=new Avaliacao(alternativa.disponivelEm(),alternativa.chegada(),alternativa.previsaoDisponibilidade().tipo(),
                        alternativa.notas(),alternativa.contribuicoes(),resultado.pesos(),alternativa.avisos(),
                        alternativa.composicao().cidade(),alternativa.composicao().capacidade(),alternativa.liberacao(),alternativa.faturamentoDia(),alternativa.previsaoDisponibilidade());
                candidatas.add(new Opcao(id,registro.versao(),c.identificacao(),c.obrigatoria(),c.coleta(),alternativa.composicao().placa(),alternativa.composicao().carreta(),alternativa.nota(),alternativa.distancia(),alternativa.folga(),resultado.custosCombustivel().get(alternativa.composicao().placa()),resultado.aprovadas().size(),"Viável. Situação: " + alternativa.composicao().situacao() + ". Segurança: " + alternativa.notas().get("seguranca") + "/100 (peso 5%; faixa de 5 km).",avaliacao));
            }
        }
        Map<UUID,String> escolhas=new LinkedHashMap<>();
        if(pedido.escolhas()!=null)pedido.escolhas().forEach((carga,placa)->escolhas.put(carga,normalizarTexto(placa)));
        Map<UUID,String> criadasEm=new HashMap<>();for(var carga:cargas)criadasEm.put(carga.id(),carga.criadaEm());
        var recomendadas=recomendarComEscolhas(candidatas,ocupados,escolhas,criadasEm);
        var proximas=selecionar(candidatas.stream().sorted(Comparator.comparing(Opcao::coleta).thenComparing(Opcao::carga).thenComparing(Opcao::distancia)).toList(),ocupados);
        var comparacoes=List.of(medir("Priorizacao proposta",recomendadas,cargas,pedido.parametros()),medir("Referência: coleta e menor distância",proximas,cargas,pedido.parametros()));
        List<Opcao> todas=new ArrayList<>(recomendadas);
        for(var registro:cargas)if(recomendadas.stream().noneMatch(o->o.carga().equals(registro.id()))) {
            var c=registro.carga();todas.add(new Opcao(registro.id(),registro.versao(),c.identificacao(),c.obrigatoria(),c.coleta(),null,null,null,null,null,null,0,"Sem composição viável livre neste cenário; nenhuma restrição foi relaxada."));
        }
        UUID id=UUID.randomUUID(); var resultado=new ResultadoCenario(id,"SIMULADO",todas,comparacoes,List.of(
            "Escolhas manuais viaveis fixadas primeiro. Para as demais: obrigatorias, coleta mais proxima, menos composicoes livres, maior perda de nota entre a primeira e segunda opcao, cadastro mais antigo. Alternativas recalculadas a cada atribuicao; nao garante otimo global.",
            "Uma utilizacao por cavalo e carreta no planejamento ativo, mesmo em horarios distintos. Viagens sequenciais ainda nao modeladas.",
            "Confirmar reserva recursos; simular nao reserva. Referencia comparativa usa as mesmas alternativas viaveis, por coleta e menor distancia, sem prioridade obrigatoria.",
            "Metricas sao estimadas no mesmo conjunto de dados; nao demonstram reducao real de custos ou atrasos. Atrasos registrados por cliente sao informativos, sem penalidade automatica.",
            "Escolhas manuais nao autorizam recurso repetido, reservado ou inviavel. Conflitos sao recusados; as cargas automaticas buscam alternativas sem utilizar recursos fixados. Recalcule antes de confirmar."),
            candidatas.stream().map(o->new Opcao(o.carga(),o.versao(),o.identificacao(),o.obrigatoria(),o.coleta(),o.placa(),o.carreta(),o.nota(),o.distancia(),o.folga(),o.combustivel(),o.alternativas(),o.motivo())).toList(),escolhas);
        banco.update("INSERT INTO cenario_planejamento(id,situacao,bases,pedido,resultado) VALUES(?,'SIMULADO',?,?::jsonb,?::jsonb)",id,assinatura,json(pedido),json(resultado));
        return resultado;
    }
    private Comparacao medir(String nome,List<Opcao> alocacoes,List<CargaPlanejada> cargas,Parametros parametros) {
        int obrigatorias=(int)cargas.stream().filter(c->c.carga().obrigatoria()).count();
        int atendidas=(int)alocacoes.stream().filter(Opcao::obrigatoria).count();
        return new Comparacao(nome,alocacoes.size(),atendidas,obrigatorias-atendidas,alocacoes.stream().mapToDouble(Opcao::distancia).sum(),
            parametros!=null&&parametros.precoCombustivel()!=null?alocacoes.stream().mapToDouble(o->o.combustivel()==null?0:o.combustivel()).sum():null,
            alocacoes.stream().map(Opcao::folga).min(Double::compare).orElse(null));
    }
    static void validarEscolhasDaProposta(List<Opcao> recomendacoes,Map<UUID,String> escolhas){
        if(escolhas==null)return;
        for(var escolha:escolhas.entrySet()){
            Opcao opcao=recomendacoes.stream().filter(o->o.carga().equals(escolha.getKey())).findFirst().orElse(null);
            if(opcao==null||opcao.placa()==null||normalizarTexto(escolha.getValue()).isBlank()||!normalizarTexto(opcao.placa()).equals(normalizarTexto(escolha.getValue())))
                throw new IllegalArgumentException("A proposta não respeita a escolha manual da carga "+(opcao==null?escolha.getKey():opcao.identificacao())+". Recalcule antes de confirmar.");
        }
    }
    @Transactional public Map<String,Object> confirmar(UUID id) {
        bloquear(); var linha=banco.queryForList("SELECT * FROM cenario_planejamento WHERE id=? FOR UPDATE",id);
        if(linha.isEmpty()||!linha.getFirst().get("situacao").equals("SIMULADO"))throw new IllegalArgumentException("Cenário inexistente ou já encerrado.");
        if(!bases().equals(linha.getFirst().get("bases")))throw new IllegalArgumentException("Bases alteradas. Simule novamente antes de confirmar.");
        ResultadoCenario resultado;try{resultado=conversor.readValue(linha.getFirst().get("resultado").toString(),ResultadoCenario.class);}catch(Exception e){throw new IllegalArgumentException("Cenário inválido.");}
        validarEscolhasDaProposta(resultado.recomendacoes(),resultado.escolhas());
        Set<String> usados=recursosReservados();
        if(resultado.recomendacoes().stream().noneMatch(o->o.placa()!=null))throw new IllegalArgumentException("Nenhuma recomendacao para confirmar.");
        for(var opcao:resultado.recomendacoes()) {
            var carga=banco.queryForMap("SELECT versao,situacao FROM carga_planejada WHERE id=? FOR UPDATE",opcao.carga());
            if(((Number)carga.get("versao")).intValue()!=opcao.versao()||!carga.get("situacao").equals("PENDENTE"))throw new IllegalArgumentException("Carga alterada. Simule novamente.");
            if(opcao.placa()==null)continue;
            if(usados.contains(normalizarTexto(opcao.placa()))||usados.contains(normalizarTexto(opcao.carreta())))throw new IllegalArgumentException("Recurso reservado por outro cenário. Simule novamente.");
            if(Boolean.TRUE.equals(banco.queryForObject("SELECT EXISTS(SELECT 1 FROM alocacao_planejada WHERE carga=? AND ativa)",Boolean.class,opcao.carga())))throw new IllegalArgumentException("Carga já alocada. Simule novamente.");
            usados.add(normalizarTexto(opcao.placa()));usados.add(normalizarTexto(opcao.carreta()));
            banco.update("INSERT INTO alocacao_planejada(id,cenario,carga,placa,carreta) VALUES(?,?,?,?,?)",UUID.randomUUID(),id,opcao.carga(),normalizarTexto(opcao.placa()),normalizarTexto(opcao.carreta()));
        }
        banco.update("UPDATE cenario_planejamento SET situacao='CONFIRMADO' WHERE id=?",id);return Map.of("situacao","CONFIRMADO");
    }
    public List<Map<String,Object>> listarReservas() {
        return banco.queryForList("SELECT id,cenario,carga,placa,carreta FROM alocacao_planejada WHERE ativa ORDER BY carga");
    }
    @Transactional public void cancelarReserva(UUID id) {
        cancelarReservas(List.of(id));
    }
    @Transactional public void cancelarReservas(List<UUID> ids) {
        if(ids==null||ids.isEmpty()||ids.size()>500||ids.stream().anyMatch(Objects::isNull)||new HashSet<>(ids).size()!=ids.size())
            throw new IllegalArgumentException("Selecione de 1 a 500 reservas distintas.");
        bloquear();
        Set<Object> cenarios=new HashSet<>();
        for(UUID id:ids.stream().sorted().toList()) {
            var reservas=banco.queryForList("SELECT cenario FROM alocacao_planejada WHERE id=? AND ativa FOR UPDATE",id);
            if(reservas.isEmpty())throw new IllegalArgumentException("Uma reserva foi alterada ou já cancelada. Nenhuma reserva deste pedido foi cancelada. Atualize a lista.");
            cenarios.add(reservas.getFirst().get("cenario"));
        }
        for(UUID id:ids)banco.update("UPDATE alocacao_planejada SET ativa=false WHERE id=? AND ativa",id);
        for(Object cenario:cenarios)banco.update("UPDATE cenario_planejamento SET situacao='CANCELADO' WHERE id=? AND situacao='CONFIRMADO' AND NOT EXISTS (SELECT 1 FROM alocacao_planejada WHERE cenario=? AND ativa)",cenario,cenario);
    }
    @Transactional public void liberar(UUID id) {
        bloquear();int alteradas=banco.update("UPDATE cenario_planejamento SET situacao='CANCELADO' WHERE id=? AND situacao IN ('SIMULADO','CONFIRMADO')",id);
        if(alteradas==0)throw new IllegalArgumentException("Cenário inexistente ou encerrado.");
        banco.update("UPDATE alocacao_planejada SET ativa=false WHERE cenario=?",id);
    }
    public List<Map<String,Object>> listar() {return banco.queryForList("SELECT id,criado_em,situacao,resultado::text AS resultado,pedido::text AS pedido FROM cenario_planejamento WHERE situacao='CONFIRMADO' OR id IN (SELECT id FROM cenario_planejamento ORDER BY criado_em DESC LIMIT 50) ORDER BY criado_em DESC");}
    @Transactional public void registrarAtraso(Atraso atraso) {
        if(atraso==null||texto(atraso.cliente()).isBlank()||TemposHistoricos.normalizarMunicipio(atraso.municipio()).isBlank()||!Set.of("CARGA","DESCARGA").contains(texto(atraso.etapa())))throw new IllegalArgumentException("Informe cliente, município/UF e etapa.");
        var previsto=converterData(atraso.previsto());var executado=converterData(atraso.executado());
        if(previsto==null||executado==null)throw new IllegalArgumentException("Informe os horários previsto e executado.");
        LocalDateTime agora=LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        if(executado.isAfter(agora))throw new IllegalArgumentException("Horário executado não pode estar no futuro.");
        banco.update("INSERT INTO atraso_cliente(id,cliente,municipio,etapa,previsto,executado,observacao) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID(),normalizarTexto(atraso.cliente()),TemposHistoricos.normalizarMunicipio(atraso.municipio()),atraso.etapa(),previsto,executado,texto(atraso.observacao()));
    }
    public List<Map<String,Object>> atrasos() {
        return banco.queryForList("""
            SELECT cliente,municipio,etapa,count(*) AS registros,count(*) FILTER(WHERE executado>previsto) AS atrasos,
            round(avg(greatest(0,extract(epoch FROM (executado-previsto))/60))::numeric,2) AS media_minutos,
            round(100.0*count(*) FILTER(WHERE executado>previsto)/count(*),2) AS percentual
            FROM atraso_cliente GROUP BY cliente,municipio,etapa ORDER BY cliente,municipio,etapa
            """);
    }
}
