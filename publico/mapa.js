const mapaOperacional={mapa:null,camada:null,pontos:[],marcadores:new Map(),selecionada:null,assinatura:null,quadro:null,carregadas:0,falhas:0,temporizador:null};
function coordenadaValidaMapa(local){return local&&Number.isFinite(local.latitude)&&Number.isFinite(local.longitude)&&Math.abs(local.latitude)<=90&&Math.abs(local.longitude)<=180&&(local.latitude!==0||local.longitude!==0);}
function avisarSituacaoMapa(texto,falha=false){const elemento=document.querySelector('#situacaoMapa');elemento.textContent=texto;elemento.classList.toggle('falha-mapa',falha);}
function iniciarMapaOperacional(){
 const elemento=document.querySelector('#mapaPosicoes');
 if(!window.L){elemento.classList.add('mapa-indisponivel');elemento.textContent='Mapa indisponível. A simulação continua disponível.';avisarSituacaoMapa('Não foi possível carregar a biblioteca do mapa.',true);return false;}
 if(mapaOperacional.mapa)return true;
 const mapa=L.map(elemento,{zoomControl:false,scrollWheelZoom:false,minZoom:2,maxZoom:19}).setView([-15.8,-47.9],4);
 mapaOperacional.mapa=mapa;mapaOperacional.camada=L.layerGroup().addTo(mapa);
 L.control.zoom({zoomInTitle:'Aproximar',zoomOutTitle:'Afastar'}).addTo(mapa);
 const url=document.querySelector('meta[name="mapa-base-url"]')?.content||'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
 const base=L.tileLayer(url,{maxZoom:19,keepBuffer:1,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors'});
 base.on('loading',()=>{mapaOperacional.carregadas=0;mapaOperacional.falhas=0;avisarSituacaoMapa('Carregando mapa de fundo...');clearTimeout(mapaOperacional.temporizador);mapaOperacional.temporizador=setTimeout(()=>avisarSituacaoMapa('O mapa está demorando a responder. Os pontos e a simulação continuam disponíveis.',true),12000);});
 base.on('tileload',()=>{mapaOperacional.carregadas++;});
 base.on('tileerror',()=>{mapaOperacional.falhas++;avisarSituacaoMapa('Parte do mapa não carregou. Verifique a conexão; a simulação continua disponível.',true);});
 base.on('load',()=>{clearTimeout(mapaOperacional.temporizador);avisarSituacaoMapa(mapaOperacional.falhas?(mapaOperacional.carregadas?'Parte do mapa não carregou. Os pontos continuam disponíveis.':'Mapa de fundo indisponível. Verifique a conexão; a simulação continua disponível.'):'Mapa carregado. Fonte: OpenStreetMap.',mapaOperacional.falhas>0);});
 base.addTo(mapa);return true;
}
function previsaoParaMapa(composicao,aprovadas){return aprovadas.get(composicao.placa)?.previsaoDisponibilidade||estado.previsoes[composicao.placa];}
function conteudoVeiculoMapa(composicao,previsao,viavel){
 const e=escaparHtml;const atual=coordenadaValidaMapa(composicao)?`${composicao.latitude.toFixed(5)}, ${composicao.longitude.toFixed(5)}`:'Sem coordenadas válidas';
 let html=`<div class="veiculo-popup"><strong>${e(composicao.placa)} + ${e(composicao.carreta||'Sem carreta')}</strong><span>${e(composicao.situacao)} / ${e(composicao.cidade||'Município não informado')}</span><span>Posição atual: ${e(atual)}</span><span>Última posição: ${e(formatarData(composicao.ultimaPosicao))}</span>`;
 if(viavel)html+='<span>Viável na avaliação atual da carga.</span>';
 if(previsao?.disponivel&&coordenadaValidaMapa(previsao.local)){
  html+=`<span>Disponibilidade ${previsao.tipo==='manual'?'manual':'prevista'}: ${e(formatarData(previsao.disponivelEm))}</span><span>Local de liberação: ${e(previsao.local.nome)} / ${e(previsao.local.cidade)}</span>`;
  if(previsao.local.fonte!=='cliente'&&previsao.local.fonte!=='posicao')html+='<span class="aviso-local-mapa">Coordenada de liberação aproximada ou manual.</span>';
 }else html+='<span>Previsão de liberação georreferenciada indisponível.</span>';
 if(previsao?.avisos?.length)html+=`<span class="aviso-local-mapa">${previsao.avisos.map(e).join(' ')}</span>`;
 return html+'</div>';
}
function iconeLocalMapa(tipo){
 return L.divIcon({className:'icone-local-mapa',html:`<span class="ponto-${tipo}-mapa"><i data-lucide="${tipo==='coleta'?'map-pin':'clock-3'}"></i></span>`,iconSize:[28,28],iconAnchor:[14,14],popupAnchor:[0,-12]});
}
function enquadrarPontosMapa(pontos=mapaOperacional.pontos){
 if(!mapaOperacional.mapa||!pontos.length)return;
 mapaOperacional.mapa.invalidateSize({pan:false});mapaOperacional.mapa.fitBounds(L.latLngBounds(pontos),{padding:[35,35],maxZoom:13,animate:false});
}
function atualizarMapaPosicoes(){
 if(mapaOperacional.quadro)cancelAnimationFrame(mapaOperacional.quadro);
 mapaOperacional.quadro=requestAnimationFrame(()=>{mapaOperacional.quadro=null;renderizarMapaPosicoes();});
}
function renderizarMapaPosicoes(){
 const elemento=document.querySelector('#mapaPosicoes');
 if(!estado.resumo||!elemento.clientWidth||!elemento.clientHeight)return;
 if(!iniciarMapaOperacional())return;
 const mapa=mapaOperacional.mapa;mapa.invalidateSize({pan:false});
 const resultado=estado.resultadosObsoletos?null:estado.resultado;
 const aprovadas=new Map((resultado?.aprovadas||[]).map(item=>[item.placa,item]));
 const filtro=document.querySelector('#viaveisMapa');filtro.disabled=!resultado;if(!resultado)filtro.checked=false;
 const modo=document.querySelector('#modoMapa').value;
 document.querySelector('#rotuloPosicoesMapa').textContent=modo==='atual'?'Posição importada':'Disponibilidade prevista';
 const composicoes=estado.resumo.composicoes.filter(c=>c.grupo==='SIDER'&&(!filtro.checked||aprovadas.has(c.placa)));
 const grupos=new Map();const pontos=[];let validas=0;
 mapaOperacional.camada.clearLayers();mapaOperacional.marcadores.clear();
 for(const composicao of composicoes){
  const previsao=previsaoParaMapa(composicao,aprovadas);
  const ponto=modo==='atual'?composicao:(previsao?.disponivel?previsao.local:null);
  if(!coordenadaValidaMapa(ponto))continue;
  validas++;const chave=`${ponto.latitude}/${ponto.longitude}`;
  if(!grupos.has(chave))grupos.set(chave,{ponto,itens:[]});
  grupos.get(chave).itens.push({composicao,previsao});
 }
 for(const {ponto,itens}of grupos.values()){
  const selecionado=itens.some(i=>i.composicao.placa===mapaOperacional.selecionada);
  const viavel=itens.some(i=>aprovadas.has(i.composicao.placa));
  const marcador=L.circleMarker([ponto.latitude,ponto.longitude],{radius:selecionado?10:6+Math.min(4,itens.length-1),color:selecionado?'#006c7e':'#172a38',weight:selecionado?3:1.5,fillColor:viavel?'#1dc878':'#52677b',fillOpacity:.95,dashArray:modo==='liberacao'?'3 2':null,bubblingMouseEvents:false}).addTo(mapaOperacional.camada);
  const titulo=itens.length===1?itens[0].composicao.placa:`${itens.length} veículos no mesmo ponto`;
  marcador.bindTooltip(escaparHtml(titulo)).bindPopup(itens.map(i=>conteudoVeiculoMapa(i.composicao,i.previsao,aprovadas.has(i.composicao.placa))).join(''),{maxWidth:340,minWidth:240});
  marcador.on('click',()=>localizarPlacaNoMapa(itens[0].composicao.placa));
  const caminho=marcador.getElement();
  if(caminho){caminho.setAttribute('tabindex','0');caminho.setAttribute('role','button');caminho.setAttribute('aria-label',titulo);caminho.addEventListener('keydown',evento=>{if(evento.key==='Enter'||evento.key===' '){evento.preventDefault();evento.stopPropagation();localizarPlacaNoMapa(itens[0].composicao.placa);}});}
  for(const item of itens)mapaOperacional.marcadores.set(item.composicao.placa,marcador);
  pontos.push([ponto.latitude,ponto.longitude]);
 }
 const origem=estado.locais.origem||resultado?.carga.origem;
 if(coordenadaValidaMapa(origem)){
  const aproximada=origem.fonte!=='cliente';
  L.marker([origem.latitude,origem.longitude],{icon:iconeLocalMapa('coleta'),title:'Local da coleta',alt:'Local da coleta',zIndexOffset:1000}).addTo(mapaOperacional.camada)
   .bindPopup(`<strong>Coleta: ${escaparHtml(origem.nome)}</strong><br>${escaparHtml(origem.cidade||'')}${aproximada?'<br>Coordenada aproximada ou manual.':''}`);
  pontos.push([origem.latitude,origem.longitude]);
 }
 const selecionada=composicoes.find(c=>c.placa===mapaOperacional.selecionada);
 if(selecionada){
  const previsao=previsaoParaMapa(selecionada,aprovadas);
  if(modo==='atual'&&previsao?.disponivel&&coordenadaValidaMapa(previsao.local)){
   L.marker([previsao.local.latitude,previsao.local.longitude],{icon:iconeLocalMapa('liberacao'),title:'Liberação prevista de '+selecionada.placa,alt:'Local previsto de liberação',zIndexOffset:500}).addTo(mapaOperacional.camada)
    .bindPopup(`<strong>Liberação ${previsao.tipo==='manual'?'manual':'prevista'}: ${escaparHtml(selecionada.placa)}</strong><br>${escaparHtml(previsao.local.nome)}<br>${escaparHtml(formatarData(previsao.disponivelEm))}<br>${previsao.local.fonte==='cliente'?'Coordenada do cliente.':'Coordenada da posição, aproximada ou manual.'}`);
  }
  if(aprovadas.has(selecionada.placa)&&previsao?.disponivel&&coordenadaValidaMapa(previsao.local)&&coordenadaValidaMapa(origem))
   L.polyline([[previsao.local.latitude,previsao.local.longitude],[origem.latitude,origem.longitude]],{color:'#006e82',weight:3,dashArray:'6 7',interactive:false}).addTo(mapaOperacional.camada);
 }
 mapaOperacional.pontos=pontos;
 const semLocal=composicoes.length-validas;
 document.querySelector('#resumoMapa').textContent=`${modo==='atual'?'Posições importadas':'Disponibilidades previstas'}: ${validas} de ${composicoes.length} composições${semLocal?` / ${semLocal} sem ${modo==='atual'?'coordenadas válidas':'liberação georreferenciada'}`:''}.`;
 const assinatura=JSON.stringify({modo,filtro:filtro.checked,pontos,viaveis:[...aprovadas.keys()]});
 if(assinatura!==mapaOperacional.assinatura){mapaOperacional.assinatura=assinatura;enquadrarPontosMapa();}
 atualizarIcones();
}
function localizarPlacaNoMapa(placa){
 mapaOperacional.selecionada=placa;renderizarMapaPosicoes();
 const composicao=estado.resumo?.composicoes.find(c=>c.placa===placa);if(!composicao)return;
 const previsao=estado.resultado?.aprovadas.find(a=>a.placa===placa)?.previsaoDisponibilidade||estado.previsoes[placa];
 const origem=estado.locais.origem||estado.resultado?.carga.origem;
 const pontos=[composicao,previsao?.disponivel?previsao.local:null,origem].filter(coordenadaValidaMapa).map(p=>[p.latitude,p.longitude]);
 enquadrarPontosMapa(pontos);mapaOperacional.marcadores.get(placa)?.openPopup();
 document.querySelector('#mapaPosicoes').scrollIntoView({behavior:'smooth',block:'center'});
}
document.querySelector('#modoMapa').addEventListener('change',atualizarMapaPosicoes);
document.querySelector('#viaveisMapa').addEventListener('change',atualizarMapaPosicoes);
document.querySelector('#enquadrarMapa').addEventListener('click',()=>enquadrarPontosMapa());
document.addEventListener('click',evento=>{const botao=evento.target.closest('[data-localizar-placa]');if(botao)localizarPlacaNoMapa(botao.dataset.localizarPlaca);});
