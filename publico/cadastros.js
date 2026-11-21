const cadastro = {tipo:'clientes',pagina:0,itens:[],edicao:null,revisao:null,consulta:0};
const camposCliente = {cnpj:'CNPJ',razaoSocial:'Razão social',nomeFantasia:'Nome fantasia',municipio:'Município',uf:'UF',endereco:'Endereço / identificação do local',latitude:'Latitude',longitude:'Longitude'};
const camposVeiculo = {placa:'Placa',tipo:'Tipo',frota:'Frota',agrupamento:'Agrupamento',classificacao:'Classificação',pbt:'PBT (kg)',tara:'Tara (kg)',eixos:'Eixos'};
async function abrirCadastro(tipo) {
  cadastro.tipo=tipo;cadastro.pagina=0;selecionar('#buscaCadastro').value='';selecionar('#situacaoCadastro').value='ATIVOS';selecionar('#revisaoCadastro').checked=false;
  selecionar('#tituloCadastro').textContent=tipo==='clientes'?'Clientes e locais':'Veículos';
  selecionar('#buscaCadastro').placeholder=tipo==='clientes'?'Nome, CNPJ, município ou endereço':'Placa, frota ou classificação';
  trocarPagina('cadastros');selecionar('[data-pagina="bases"]').classList.add('ativa');selecionar('#nomePagina').textContent=tipo==='clientes'?'Clientes e locais':'Veículos';await carregarCadastro();
}
async function carregarCadastro() {
  const consulta=++cadastro.consulta;
  const parametros=new URLSearchParams({busca:selecionar('#buscaCadastro').value,situacao:selecionar('#situacaoCadastro').value,revisao:selecionar('#revisaoCadastro').checked,pagina:cadastro.pagina});
  selecionar('#resumoCadastro').textContent='Carregando...';
  try {
    const resultado=await consultarApi(`/api/cadastros/${cadastro.tipo}?${parametros}`);if(consulta!==cadastro.consulta)return;
    cadastro.itens=resultado.itens;
    selecionar('#resumoCadastro').textContent=`${resultado.total} registro(s) encontrado(s)`;
    const cliente=cadastro.tipo==='clientes';
    selecionar('#cabecalhoTabelaCadastro').innerHTML=cliente?'<tr><th>Cliente / CNPJ</th><th>Município / local</th><th>Coordenadas</th><th>Situação / origem</th><th>Ações</th></tr>':'<tr><th>Placa / tipo</th><th>Equipamento / frota</th><th>PBT / tara</th><th>Capacidade útil / eixos</th><th>Situação / origem</th><th>Ações</th></tr>';
    selecionar('#linhasCadastro').innerHTML=resultado.itens.map((item,indice)=>{
      const d=item.dados;
      const situacao=`${item.ativo?'Ativo':'Inativo'}<small>${item.manual?'Cadastro pela tela':'Importado'}</small>${item.proposta?'<span class="etiqueta">Revisão pendente</span>':''}`;
      const acoes=`<div class="acoes-linha"><button class="botao-icone" data-editar-cadastro="${indice}" title="Editar cadastro" aria-label="Editar cadastro"><i data-lucide="pencil"></i></button>${item.proposta?`<button class="botao-icone" data-revisar-cadastro="${indice}" title="Revisar importação" aria-label="Revisar importação"><i data-lucide="git-compare-arrows"></i></button>`:''}<button class="botao-icone" data-situacao-cadastro="${indice}" title="${item.ativo?'Inativar':'Reativar'} cadastro" aria-label="${item.ativo?'Inativar':'Reativar'} cadastro"><i data-lucide="${item.ativo?'circle-pause':'circle-play'}"></i></button></div>`;
      const historico=cliente?`<button class="botao-icone" data-atrasos-cliente="${indice}" title="Histórico de atrasos" aria-label="Histórico de atrasos de ${escaparHtml(d.nomeFantasia||d.razaoSocial)}"><i data-lucide="history"></i></button>`:'';
      return cliente?`<tr><td>${escaparHtml(d.nomeFantasia||d.razaoSocial)}<small>${escaparHtml(d.razaoSocial)}</small><small>${escaparHtml(d.cnpj||'CNPJ não informado')}</small></td><td>${escaparHtml(d.municipio)}/${escaparHtml(d.uf)}<small>${escaparHtml(d.endereco||'Sem endereço cadastrado')}</small></td><td>${d.latitude==null||d.longitude==null?'Ausentes':`${d.latitude.toFixed(6)}, ${d.longitude.toFixed(6)}`}<small>${item.fonte==='cliente'?'Ponto do cliente':item.fonte==='municipio'?'Referência municipal':'Sem coordenadas'}</small></td><td>${situacao}</td><td><div class="acoes-linha">${historico}${acoes}</div></td></tr>`:
      `<tr><td><strong>${escaparHtml(d.placa)}</strong><small>${escaparHtml(d.tipo||'Tipo não informado')}</small></td><td>${escaparHtml(d.classificacao||'--')}<small>${escaparHtml(d.frota||'--')} / ${escaparHtml(d.agrupamento||'--')}</small></td><td>${d.pbt==null?'--':formatoNumero.format(d.pbt)} kg<small>${d.tara==null?'--':formatoNumero.format(d.tara)} kg de tara</small></td><td>${d.pbt==null||d.tara==null?'--':formatoNumero.format(d.pbt-d.tara)+' kg'}<small>${d.eixos??'--'} eixos</small></td><td>${situacao}</td><td>${acoes}</td></tr>`;
    }).join('')||`<tr><td colspan="${cliente?5:6}">Nenhum registro encontrado.</td></tr>`;
    selecionar('#paginaCadastro').textContent=`Página ${resultado.pagina+1} de ${Math.max(1,Math.ceil(resultado.total/resultado.tamanho))}`;
    selecionar('#anteriorCadastro').disabled=cadastro.pagina===0;selecionar('#proximoCadastro').disabled=(cadastro.pagina+1)*resultado.tamanho>=resultado.total;atualizarIcones();
  }catch(erro){if(consulta===cadastro.consulta){selecionar('#resumoCadastro').textContent='Não foi possível carregar.';mostrarMensagem(erro.message);}}
}
function formularioCadastro(item=null) {
  cadastro.edicao=item;const cliente=cadastro.tipo==='clientes';const dados=item?.dados||{};
  selecionar('#tituloFormularioCadastro').textContent=`${item?'Editar':'Incluir'} ${cliente?'cliente / local':'veículo'}`;
  const campo=(nome,tipo='text',atributos='')=>`<label>${(cliente?camposCliente:camposVeiculo)[nome]}<input name="${nome}" type="${tipo}" value="${escaparHtml(dados[nome]??'')}" ${atributos}></label>`;
  if(cliente){
    const ufs=['AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'];
    selecionar('#camposCadastro').innerHTML=campo('cnpj','text','maxlength="18"')+campo('razaoSocial','text','maxlength="200"')+campo('nomeFantasia','text','maxlength="200"')+`<div class="duas-colunas">${campo('municipio','text','required maxlength="120"')}<label>UF<select name="uf" required><option value="">Selecione</option>${ufs.map(uf=>`<option ${dados.uf===uf?'selected':''}>${uf}</option>`).join('')}</select></label></div>`+campo('endereco','text','required maxlength="1000"')+`<div class="duas-colunas">${campo('latitude','number','required min="-90" max="90" step="any"')}${campo('longitude','number','required min="-180" max="180" step="any"')}</div>`;
  }else{
    selecionar('#camposCadastro').innerHTML=`<div class="duas-colunas">${campo('placa','text',`required maxlength="8" ${item?'readonly':''}`)}<label>Tipo<select name="tipo" required><option value="">Selecione</option><option value="CAVALO" ${dados.tipo==='CAVALO'?'selected':''}>Cavalo</option><option value="CARRETA" ${dados.tipo==='CARRETA'?'selected':''}>Carreta</option></select></label></div>`+campo('classificacao','text','required maxlength="100" list="classificacoesCadastro"')+'<datalist id="classificacoesCadastro"><option value="SIDER"><option value="CAVALO MECANICO"><option value="BAU"><option value="GRANELEIRO"><option value="TANQUE"></datalist>'+`<div class="duas-colunas">${campo('frota','text','maxlength="100"')}${campo('agrupamento','text','maxlength="100"')}</div><div class="duas-colunas">${campo('pbt','number','required min="0.01" max="200000" step="0.01"')}${campo('tara','number','required min="0" max="200000" step="0.01"')}</div>`+campo('eixos','number','required min="1" max="12" step="1"');
  }
  selecionar('#erroCadastro').hidden=true;selecionar('#dialogoCadastro').showModal();atualizarIcones();selecionar('#camposCadastro input').focus();
}
async function aposAlterarCadastro(item) {
  if(cadastro.tipo==='clientes'){
    const d=item.dados;const local={id:item.id,nome:d.nomeFantasia||d.razaoSocial,cidade:`${d.municipio}/${d.uf}`,latitude:d.latitude,longitude:d.longitude,fonte:item.fonte,detalhe:d.endereco};
    for(const campo of ['origem','destino','liberacao'])if(estado.locais[campo]?.id===item.id)selecionarLocal(campo,item.ativo?local:null);
    for(const [placa,ajuste] of Object.entries(estado.ajustes))if(ajuste.local?.id===item.id){if(item.ativo)ajuste.local=local;else delete estado.ajustes[placa];}
  }
  invalidarResultado();atualizarResumo(await consultarApi('/api/resumo'));await carregarCadastro();
}
selecionar('#formularioCadastro').addEventListener('submit',async evento=>{
  evento.preventDefault();const botao=evento.target.querySelector('[type="submit"]');botao.disabled=true;selecionar('#erroCadastro').hidden=true;
  try{
    const dados=Object.fromEntries(new FormData(evento.target));for(const nome of ['latitude','longitude','pbt','tara','eixos'])if(nome in dados)dados[nome]=dados[nome]===''?null:Number(dados[nome]);
    const item=cadastro.edicao;const id=item?(cadastro.tipo==='clientes'?item.id:item.dados.placa):null;
    const salvo=await consultarApi(`/api/cadastros/${cadastro.tipo}${id?'/'+encodeURIComponent(id):''}`,{dados,versao:item?.versao},id?'PUT':'POST');
    await aposAlterarCadastro(salvo);selecionar('#dialogoCadastro').close();mostrarMensagem('Cadastro salvo.',true);
  }catch(erro){selecionar('#erroCadastro').textContent=erro.message;selecionar('#erroCadastro').hidden=false;}finally{botao.disabled=false;}
});
async function abrirHistoricoAtrasos(item) {
  if (!item || cadastro.gravandoAtraso) return;
  cadastro.clienteAtrasos = item.id;
  selecionar('#formularioAtrasosCliente').reset();
  selecionar('#resumoAtrasosCliente').innerHTML = '';
  selecionar('#registrosAtrasosCliente').innerHTML = '';
  selecionar('#localAtrasosCliente').textContent = [item.dados.nomeFantasia || item.dados.razaoSocial,
    item.dados.municipio + '/' + item.dados.uf, item.dados.endereco].filter(Boolean).join(' / ');
  selecionar('#dialogoAtrasosCliente').showModal();
  await carregarAtrasosCliente();
}

