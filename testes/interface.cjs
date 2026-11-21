const {chromium}=require('playwright');
const fs=require('node:fs');const path=require('node:path');
const endereco=process.env.ENDERECO_PRIORIZA||'http://127.0.0.1:8766';
const conferir=(condicao,mensagem)=>{if(!condicao)throw Error(mensagem)};
function entradaData(data){const d=new Date(data);return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-')+'T10:00';}
async function verificar(){
  conferir(['127.0.0.1','localhost','[::1]'].includes(new URL(endereco).hostname),'Utilize somente uma instalacao local de demonstracao.');
  const resposta=await fetch(endereco+'/api/resumo');conferir(resposta.ok,'Portal indisponivel.');const resumo=await resposta.json();
  const composicoes=resumo.composicoes.filter(c=>c.grupo==='SIDER');conferir(composicoes.length>=2,'Inicie com dados de demonstracao.');
  const amanha=new Date(resumo.referencia);amanha.setDate(amanha.getDate()+1);const coleta=entradaData(amanha);
  const local={id:'cliente-demo-interface',nome:'Cliente de Demonstracao',cidade:'São Paulo/SP',latitude:-23.5505,longitude:-46.6333,fonte:'cliente'};
  let reservas=[],configuracao={precoCombustivel:null,consumoMedio:null},ultimaProposta=null;
  const cargas=[1,2].map(n=>({id:'00000000-0000-4000-8000-00000000000'+n,versao:1,situacao:'PENDENTE',alocada:false,carga:{identificacao:'INTERFACE-DEMO-'+n,origem:local,destino:local,coleta,peso:26000,frete:2000,referencia:resumo.referencia,tempoCarga:2,tempoDescarga:2}}));
  const navegador=await chromium.launch({headless:true,...(process.env.NAVEGADOR_EXECUTAVEL?{executablePath:process.env.NAVEGADOR_EXECUTAVEL}:{})});
  try {
    const pagina=await navegador.newPage({viewport:{width:1440,height:1000}});const erros=[];pagina.on('pageerror',e=>erros.push(e.message));
    await pagina.route('https://tile.openstreetmap.org/**',r=>r.abort());
    await pagina.route('**/api/**',async rota=>{
      const requisicao=rota.request();const url=new URL(requisicao.url());const caminho=url.pathname;const metodo=requisicao.method();
      if(caminho==='/api/planejamento'&&metodo==='GET')return rota.fulfill({json:cargas.map(c=>({...c,alocada:reservas.some(r=>r.carga===c.id)}))});
      if(caminho==='/api/reservas')return rota.fulfill({json:reservas});
      if(caminho==='/api/parametros/combustivel'){
        if(metodo==='PUT')configuracao=requisicao.postDataJSON();return rota.fulfill({json:configuracao});
      }
      if(caminho==='/api/simular'){
        const pedido=requisicao.postDataJSON();const alternativa={...composicoes[0],previsaoDisponibilidade:{tipo:'vazio',disponivel:true,disponivelEm:pedido.carga.referencia,local,etapas:[],avisos:[],motivos:[]},avisos:[],motivos:[],distancia:10,horasDeslocamento:10/53,chegada:pedido.carga.referencia,folga:5,disponivelEm:pedido.carga.referencia,nota:90,notas:{distancia:90,prazo:90,faturamento:0,seguranca:100},contribuicoes:{distancia:45,prazo:45},liberacao:coleta,faturamentoDia:2000};
        return rota.fulfill({json:{aprovadas:[alternativa],excluidas:[],pesos:{distancia:.5,prazo:.5},avisos:[],parametros:pedido.parametros,custosCombustivel:{},distanciaCarregada:0}});
      }
      if(caminho==='/api/cenarios'){
        if(metodo==='GET')return rota.fulfill({json:ultimaProposta?[{id:ultimaProposta.id,situacao:ultimaProposta.situacao,resultado:ultimaProposta}]:[]});
        const pedido=requisicao.postDataJSON();
        ultimaProposta={id:'10000000-0000-4000-8000-000000000001',situacao:'SIMULADO',escolhas:pedido.escolhas||{},alternativas:[],comparacao:[{atendidas:pedido.cargas.length,distanciaVazia:20,menorFolga:2}],recomendacoes:pedido.cargas.map((id,i)=>({carga:id,identificacao:cargas.find(c=>c.id===id).carga.identificacao,coleta,placa:composicoes[i].placa,carreta:composicoes[i].carreta,nota:90,distancia:10,folga:2,motivo:'Resultado ficticio de teste.',avaliacao:{disponivelEm:resumo.referencia,chegada:resumo.referencia,contribuicoes:{distancia:45,prazo:45},pesos:{distancia:.5,prazo:.5},avisos:[]}}))};
        return rota.fulfill({json:ultimaProposta});
      }
      if(caminho.endsWith('/confirmar')){reservas=ultimaProposta.recomendacoes.map((r,i)=>({...r,id:'reserva-demo-'+i,cenario:ultimaProposta.id}));ultimaProposta.situacao='CONFIRMADO';return rota.fulfill({json:{situacao:'CONFIRMADO'}});}
      if(caminho==='/api/reservas/cancelar-lote'){const ids=requisicao.postDataJSON().reservas;reservas=reservas.filter(r=>!ids.includes(r.id));return rota.fulfill({json:{canceladas:ids.length}});}
      if(metodo!=='GET'&&caminho!=='/api/disponibilidades')throw Error('Gravacao nao interceptada: '+caminho);
      return rota.continue();
    });
    await pagina.goto(endereco);await pagina.waitForFunction(()=>estado.resumo);
    const menu=async nome=>{await pagina.locator('[data-pagina="'+nome+'"]').click();await pagina.waitForFunction(()=>estado.menuAtualizando===null);};
    await menu('simulacao');await pagina.locator('#carregarExemplo').click();await pagina.waitForFunction(()=>estado.resultado!==null);
    conferir(await pagina.locator('#ranking tr').count()===1,'Ranking nao exibido.');
    await pagina.locator('[data-detalhe="0"]').click();await pagina.locator('#dialogoDetalhes').waitFor({state:'visible'});await pagina.keyboard.press('Escape');
    await menu('cenarios');await pagina.locator('#selecionarTodasCenario').check();await menu('simulacao');
    conferir(await pagina.locator('#identificacao').inputValue()===''&&await pagina.locator('#ranking tr').count()===0,'Retorno ao menu nao limpou simulacao.');
    await menu('cenarios');conferir(await pagina.locator('[name=cargaCenario]:checked').count()===0,'Retorno nao limpou selecao.');
    await pagina.locator('#selecionarTodasCenario').check();await pagina.locator('#modoOperacionalCenario').selectOption('PRAZO');
    await pagina.locator('#simularCenario').click();await pagina.waitForFunction(()=>!estado.cenarioOcupado&&estado.cenarioAtual);
    await pagina.locator('[data-justificativa-cenario]').first().click();await pagina.locator('#dialogoDetalhes').waitFor({state:'visible'});await pagina.keyboard.press('Escape');
    await pagina.locator('#modoOperacionalCenario').selectOption('BALANCEADO');conferir(await pagina.locator('#confirmarCenario').isDisabled(),'Mudanca do modo nao invalidou resultado.');
    await pagina.locator('#recalcularEscolhas').click();await pagina.waitForFunction(()=>!estado.cenarioOcupado&&!estado.cenarioDesatualizado);
    await pagina.locator('#confirmarCenario').click();await pagina.waitForFunction(()=>!estado.cenarioOcupado&&estado.cenarioAtual===null);
    await menu('planejamento');await pagina.locator('#selecionarReservasVisiveis').check();await pagina.locator('#cancelarReservasLote').click();await pagina.locator('#confirmarCancelamentoLote').click();await pagina.waitForFunction(()=>!document.querySelector('#dialogoCancelarReservasLote').open);
    conferir(reservas.length===0,'Cancelamento ficticio falhou.');
    await menu('metodo');await pagina.locator('#precoCombustivel').fill('6.25');await pagina.locator('#consumoMedio').fill('2.5');await pagina.locator('#formularioParametros [type=submit]').click();await pagina.waitForFunction(()=>!document.querySelector('#formularioParametros [type=submit]').disabled);
    await pagina.reload();await pagina.waitForFunction(()=>estado.resumo);conferir(await pagina.locator('#precoCombustivel').inputValue()==='6.25','Parametro ficticio nao restaurado.');
    for(const width of [1280,1440,1920]){await pagina.setViewportSize({width,height:1000});await menu('cenarios');conferir(await pagina.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Tela transbordando.');}
    const pasta=path.join(__dirname,'..','test-results');fs.mkdirSync(pasta,{recursive:true});await pagina.screenshot({path:path.join(pasta,'interface-demonstracao.png')});
    conferir(erros.length===0,erros.join('; '));console.log('Interface validada: ranking, justificativas, menus, modos, reservas, cancelamento em lote, parametros e layout. Gravacoes interceptadas.');
  } finally { await navegador.close(); }
}
verificar().catch(erro=>{console.error(erro.message);process.exit(1)});
