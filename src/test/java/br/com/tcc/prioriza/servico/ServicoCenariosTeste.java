package br.com.tcc.prioriza.servico;
import br.com.tcc.prioriza.servico.ServicoCenarios.Opcao;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ServicoCenariosTeste {
 private Opcao opcao(UUID id,boolean obrigatoria,String coleta,String placa,String carreta,double nota) {
  return new Opcao(id,1,"Carga",obrigatoria,coleta,placa,carreta,nota,50d,2d,100d,2,"");
 }
 @Test void avaliacaoPreservadaNaEscolhaManualECompativelComRegistrosAntigos() throws Exception {
  UUID id=UUID.randomUUID();
  var avaliacao=new ServicoCenarios.Avaliacao("2026-11-20T10:00","2026-11-20T11:00","automatica",Map.of("prazo",90d),Map.of("prazo",45d),Map.of("prazo",.5),List.of("Previsão estimada."));
  var opcao=new Opcao(id,1,"Carga",false,"2026-11-20T12:00","A","X",90d,50d,1d,null,1,"Viável",avaliacao);
  var fixada=ServicoCenarios.recomendarComEscolhas(List.of(opcao),Set.of(),Map.of(id,"A")).getFirst();
  assertEquals(avaliacao,fixada.avaliacao());
  var conversor=new com.fasterxml.jackson.databind.ObjectMapper();
  assertEquals(fixada,conversor.readValue(conversor.writeValueAsString(fixada),Opcao.class));
  var antigo=conversor.valueToTree(fixada);((com.fasterxml.jackson.databind.node.ObjectNode)antigo).remove("avaliacao");
  assertNull(conversor.treeToValue(antigo,Opcao.class).avaliacao());
 }
 @Test void menosAlternativasTemPrioridade(){
  UUID a=UUID.randomUUID(),b=UUID.randomUUID();
  var lista=List.of(opcao(a,false,"2026-11-20T10:00","A","X",99),opcao(a,false,"2026-11-20T10:00","B","Y",90),opcao(b,false,"2026-11-20T10:00","A","X",70));
  var resultado=ServicoCenarios.recomendar(lista,Set.of());
  assertEquals(b,resultado.getFirst().carga());assertEquals("B",resultado.get(1).placa());
 }
 @Test void maiorPerdaDeNotaDesempata(){
  UUID a=UUID.randomUUID(),b=UUID.randomUUID();
  var lista=List.of(opcao(a,false,"2026-11-20T10:00","A","X",99),opcao(a,false,"2026-11-20T10:00","B","Y",98),opcao(b,false,"2026-11-20T10:00","A","X",90),opcao(b,false,"2026-11-20T10:00","C","Z",30));
  var resultado=ServicoCenarios.recomendar(lista,Set.of());assertEquals(b,resultado.getFirst().carga());assertEquals("B",resultado.get(1).placa());
 }
 @Test void cadastroMaisAntigoDesempataSemDependerDaOrdemDeEntrada(){
  UUID a=UUID.randomUUID(),b=UUID.randomUUID();
  var lista=List.of(opcao(a,false,"2026-11-20T10:00","A","X",90),opcao(b,false,"2026-11-20T10:00","A","X",90));
  var datas=Map.of(a,"2026-11-19 10:00",b,"2026-11-18 10:00");
  assertEquals(b,ServicoCenarios.recomendarComEscolhas(lista,Set.of(),Map.of(),datas).getFirst().carga());
  assertEquals(b,ServicoCenarios.recomendarComEscolhas(lista.reversed(),Set.of(),Map.of(),datas).getFirst().carga());
 }
 @Test void alternativasSaoRecontadasAposCadaAtribuicao(){
  UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();String coleta="2026-11-20T10:00";
  var lista=List.of(opcao(a,true,coleta,"A","X",90),opcao(b,false,coleta,"A","X",95),opcao(b,false,coleta,"B","Y",90),opcao(b,false,coleta,"C","Z",89),opcao(c,false,coleta,"B","Y",90),opcao(c,false,coleta,"D","W",80));
  var resultado=ServicoCenarios.recomendar(lista,Set.of());
  assertEquals(a,resultado.get(0).carga());assertEquals(c,resultado.get(1).carga());assertEquals("C",resultado.get(2).placa());
 }
 @Test void obrigatoriaTemPrioridadeMesmoComColetaPosterior(){
  var normal=opcao(UUID.randomUUID(),false,"2026-11-11T10:00","DMO3011","DMO3012",99);
  var obrigatoria=opcao(UUID.randomUUID(),true,"2026-11-11T12:00","DMO3011","DMO3012",70);
  assertEquals(List.of(obrigatoria),ServicoCenarios.recomendar(List.of(normal,obrigatoria),Set.of()));
 }
 @Test void naoRepeteCavaloNemCarretaEUsaAlternativaLivre(){
  UUID a=UUID.randomUUID(),b=UUID.randomUUID();
  var candidatos=List.of(opcao(a,true,"2026-11-11T10:00","A","X",99),opcao(b,false,"2026-11-11T12:00","B","X",90),opcao(b,false,"2026-11-11T12:00","C","Y",80));
  var resultado=ServicoCenarios.recomendar(candidatos,Set.of());
  assertEquals(2,resultado.size());assertEquals("C",resultado.get(1).placa());
 }
 @Test void placasNormalizadasRespeitamReservas(){
  var opcao=opcao(UUID.randomUUID(),true,"2026-11-11T10:00","DMO-3011","DMO3012",90);
  assertTrue(ServicoCenarios.recomendar(List.of(opcao),Set.of("DMO3011")).isEmpty());
 }
 @Test void cadaCargaRecebeUmaComposicao(){
  UUID id=UUID.randomUUID();var lista=List.of(opcao(id,false,"2026-11-11T10:00","A","X",90),opcao(id,false,"2026-11-11T10:00","B","Y",80));
  assertEquals(1,ServicoCenarios.recomendar(lista,Set.of()).size());
 }
 @Test void propostasVaziasNaoInventamAlocacoes(){assertTrue(ServicoCenarios.recomendar(List.of(),Set.of()).isEmpty());}
 @Test void mesmaPrioridadeAtendeColetaMaisProxima(){
  var cedo=opcao(UUID.randomUUID(),false,"2026-11-11T10:00","A","X",50);
  var tarde=opcao(UUID.randomUUID(),false,"2026-11-11T12:00","A","X",99);
  assertEquals(cedo,ServicoCenarios.recomendar(List.of(tarde,cedo),Set.of()).getFirst());
 }
 @Test void manualDa129RedistribuiSugestaoAutomaticaDa126(){
  UUID carga126=UUID.randomUUID(),carga129=UUID.randomUUID();
  var lista=List.of(opcao(carga126,false,"2026-11-20T22:00","DMO3016","DMO3017",99),opcao(carga126,false,"2026-11-20T22:00","DMO3018","DMO3019",90),opcao(carga129,false,"2026-11-21T11:00","DMO3016","DMO3017",80),opcao(carga129,false,"2026-11-21T11:00","DMO3020","DMO3021",95));
  var resultado=ServicoCenarios.recomendarComEscolhas(lista,Set.of(),Map.of(carga129,"DMO3016"));
  assertEquals(2,resultado.size());assertEquals("DMO3018",resultado.stream().filter(o->o.carga().equals(carga126)).findFirst().orElseThrow().placa());
  assertEquals("DMO3016",resultado.stream().filter(o->o.carga().equals(carga129)).findFirst().orElseThrow().placa());
 }
 @Test void duasEscolhasManuaisNaoPodemCompartilharCavalo(){
  UUID a=UUID.randomUUID(),b=UUID.randomUUID();
  var lista=List.of(opcao(a,false,"2026-11-20T22:00","DMO-3011","DMO3022",90),opcao(b,false,"2026-11-21T11:00","DMO3011","DMO3023",80));
  var erro=assertThrows(IllegalArgumentException.class,()->ServicoCenarios.recomendarComEscolhas(lista,Set.of(),Map.of(a,"dmo3011",b,"DMO-3011")));
  assertTrue(erro.getMessage().contains("Conflito entre escolhas manuais"));
 }
 @Test void duasEscolhasManuaisNaoPodemCompartilharCarreta(){
  UUID a=UUID.randomUUID(),b=UUID.randomUUID();
  var lista=List.of(opcao(a,false,"2026-11-20T22:00","DMO3011","DMO-3012",90),opcao(b,false,"2026-11-21T11:00","DMO3015","DMO3012",80));
  assertThrows(IllegalArgumentException.class,()->ServicoCenarios.recomendarComEscolhas(lista,Set.of(),Map.of(a,"DMO3011",b,"DMO3015")));
 }
 @Test void manualRespeitaReservasExistentesDeCavaloOuCarreta(){
  UUID id=UUID.randomUUID();var lista=List.of(opcao(id,false,"2026-11-20T22:00","DMO3011","DMO3012",90));
  for(String recurso:List.of("DMO-3011","DMO3012"))assertTrue(assertThrows(IllegalArgumentException.class,()->ServicoCenarios.recomendarComEscolhas(lista,Set.of(recurso),Map.of(id,"DMO3011"))).getMessage().contains("reservado em outro cenário"));
 }
 @Test void escolhaInviavelOuCargaForaDaSelecaoNaoEIgnorada(){
  UUID id=UUID.randomUUID();var lista=List.of(opcao(id,false,"2026-11-20T22:00","DMO3011","DMO3012",90));
  assertThrows(IllegalArgumentException.class,()->ServicoCenarios.recomendarComEscolhas(lista,Set.of(),Map.of(id,"INEXISTENTE")));
  assertThrows(IllegalArgumentException.class,()->ServicoCenarios.recomendarComEscolhas(lista,Set.of(),Map.of(UUID.randomUUID(),"DMO3011")));
 }
 @Test void semAlternativaAManualPermaneceEAOutraCargaFicaSemAlocacao(){
  UUID a=UUID.randomUUID(),b=UUID.randomUUID();var lista=List.of(opcao(a,true,"2026-11-20T22:00","DMO3011","DMO3012",99),opcao(b,false,"2026-11-21T11:00","DMO3011","DMO3012",80));
  var resultado=ServicoCenarios.recomendarComEscolhas(lista,Set.of(),Map.of(b,"DMO3011"));
  assertEquals(1,resultado.size());assertEquals(b,resultado.getFirst().carga());
 }
 @Test void automaticasNaoPodemUsarCarretaFixadaNemComOutroCavalo(){
  UUID a=UUID.randomUUID(),b=UUID.randomUUID();
  var lista=List.of(opcao(a,false,"2026-11-20T22:00","DMO3011","DMO3012",99),opcao(a,false,"2026-11-20T22:00","DMO3015","DMO3023",80),opcao(b,false,"2026-11-21T11:00","DMO3024","DMO3012",70));
  var resultado=ServicoCenarios.recomendarComEscolhas(lista,Set.of(),Map.of(b,"DMO3024"));assertEquals("DMO3015",resultado.stream().filter(o->o.carga().equals(a)).findFirst().orElseThrow().placa());
 }
 @Test void semEscolhasMantemRegraAnteriorENaoMudaEntradas(){
  UUID id=UUID.randomUUID();var lista=List.of(opcao(id,false,"2026-11-20T22:00","DMO3011","DMO3012",90));
  Set<String> reservas=new HashSet<>();Map<UUID,String> escolhas=new HashMap<>();
  assertEquals(ServicoCenarios.recomendar(lista,reservas),ServicoCenarios.recomendarComEscolhas(lista,reservas,escolhas));assertTrue(reservas.isEmpty());assertTrue(escolhas.isEmpty());
 }
 @Test void propostaAntigaQueNaoAtendeuEscolhaManualNaoPodeSerConfirmada(){
  UUID id=UUID.randomUUID();var lista=List.of(opcao(id,false,"2026-11-20T22:00","DMO3011","DMO3012",90));
  assertThrows(IllegalArgumentException.class,()->ServicoCenarios.validarEscolhasDaProposta(lista,Map.of(id,"DMO3015")));
  assertThrows(IllegalArgumentException.class,()->ServicoCenarios.validarEscolhasDaProposta(List.of(),Map.of(id,"DMO3011")));
  assertDoesNotThrow(()->ServicoCenarios.validarEscolhasDaProposta(lista,Map.of(id,"dmo-3011")));
 }
}