async function carregarAtrasosCliente() {
  const id = cadastro.clienteAtrasos;
  const consulta = (cadastro.consultaAtrasos || 0) + 1;
  cadastro.consultaAtrasos = consulta;
  selecionar('#erroAtrasosCliente').hidden = true;
  selecionar('#situacaoAtrasosCliente').textContent = 'Carregando histórico...';
  selecionar('#formularioAtrasosCliente').inert = true;
  selecionar('#atualizarAtrasosCliente').disabled = true;
  try {
    const historico = await consultarApi('/api/cadastros/clientes/' + encodeURIComponent(id) + '/atrasos');
    if (consulta !== cadastro.consultaAtrasos || cadastro.clienteAtrasos !== id) return;
    selecionar('#formularioAtrasosCliente').hidden = !historico.cliente.ativo;
    const total = historico.resumo.reduce((soma, item) => soma + item.registros, 0);
    selecionar('#situacaoAtrasosCliente').textContent = total ? total + (total === 1 ? ' observação registrada' : ' observações registradas') + (total > 100 ? ' / últimas 100 exibidas' : '') : 'Nenhuma ocorrência registrada.';
    if (!historico.cliente.ativo) selecionar('#situacaoAtrasosCliente').textContent += ' Cliente inativo.';
    const dataHistorica = valor => new Date(valor).toLocaleString('pt-BR', {day:'2-digit', month:'2-digit', year:'numeric', hour:'2-digit', minute:'2-digit'});
    selecionar('#resumoAtrasosCliente').innerHTML = historico.resumo.length ? '<table><thead><tr><th>Etapa</th><th>Observações</th><th>Com atraso</th><th>No prazo ou antecipadas</th><th title="Média do atraso por observação; pontuais e antecipadas entram com zero.">Atraso médio (min)</th><th title="Observações com atraso divididas pelo total da etapa.">Frequência</th></tr></thead><tbody>'
      + historico.resumo.map(item => `<tr><td>${item.etapa === 'CARGA' ? 'Carga' : 'Descarga'}</td><td>${item.registros}</td><td>${item.atrasos}</td><td>${item.registros - item.atrasos}</td><td>${formatoNumero.format(item.mediaMinutos)}</td><td>${formatoNumero.format(item.percentual)}%</td></tr>`).join('') + '</tbody></table>' : '';
    selecionar('#registrosAtrasosCliente').innerHTML = historico.registros.length ? '<table><thead><tr><th>Etapa</th><th>Início previsto</th><th>Início executado</th><th>Resultado</th><th>Referência</th></tr></thead><tbody>'
      + historico.registros.map(item => `<tr><td>${item.etapa === 'CARGA' ? 'Carga' : 'Descarga'}</td><td>${dataHistorica(item.previsto)}</td><td>${dataHistorica(item.executado)}</td><td>${item.diferencaMinutos > 0 ? formatoNumero.format(item.diferencaMinutos) + ' min de atraso' : item.diferencaMinutos < 0 ? formatoNumero.format(-item.diferencaMinutos) + ' min de antecedência' : 'No horário'}</td><td>${escaparHtml(item.observacao || '--')}</td></tr>`).join('') + '</tbody></table>' : '';
  } catch (erro) {
    if (consulta === cadastro.consultaAtrasos) {
      selecionar('#erroAtrasosCliente').textContent = erro.message;
      selecionar('#erroAtrasosCliente').hidden = false;
      selecionar('#situacaoAtrasosCliente').textContent = 'Não foi possível atualizar o histórico.';
    }
  } finally {
    if (consulta === cadastro.consultaAtrasos) {
      selecionar('#formularioAtrasosCliente').inert = false;
      selecionar('#atualizarAtrasosCliente').disabled = false;
    }
  }
}

