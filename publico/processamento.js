const processamentoImportacao={ativo:false,inicio:0,intervalo:null,arquivos:[],indice:0,inicioArquivo:0};
const chaveHistoricoImportacao='prioriza.temposImportacao.v1';
function lerHistoricoImportacao(){
  try{const dados=JSON.parse(localStorage.getItem(chaveHistoricoImportacao)||'[]');return Array.isArray(dados)?dados.filter(item=>typeof item.tipo==='string'&&Number.isFinite(item.tamanho)&&item.tamanho>0&&Number.isFinite(item.duracao)&&item.duracao>0).slice(-40):[];}catch{return [];}
}
function tipoArquivoImportacao(arquivo){return arquivo.name.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toUpperCase().replace(/[^A-Z0-9]/g,'');}
function estimarArquivoImportacao(arquivo){
  const amostras=lerHistoricoImportacao().filter(item=>item.tipo===tipoArquivoImportacao(arquivo)&&item.tamanho>=arquivo.size/2&&item.tamanho<=arquivo.size*2).slice(-8);
  if(amostras.length<2)return null;
  const duracoes=amostras.map(item=>item.duracao*arquivo.size/item.tamanho).sort((a,b)=>a-b);const meio=Math.floor(duracoes.length/2);
  return duracoes.length%2?duracoes[meio]:(duracoes[meio-1]+duracoes[meio])/2;
}
function formatarTempoProcessamento(ms){
  const segundos=Math.max(0,Math.floor(ms/1000));const minutos=Math.floor(segundos/60);return `${String(minutos).padStart(2,'0')}:${String(segundos%60).padStart(2,'0')}`;
}
function atualizarRelogioImportacao(){
  if(!processamentoImportacao.ativo)return;
  const agora=performance.now();document.querySelector('#decorridoProcessamento').textContent=formatarTempoProcessamento(agora-processamentoImportacao.inicio);
  const pendentes=processamentoImportacao.arquivos.slice(processamentoImportacao.indice);const estimativas=pendentes.map(estimarArquivoImportacao);
  let restante='Sem estimativa';
  if(estimativas.length&&estimativas.every(valor=>valor!=null)){
    const ms=estimativas.reduce((soma,valor)=>soma+valor,0)-(agora-processamentoImportacao.inicioArquivo);
    restante=ms>0?'~ '+formatarTempoProcessamento(Math.max(1000,ms)):'Acima da estimativa';
  }
  document.querySelector('#restanteProcessamento').textContent=restante;
}
function iniciarProcessamentoImportacao(arquivos){
  if(processamentoImportacao.ativo)return;
  Object.assign(processamentoImportacao,{ativo:true,inicio:performance.now(),arquivos:Array.from(arquivos),indice:0,inicioArquivo:performance.now()});
  document.querySelector('#decorridoProcessamento').textContent='00:00';document.querySelector('#etapaProcessamento').textContent='Preparando importação';
  document.querySelector('#detalheProcessamento').textContent='Tempo restante estimado a partir de importações anteriores de mesmo layout e tamanho semelhante.';
  document.querySelector('#arquivoProcessamento').textContent='';document.querySelector('#dialogoProcessamento').showModal();
  document.querySelector('.area-principal').setAttribute('aria-busy','true');atualizarIcones();atualizarRelogioImportacao();
  processamentoImportacao.intervalo=setInterval(atualizarRelogioImportacao,500);window.addEventListener('beforeunload',avisarSaidaDuranteImportacao);
}
function atualizarEtapaImportacao(etapa,indice){
  if(!processamentoImportacao.ativo)return;
  if(indice!=null){processamentoImportacao.indice=indice;processamentoImportacao.inicioArquivo=performance.now();}
  const arquivo=processamentoImportacao.arquivos[processamentoImportacao.indice];
  document.querySelector('#arquivoProcessamento').textContent=`Arquivo ${processamentoImportacao.indice+1} de ${processamentoImportacao.arquivos.length} / ${arquivo.name}`;
  document.querySelector('#etapaProcessamento').textContent=etapa;
  const estimativa=estimarArquivoImportacao(arquivo);
  document.querySelector('#detalheProcessamento').textContent=estimativa==null?'Ainda sem histórico suficiente para estimar o tempo restante.':'Estimativa aproximada; a duração pode variar conforme o arquivo e o processamento no servidor.';
  atualizarRelogioImportacao();
}
function registrarTempoImportacao(arquivo){
  const amostras=lerHistoricoImportacao();amostras.push({tipo:tipoArquivoImportacao(arquivo),tamanho:arquivo.size,duracao:performance.now()-processamentoImportacao.inicioArquivo});
  try{localStorage.setItem(chaveHistoricoImportacao,JSON.stringify(amostras.slice(-40)));}catch{}
}
function encerrarProcessamentoImportacao(){
  processamentoImportacao.ativo=false;clearInterval(processamentoImportacao.intervalo);processamentoImportacao.intervalo=null;
  document.querySelector('#dialogoProcessamento').close();document.querySelector('.area-principal').removeAttribute('aria-busy');window.removeEventListener('beforeunload',avisarSaidaDuranteImportacao);
}
function avisarSaidaDuranteImportacao(evento){evento.preventDefault();evento.returnValue='';}
document.querySelector('#dialogoProcessamento').addEventListener('cancel',evento=>{if(processamentoImportacao.ativo)evento.preventDefault();});