selecionar('#atualizarAtrasosCliente').addEventListener('click', carregarAtrasosCliente);
selecionar('#dialogoAtrasosCliente').addEventListener('close', () => { cadastro.consultaAtrasos = (cadastro.consultaAtrasos || 0) + 1; });
selecionar('#formularioAtrasosCliente').addEventListener('submit', async evento => {
  evento.preventDefault();
  if (cadastro.gravandoAtraso) return;
  cadastro.gravandoAtraso = true;
  const dialogo = selecionar('#dialogoAtrasosCliente');
  selecionar('#registrarAtrasosCliente').disabled = true;
  selecionar('#atualizarAtrasosCliente').disabled = true;
  selecionar('#erroAtrasosCliente').hidden = true;
  selecionarTodos('#dialogoAtrasosCliente [data-fechar]').forEach(botao => botao.disabled = true);
  dialogo.oncancel = evento => evento.preventDefault();
  try {
    await consultarApi('/api/cadastros/clientes/' + encodeURIComponent(cadastro.clienteAtrasos) + '/atrasos', {
      etapa: selecionar('#etapaAtrasosCliente').value, previsto: selecionar('#previstoAtrasosCliente').value,
      executado: selecionar('#executadoAtrasosCliente').value, observacao: selecionar('#observacaoAtrasosCliente').value
    });
    evento.target.reset();
    await carregarAtrasosCliente();
  } catch (erro) {
    selecionar('#erroAtrasosCliente').textContent = erro.message;
    selecionar('#erroAtrasosCliente').hidden = false;
  } finally {
    cadastro.gravandoAtraso = false; dialogo.oncancel = null;
    selecionar('#registrarAtrasosCliente').disabled = false;
    selecionar('#atualizarAtrasosCliente').disabled = false;
    selecionarTodos('#dialogoAtrasosCliente [data-fechar]').forEach(botao => botao.disabled = false);
  }
});

function abrirRevisaoCadastro(item){
  cadastro.revisao=item;const campos=cadastro.tipo==='clientes'?camposCliente:camposVeiculo;
  selecionar('#comparacaoCadastro').innerHTML=`<table><thead><tr><th>Campo</th><th>Cadastro atual</th><th>Planilha</th></tr></thead><tbody>${Object.entries(campos).map(([chave,nome])=>`<tr><td>${nome}</td><td>${escaparHtml(String(item.dados[chave]??'--'))}</td><td class="${item.dados[chave]!==item.proposta[chave]?'valor-divergente':''}">${escaparHtml(String(item.proposta[chave]??'--'))}</td></tr>`).join('')}</tbody></table>`;
  selecionar('#erroRevisaoCadastro').hidden=true;selecionar('#dialogoRevisaoCadastro').showModal();atualizarIcones();
}
async function revisarCadastro(aceitar){
  const item=cadastro.revisao;const id=cadastro.tipo==='clientes'?item.id:item.dados.placa;
  selecionar('#manterCadastro').disabled=true;selecionar('#aplicarImportacaoCadastro').disabled=true;
  try{const salvo=await consultarApi(`/api/cadastros/${cadastro.tipo}/${encodeURIComponent(id)}/revisar`,{versao:item.versao,aceitar});await aposAlterarCadastro(salvo);selecionar('#dialogoRevisaoCadastro').close();mostrarMensagem(aceitar?'Dados importados aplicados.':'Cadastro atual mantido.',true);}
  catch(erro){selecionar('#erroRevisaoCadastro').textContent=erro.message;selecionar('#erroRevisaoCadastro').hidden=false;}
  finally{selecionar('#manterCadastro').disabled=false;selecionar('#aplicarImportacaoCadastro').disabled=false;}
}
document.addEventListener('click',async evento=>{
  const atrasos=evento.target.closest('[data-atrasos-cliente]');
  if(atrasos&&cadastro.tipo==='clientes'){await abrirHistoricoAtrasos(cadastro.itens[Number(atrasos.dataset.atrasosCliente)]);return;}
  const abrir=evento.target.closest('[data-cadastro]');if(abrir){await abrirCadastro(abrir.dataset.cadastro);return;}
  const editar=evento.target.closest('[data-editar-cadastro]');if(editar)formularioCadastro(cadastro.itens[Number(editar.dataset.editarCadastro)]);
  const revisar=evento.target.closest('[data-revisar-cadastro]');if(revisar)abrirRevisaoCadastro(cadastro.itens[Number(revisar.dataset.revisarCadastro)]);
  const situacao=evento.target.closest('[data-situacao-cadastro]');if(situacao){
    const item=cadastro.itens[Number(situacao.dataset.situacaoCadastro)];const id=cadastro.tipo==='clientes'?item.id:item.dados.placa;
    if(!window.confirm(`${item.ativo?'Inativar':'Reativar'} ${cadastro.tipo==='clientes'?(item.dados.nomeFantasia||item.dados.razaoSocial):id}?`))return;
    situacao.disabled=true;try{const salvo=await consultarApi(`/api/cadastros/${cadastro.tipo}/${encodeURIComponent(id)}/situacao`,{versao:item.versao,ativo:!item.ativo});await aposAlterarCadastro(salvo);mostrarMensagem(item.ativo?'Cadastro inativado.':'Cadastro reativado.',true);}catch(erro){mostrarMensagem(erro.message);situacao.disabled=false;}
  }
});
selecionar('#voltarBases').addEventListener('click',()=>trocarPagina('bases'));selecionar('#incluirCadastro').addEventListener('click',()=>formularioCadastro());
selecionar('#atualizarCadastro').addEventListener('click',carregarCadastro);
selecionar('#manterCadastro').addEventListener('click',()=>revisarCadastro(false));selecionar('#aplicarImportacaoCadastro').addEventListener('click',()=>revisarCadastro(true));
let buscaCadastroTemporizador;selecionar('#buscaCadastro').addEventListener('input',()=>{clearTimeout(buscaCadastroTemporizador);buscaCadastroTemporizador=setTimeout(()=>{cadastro.pagina=0;carregarCadastro();},250);});
for(const seletor of ['#situacaoCadastro','#revisaoCadastro'])selecionar(seletor).addEventListener('change',()=>{cadastro.pagina=0;carregarCadastro();});
selecionar('#anteriorCadastro').addEventListener('click',()=>{cadastro.pagina--;carregarCadastro();});selecionar('#proximoCadastro').addEventListener('click',()=>{cadastro.pagina++;carregarCadastro();});
