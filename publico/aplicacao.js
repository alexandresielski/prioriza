const selecionar = (seletor) => document.querySelector(seletor);
const selecionarTodos = (seletor) => [...document.querySelectorAll(seletor)];
const estado = { resumo: null, resultado: null, ajustes: {}, previsoes: {}, medias: [], locais: {}, placaEdicao: null,
  campoCoordenadas: null, temporizadores: {}, sequencias: {}, resultadosObsoletos: false,
  planejamento: [], planejamentoEdicao: null, cancelamentoCarga: null };
const formatoNumero = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });
const formatoDinheiro = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 });
const nomesCriterios = { distancia: 'Deslocamento', prazo: 'Folga de chegada', faturamento: 'Faturamento / dia', seguranca: 'Segurança da disponibilidade' };

function escaparHtml(valor) {
  return String(valor ?? '').replace(/[&<>"']/g, caractere => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[caractere]));
}

function atualizarIcones() {
  if (window.lucide) window.lucide.createIcons({ attrs: { 'stroke-width': 1.7 } });
}

function formatarData(valor) {
  if (!valor) return 'Não informado';
  const data = new Date(valor);
  return Number.isNaN(data.getTime()) ? 'Data inválida' : data.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function formatarDuracao(horas) {
  if (horas == null) return '--';
  const minutos = Math.round(horas * 60);
  return `${Math.floor(minutos / 60)}h${String(minutos % 60).padStart(2, '0')}`;
}

function paraEntradaData(data) {
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}T${String(data.getHours()).padStart(2, '0')}:${String(data.getMinutes()).padStart(2, '0')}`;
}

async function consultarApi(caminho, dados, metodo = 'POST') {
  const resposta = await fetch(caminho, dados ? { method: metodo, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(dados) } : {});
  const conteudo = await resposta.json();
  if (!resposta.ok) throw new Error(conteudo.erro || 'Não foi possível concluir a operação.');
  return conteudo;
}

function mostrarMensagem(texto, sucesso = false) {
  const mensagem = selecionar('#mensagem');
  mensagem.textContent = texto;
  mensagem.className = `mensagem${sucesso ? ' sucesso' : ''}`;
  mensagem.hidden = false;
  mensagem.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
}

function invalidarResultado() {
  if (typeof invalidarCenario === 'function') invalidarCenario();
  if (!estado.resultado) { desenharPosicoes(); return; }
  estado.resultadosObsoletos = true;
  selecionar('#exportarResultado').disabled = true;
  selecionar('#resumoResultado').textContent = 'Dados alterados. Avalie novamente para atualizar esta classificação.';
  desenharPosicoes();
}

function trocarPagina(pagina, atualizarDados = true) {
  selecionar('#mensagem').hidden = true;
  selecionar('#mensagem').textContent = '';
  selecionarTodos('.pagina').forEach(secao => { secao.hidden = secao.id !== `pagina-${pagina}`; });
  selecionarTodos('.navegacao').forEach(botao => botao.classList.toggle('ativa', botao.dataset.pagina === pagina));
  selecionar('#nomePagina').textContent = { simulacao: 'Simulação', planejamento: 'Cargas previstas', frota: 'Composições', bases: 'Bases de dados', metodo: 'Parâmetros' }[pagina];
  if (pagina === 'simulacao') { desenharPosicoes(); if (atualizarDados) conferirReservasSimulacao(); }
  if (pagina === 'frota') atualizarPrevisoes();
  if (pagina === 'planejamento' && atualizarDados) return carregarPlanejamento();
  if (pagina === 'cenarios') { selecionar('#nomePagina').textContent = 'Cenários'; if (atualizarDados) return carregarCenarios(); }
}

async function recarregarMenu(pagina) {
  const consulta = (estado.consultaMenu || 0) + 1;
  estado.consultaMenu = consulta;
  estado.menuAtualizando = null;
  selecionar('#formularioCarga').inert = false;
  selecionarTodos('.pagina').forEach(secao => secao.removeAttribute('aria-busy'));
  if (!['simulacao', 'planejamento', 'cenarios'].includes(pagina)) { trocarPagina(pagina); return; }
  trocarPagina(pagina, false);
  estado.menuAtualizando = pagina;
  selecionar('#pagina-' + pagina).setAttribute('aria-busy', 'true');
  if (pagina === 'simulacao') { novaCargaPrevista(false); selecionar('#formularioCarga').inert = true; }
  if (pagina === 'cenarios') { limparCenario(); atualizarAcoesCenario(); }
  try {
    if (pagina === 'planejamento') { await carregarPlanejamento(); return; }
    const resumo = await consultarApi('/api/resumo');
    const medias = await consultarApi('/api/tempos-atendimento');
    if (consulta !== estado.consultaMenu) return;
    estado.medias = medias; atualizarTemposAtendimento();
    await atualizarResumo(resumo);
    if (consulta !== estado.consultaMenu) return;
    if (pagina === 'simulacao') await conferirReservasSimulacao();
    else await carregarCenarios();
  } catch (erro) {
    if (consulta === estado.consultaMenu) mostrarMensagem('Não foi possível atualizar os dados da tela: ' + erro.message);
  } finally {
    if (consulta === estado.consultaMenu) {
      estado.menuAtualizando = null;
      selecionar('#pagina-' + pagina).removeAttribute('aria-busy');
      selecionar('#formularioCarga').inert = false;
      if (pagina === 'cenarios') atualizarAcoesCenario();
    }
  }
}

function atualizarContextoPlanejamento() {
  const edicao = estado.planejamentoEdicao;
  selecionar('#contextoPlanejamento').hidden = !edicao;
  selecionar('#contextoPlanejamento').textContent = edicao ? 'Editando carga prevista / versão ' + edicao.versao : '';
  selecionar('#salvarCargaPrevista').innerHTML = `<i data-lucide="save"></i>${edicao ? 'Salvar alterações' : 'Salvar carga prevista'}`;
  atualizarIcones();
}

function novaCargaPrevista(navegar = true) {
  estado.planejamentoEdicao = null; atualizarContextoPlanejamento();
  selecionar('#identificacao').value = '';
  selecionar('#cargaObrigatoria').checked = false;
  selecionarLocal('origem', null); selecionarLocal('destino', null);
  selecionar('#peso').value = ''; selecionar('#frete').value = '';
  selecionar('#historico-carga').checked = true; selecionar('#historico-descarga').checked = true;
  atualizarTemposAtendimento();
  selecionar('#coleta').value = '';
  invalidarResultado(); limparResultadoSimulacao();
  if (navegar) { trocarPagina('simulacao'); selecionar('#identificacao').focus(); }
}

function atualizarSimulacaoAposReservas(reservas) {
  const assinatura = reservas.map(item => item.id).sort().join('|');
  const mudou = estado.assinaturaReservasSimulacao != null && estado.assinaturaReservasSimulacao !== assinatura;
  estado.assinaturaReservasSimulacao = assinatura;
  if (estado.planejamentoEdicao && reservas.some(item => item.carga === estado.planejamentoEdicao.id)) {
    novaCargaPrevista(false);
  } else if (mudou && estado.resultado) limparResultadoSimulacao();
}

async function conferirReservasSimulacao() {
  const consulta = (estado.consultaReservasSimulacao || 0) + 1;
  estado.consultaReservasSimulacao = consulta;
  try {
    const reservas = await consultarApi('/api/reservas');
    if (consulta === estado.consultaReservasSimulacao) atualizarSimulacaoAposReservas(reservas);
  } catch (erro) {
    limparResultadoSimulacao();
    mostrarMensagem('Não foi possível conferir as reservas. Avalie novamente antes de escolher uma composição.');
  }
}

function limparResultadoSimulacao() {
  estado.versaoResultadoSimulacao = (estado.versaoResultadoSimulacao || 0) + 1;
  estado.resultado = null; estado.resultadosObsoletos = false;
  selecionar('#ranking').innerHTML = ''; selecionar('#pesos').innerHTML = '';
  selecionar('#indicadorViaveis').textContent = '--'; selecionar('#indicadorDistancia').textContent = '--';
  selecionar('#quantidadeResultados').textContent = '0'; selecionar('#contextoPrazo').textContent = 'Aguardando carga';
  selecionar('#resumoResultado').textContent = ''; selecionar('#avisosResultado').innerHTML = '';
  selecionar('#listaExcluidas').innerHTML = ''; selecionar('#painelExcluidas').hidden = true;
  selecionar('#exportarResultado').disabled = true;
  selecionar('#estadoVazio').innerHTML = '<i data-lucide="route"></i><h3>Nenhuma carga avaliada.</h3>';
  selecionar('#estadoVazio').hidden = false; atualizarIcones(); desenharPosicoes();
}

async function carregarPlanejamento() {
  selecionar('#resumoPlanejamento').textContent = 'Carregando cargas...';
  try {
    estado.planejamento = await consultarApi('/api/planejamento');
    estado.reservasPlanejamento = {};
    const cenarios = await consultarApi('/api/cenarios');
    const reservasAtivas = await consultarApi('/api/reservas');
    for (const cenario of cenarios.filter(item => item.situacao === 'CONFIRMADO')) {
      const resultado = typeof cenario.resultado === 'string' ? JSON.parse(cenario.resultado) : cenario.resultado;
      for (const reserva of resultado?.recomendacoes || []) {
        const ativa = reservasAtivas.find(item => item.carga === reserva.carga && item.cenario === cenario.id);
        if (ativa) estado.reservasPlanejamento[reserva.carga] = { ...reserva, ...ativa };
      }
    }
    renderizarPlanejamento();
  } catch (erro) { selecionar('#resumoPlanejamento').textContent = 'Não foi possível atualizar as cargas.'; mostrarMensagem(erro.message); }
}

function renderizarPlanejamento() {
  const termo = normalizarNome(selecionar('#filtroPlanejamento').value);
  const situacao = selecionar('#situacaoPlanejamento').value;
  const inicio = selecionar('#coletaPlanejamentoDe').value, fim = selecionar('#coletaPlanejamentoAte').value;
  const itens = estado.planejamento.filter(item => {
    const carga = item.carga, dia = carga.coleta.slice(0, 10);
    const reserva = item.alocada ? estado.reservasPlanejamento?.[item.id] : null;
    return (situacao === 'TODAS' || (situacao === 'RESERVADA' ? item.alocada : item.situacao === situacao)) && (!inicio || dia >= inicio) && (!fim || dia <= fim)
      && normalizarNome([carga.identificacao, carga.origem?.nome, carga.origem?.cidade, carga.destino?.nome, carga.destino?.cidade, reserva?.placa, reserva?.carreta].join(' ')).includes(termo);
  });
  const pendentes = estado.planejamento.filter(item => item.situacao === 'PENDENTE' && !item.alocada).length;
  estado.reservasVisiveis = itens.filter(item => item.alocada && estado.reservasPlanejamento?.[item.id]).map(item => estado.reservasPlanejamento[item.id].id);
  estado.selecaoReservas = new Set([...(estado.selecaoReservas || [])].filter(id => estado.reservasVisiveis.includes(id)));
  atualizarSelecaoReservas();
  selecionar('#totalPlanejamento').textContent = pendentes;
  selecionar('#resumoPlanejamento').textContent = `${pendentes} pendente(s) / ${itens.length} carga(s) exibida(s)`;
  selecionar('#tabelaPlanejamento').innerHTML = itens.map(item => {
    const carga = item.carga, pendente = item.situacao === 'PENDENTE' && !item.alocada;
    const reserva = item.alocada ? estado.reservasPlanejamento?.[item.id] : null;
    return `<tr data-carga-planejada="${item.id}"><td><input type="checkbox" data-selecionar-reserva="${reserva?.id || ''}" aria-label="Selecionar reserva da carga ${escaparHtml(carga.identificacao)}" ${reserva ? '' : 'disabled'} ${estado.selecaoReservas.has(reserva?.id) ? 'checked' : ''}></td><td><strong>${escaparHtml(carga.identificacao)}</strong>${carga.obrigatoria ? '<small>Carga obrigatória</small>' : ''}<small>${formatarData(carga.coleta)}</small></td>
      <td>${escaparHtml(carga.origem?.nome)}<small>${escaparHtml(carga.origem?.cidade)}</small></td>
      <td>${escaparHtml(carga.destino?.nome)}<small>${escaparHtml(carga.destino?.cidade)}</small></td>
      <td>${formatoNumero.format(carga.peso / 1000)} t<small>${carga.frete == null ? 'Frete não informado' : formatoDinheiro.format(carga.frete)}</small></td>
      <td><span class="etiqueta">${item.alocada ? 'Reservada' : pendente ? 'Pendente' : 'Cancelada'}</span>${reserva ? `<small>Cavalo <strong>${escaparHtml(reserva.placa)}</strong></small><small>Carreta <strong>${escaparHtml(reserva.carreta || '--')}</strong></small>` : ''}</td>
      <td><div class="acoes-linha">${pendente ? `<button class="botao-icone" data-avaliar-planejada="${item.id}" title="Avaliar composições" aria-label="Avaliar ${escaparHtml(carga.identificacao)}"><i data-lucide="scan-line"></i></button>
      <button class="botao-icone" data-editar-planejada="${item.id}" title="Editar carga" aria-label="Editar ${escaparHtml(carga.identificacao)}"><i data-lucide="pencil"></i></button>
      <button class="botao-icone" data-cancelar-planejada="${item.id}" title="Cancelar carga" aria-label="Cancelar ${escaparHtml(carga.identificacao)}"><i data-lucide="circle-x"></i></button>` : item.alocada ? `<button class="botao secundario" data-ver-reserva="${item.id}"><i data-lucide="eye"></i>Ver reserva</button>` : '<span class="texto-suave">Encerrada</span>'}</div></td></tr>`;
  }).join('');
  selecionar('#vazioPlanejamento').hidden = itens.length > 0;
  atualizarIcones();
}

function atualizarSelecaoReservas() {
  const quantidade = estado.selecaoReservas?.size || 0;
  selecionar('#cancelarReservasLote').disabled = quantidade === 0;
  selecionar('#quantidadeReservasLote').textContent = quantidade;
  const todas = selecionar('#selecionarReservasVisiveis');
  todas.disabled = !estado.reservasVisiveis?.length;
  todas.checked = quantidade > 0 && quantidade === estado.reservasVisiveis?.length;
  todas.indeterminate = quantidade > 0 && !todas.checked;
}

function abrirCancelamentoReservasLote() {
  estado.loteReservas = Object.values(estado.reservasPlanejamento || {}).filter(item => estado.selecaoReservas?.has(item.id)).map(item => ({...item}));
  if (!estado.loteReservas.length) return;
  selecionar('#listaCancelamentoReservas').innerHTML = estado.loteReservas.map(reserva => {
    const carga = estado.planejamento.find(item => item.id === reserva.carga);
    return `<li>${escaparHtml(carga?.carga.identificacao)} / ${escaparHtml(reserva.placa)} + ${escaparHtml(reserva.carreta)}</li>`;
  }).join('');
  selecionar('#erroCancelamentoLote').hidden = true;
  selecionar('#dialogoCancelarReservasLote').showModal();
}

async function cancelarReservasLote() {
  const botao = selecionar('#confirmarCancelamentoLote');
  if (botao.disabled || !estado.loteReservas?.length) return;
  const dialogo = selecionar('#dialogoCancelarReservasLote');
  botao.disabled = true; dialogo.oncancel = evento => evento.preventDefault();
  selecionarTodos('#dialogoCancelarReservasLote [data-fechar]').forEach(item => item.disabled = true);
  selecionar('#erroCancelamentoLote').hidden = true;
  try {
    const resultado = await consultarApi('/api/reservas/cancelar-lote', {reservas: estado.loteReservas.map(item => item.id)});
    estado.selecaoReservas = new Set(); dialogo.close(); invalidarResultado();
    await carregarPlanejamento();
    mostrarMensagem(resultado.canceladas + ' reserva(s) cancelada(s). Cargas preservadas.', true);
  } catch (erro) {
    selecionar('#erroCancelamentoLote').textContent = erro.message;
    selecionar('#erroCancelamentoLote').hidden = false;
    await carregarPlanejamento();
  } finally {
    botao.disabled = false; dialogo.oncancel = null;
    selecionarTodos('#dialogoCancelarReservasLote [data-fechar]').forEach(item => item.disabled = false);
  }
}

function abrirReservaPlanejada(id) {
  const item = estado.planejamento.find(item => item.id === id && item.alocada);
  if (!item) return;
  const reserva = estado.reservasPlanejamento?.[id];
  estado.reservaEmConsulta = reserva ? { ...reserva, identificacao: item.carga.identificacao } : null;
  selecionar('#cancelarReservaPlanejada').hidden = !reserva;
  selecionar('#tituloReservaPlanejada').textContent = 'Reserva / ' + item.carga.identificacao;
  const campo = (titulo, valor) => `<dt>${escaparHtml(titulo)}</dt><dd>${escaparHtml(valor)}</dd>`;
  selecionar('#detalhesReservaPlanejada').innerHTML = reserva ? '<dl>'
    + campo('Cavalo', reserva.placa) + campo('Carreta', reserva.carreta || '--')
    + campo('Origem', item.carga.origem.nome + ' / ' + item.carga.origem.cidade)
    + campo('Destino', item.carga.destino.nome + ' / ' + item.carga.destino.cidade)
    + campo('Coleta', formatarData(item.carga.coleta))
    + campo('Nota', reserva.nota == null ? '--' : formatoNumero.format(reserva.nota))
    + campo('Deslocamento estimado', reserva.distancia == null ? '--' : formatoNumero.format(reserva.distancia) + ' km')
    + campo('Folga da chegada', reserva.folga == null ? '--' : formatarDuracao(reserva.folga))
    + '</dl>'
    : '<p>Detalhes indisponíveis. Atualize a lista para consultar novamente.</p>';
  selecionar('#dialogoReservaPlanejada').showModal();
}

async function cancelarReservaPlanejada() {
  const reserva = estado.reservaEmConsulta;
  const botao = selecionar('#confirmarCancelamentoReserva');
  if (!reserva || botao.disabled) return;
  botao.disabled = true; selecionar('#erroCancelamentoReserva').hidden = true;
  selecionar('#dialogoCancelarReserva').oncancel = evento => evento.preventDefault();
  selecionarTodos('#dialogoCancelarReserva [data-fechar]').forEach(item => item.disabled = true);
  try {
    await consultarApi('/api/reservas/' + reserva.id + '/cancelar', {});
    selecionar('#dialogoCancelarReserva').close(); selecionar('#dialogoReservaPlanejada').close();
    estado.reservaEmConsulta = null;
    invalidarResultado();
    if (typeof invalidarCenario === 'function') invalidarCenario();
    await carregarPlanejamento();
    mostrarMensagem('Reserva da carga ' + reserva.identificacao + ' cancelada. Demais reservas preservadas.', true);
  } catch (erro) {
    selecionar('#erroCancelamentoReserva').textContent = erro.message;
    selecionar('#erroCancelamentoReserva').hidden = false;
  } finally {
    botao.disabled = false; selecionar('#dialogoCancelarReserva').oncancel = null;
    selecionarTodos('#dialogoCancelarReserva [data-fechar]').forEach(item => item.disabled = false);
  }
}

async function salvarCargaPrevista() {
  if (!selecionar('#formularioCarga').reportValidity()) return;
  const botao = selecionar('#salvarCargaPrevista'); botao.disabled = true;
  try {
    const edicao = estado.planejamentoEdicao;
    await consultarApi('/api/planejamento' + (edicao ? '/' + edicao.id : ''),
      { carga: obterCarga(), versao: edicao?.versao ?? null }, edicao ? 'PUT' : 'POST');
    novaCargaPrevista();
    trocarPagina('planejamento'); mostrarMensagem('Carga prevista salva no planejamento.', true);
  } catch (erro) { mostrarMensagem(erro.message); }
  finally { botao.disabled = false; }
}

async function abrirCargaPlanejada(id, avaliar) {
  const item = estado.planejamento.find(item => item.id === id);
  if (!item || item.situacao !== 'PENDENTE' || item.alocada) return;
  if (!avaliar) { abrirEdicaoCarga(item); return; }
  limparResultadoSimulacao();
  estado.planejamentoEdicao = { id: item.id, versao: item.versao };
  const carga = item.carga;
  selecionar('#identificacao').value = carga.identificacao;
  selecionar('#cargaObrigatoria').checked = Boolean(carga.obrigatoria);
  selecionarLocal('origem', carga.origem); selecionarLocal('destino', carga.destino);
  selecionar('#coleta').value = carga.coleta; selecionar('#peso').value = carga.peso;
  selecionar('#frete').value = carga.frete ?? '';
  selecionar('#historico-carga').checked = carga.tempoCarga == null;
  selecionar('#historico-descarga').checked = carga.tempoDescarga == null;
  if (carga.tempoCarga != null) selecionar('#tempoCarga').value = carga.tempoCarga;
  if (carga.tempoDescarga != null) selecionar('#tempoDescarga').value = carga.tempoDescarga;
  atualizarTemposAtendimento(); atualizarContextoPlanejamento(); invalidarResultado(); trocarPagina('simulacao');
  if (avaliar) await simularCarga();
}

function abrirEdicaoCarga(item) {
  estado.edicaoCarga = structuredClone(item);
  const carga = item.carga;
  selecionar('#formularioEdicaoCarga').reset();
  selecionar('#erroEdicaoCarga').hidden = true;
  selecionar('#edicaoIdentificacao').value = carga.identificacao;
  selecionar('#edicaoColeta').value = carga.coleta;
  selecionar('#edicaoPeso').value = carga.peso;
  selecionar('#edicaoFrete').value = carga.frete ?? '';
  selecionar('#edicaoObrigatoria').checked = Boolean(carga.obrigatoria);
  for (const campo of ['origem', 'destino']) selecionarLocal('edicao-' + campo, structuredClone(carga[campo]));
  for (const tipo of ['Carga', 'Descarga']) {
    selecionar('#edicaoHistorico' + tipo).checked = carga['tempo' + tipo] == null;
    selecionar('#edicaoTempo' + tipo).disabled = carga['tempo' + tipo] == null;
    selecionar('#edicaoTempo' + tipo).value = carga['tempo' + tipo] ?? '';
  }
  selecionar('#dialogoEdicaoCarga').showModal();
}

async function salvarEdicaoCarga(evento) {
  evento.preventDefault();
  const erroTela = selecionar('#erroEdicaoCarga');
  const botao = selecionar('#salvarEdicaoCarga');
  erroTela.hidden = true;
  const item = estado.edicaoCarga;
  if (!item || botao.disabled) return;
  botao.disabled = true;
  try {
    const origem = estado.locais['edicao-origem'], destino = estado.locais['edicao-destino'];
    if ([origem, destino].some(local => !local || local.latitude == null || local.longitude == null)) throw new Error('Selecione origem e destino com coordenadas.');
    const carga = { ...item.carga, identificacao: selecionar('#edicaoIdentificacao').value.trim(),
      coleta: selecionar('#edicaoColeta').value, peso: Number(selecionar('#edicaoPeso').value),
      frete: selecionar('#edicaoFrete').value === '' ? null : Number(selecionar('#edicaoFrete').value),
      obrigatoria: selecionar('#edicaoObrigatoria').checked, origem, destino };
    for (const tipo of ['Carga', 'Descarga']) carga['tempo' + tipo] = selecionar('#edicaoHistorico' + tipo).checked ? null : Number(selecionar('#edicaoTempo' + tipo).value);
    const registro = await consultarApi('/api/planejamento/' + item.id, { carga, versao: item.versao }, 'PUT');
    estado.planejamento = estado.planejamento.map(atual => atual.id === item.id ? registro : atual);
    renderizarPlanejamento();
    if (typeof invalidarCenario === 'function') invalidarCenario();
    if (estado.planejamentoEdicao?.id === item.id) {
      estado.planejamentoEdicao = null; atualizarContextoPlanejamento(); limparResultadoSimulacao();
    }
    selecionar('#dialogoEdicaoCarga').close();
    mostrarMensagem('Carga prevista atualizada.', true);
  } catch (erro) { erroTela.textContent = erro.message; erroTela.hidden = false; }
  finally { botao.disabled = false; }
}

function abrirCancelamentoCarga(id) {
  const item = estado.planejamento.find(item => item.id === id);
  if (!item || item.situacao !== 'PENDENTE') return;
  estado.cancelamentoCarga = { id: item.id, versao: item.versao };
  selecionar('#identificacaoCancelarCarga').textContent = item.carga.identificacao;
  selecionar('#dialogoCancelarCarga').showModal();
}

async function cancelarCargaPrevista() {
  const item = estado.cancelamentoCarga;
  if (!item) return;
  const botao = selecionar('#confirmarCancelamentoCarga'); botao.disabled = true;
  try {
    await consultarApi(`/api/planejamento/${item.id}/cancelar`, { versao: item.versao });
    if (estado.planejamentoEdicao?.id === item.id) { estado.planejamentoEdicao = null; atualizarContextoPlanejamento(); }
    selecionar('#dialogoCancelarCarga').close(); estado.cancelamentoCarga = null;
    await carregarPlanejamento(); mostrarMensagem('Carga cancelada. O registro foi preservado no planejamento.', true);
  } catch (erro) { selecionar('#dialogoCancelarCarga').close(); mostrarMensagem(erro.message); }
  finally { botao.disabled = false; }
}

function selecionarLocal(campo, local) {
  estado.sequencias[campo] = (estado.sequencias[campo] || 0) + 1;
  estado.locais[campo] = local;
  selecionar(`#busca-${campo}`).value = local ? `${local.nome} (${local.cidade})` : '';
  selecionar(`#sugestoes-${campo}`).hidden = true;
  selecionar(`#busca-${campo}`).setAttribute('aria-expanded', 'false');
  renderizarDetalheLocal(campo);
  if (campo !== 'liberacao' && !campo.startsWith('edicao-')) { atualizarTemposAtendimento(); invalidarResultado(); }
}

function normalizarNome(valor) {
  return String(valor || '').normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toUpperCase().replace(/[^A-Z0-9]/g, '');
}

function normalizarMunicipio(valor) {
  const partes = String(valor || '').match(/^\s*(.+?)\s*[-/]\s*([A-Za-z]{2})(?:\s*\(.*)?\s*$/);
  return partes ? `${normalizarNome(partes[1])}/${partes[2].toUpperCase()}` : '';
}

function atualizarTemposAtendimento() {
  for (const [tipo, campo, entrada, parametro] of [['carga', 'origem', 'tempoCarga', 'tempoCargaRestante'], ['descarga', 'destino', 'tempoDescarga', 'tempoDescargaRestante']]) {
    const automatico = selecionar(`#historico-${tipo}`).checked;
    const local = estado.locais[campo];
    const cidade = normalizarMunicipio(local?.cidade);
    const media = cidade ? estado.medias.find(media => media.tipo === tipo && media.utilizados >= 10
      && media.mediaHoras > 0 && normalizarNome(media.cliente) === normalizarNome(local?.nome)
      && normalizarMunicipio(media.cidade) === cidade) : null;
    selecionar(`#${entrada}`).disabled = automatico;
    if (automatico) selecionar(`#${entrada}`).value = Number((media?.mediaHoras ?? Number(selecionar(`#${parametro}`).value)).toFixed(4));
    selecionar(`#fonte-${tipo}`).textContent = automatico
      ? media ? `Média histórica: ${formatarDuracao(media.mediaHoras)} / ${media.utilizados} atendimentos / ${media.cidade}`
        : 'Tempo configurado: sem histórico suficiente para cliente + município.'
      : 'Tempo de atendimento informado manualmente.';
  }
}

async function carregarMediasAtendimento() {
  estado.medias = await consultarApi('/api/tempos-atendimento');
  atualizarTemposAtendimento();
}

function renderizarDetalheLocal(campo) {
  const local = estado.locais[campo];
  let texto = 'Nenhum local selecionado';
  if (local) {
    texto = local.latitude != null && local.longitude != null
      ? `${Number(local.latitude).toFixed(4)}, ${Number(local.longitude).toFixed(4)} / ${local.fonte === 'cliente' ? 'Estabelecimento' : local.fonte === 'manual' ? 'Informado manualmente' : 'Referência municipal aproximada'}`
      : 'Sem coordenadas. Informe um ponto para continuar.';
  }
  selecionar(`#detalhe-${campo}`).innerHTML = `<span>${escaparHtml(texto)}</span><button type="button" class="editar-coordenadas" data-coordenadas="${campo}" title="Informar coordenadas" aria-label="Informar coordenadas de ${campo}"><i data-lucide="pencil"></i></button>`;
  atualizarIcones();
}

async function buscarLocais(campo) {
  const entrada = selecionar(`#busca-${campo}`);
  const consulta = entrada.value.trim();
  const sequencia = (estado.sequencias[campo] || 0) + 1;
  estado.sequencias[campo] = sequencia;
  if (consulta.length < 2) {
    selecionar(`#sugestoes-${campo}`).hidden = true;
    entrada.setAttribute('aria-expanded', 'false');
    return;
  }
  try {
    const locais = await consultarApi(`/api/locais?q=${encodeURIComponent(consulta)}`);
    if (sequencia !== estado.sequencias[campo]) return;
    const lista = selecionar(`#sugestoes-${campo}`);
    lista.innerHTML = locais.map((local, indice) => `<button type="button" class="sugestao" role="option" data-indice="${indice}">${escaparHtml(local.nome)}<small>${escaparHtml(local.cidade)} / ${local.fonte === 'cliente' ? 'Coordenada do cliente' : local.fonte === 'ausente' ? 'Coordenadas ausentes' : 'Referência municipal aproximada'}</small></button>`).join('') + `<button type="button" class="sugestao manual" data-manual="${campo}">Informar coordenadas manualmente</button>`;
    lista.querySelectorAll('[data-indice]').forEach(botao => botao.addEventListener('click', () => selecionarLocal(campo, locais[Number(botao.dataset.indice)])));
    lista.querySelector('[data-manual]').addEventListener('click', () => abrirCoordenadas(campo));
    lista.hidden = false;
    entrada.setAttribute('aria-expanded', 'true');
  } catch (erro) { mostrarMensagem(erro.message); }
}

function abrirCoordenadas(campo) {
  estado.campoCoordenadas = campo;
  const local = estado.locais[campo];
  selecionar('#nomeCoordenadas').value = local?.nome || selecionar(`#busca-${campo}`).value;
  selecionar('#latitudeManual').value = local?.latitude ?? '';
  selecionar('#longitudeManual').value = local?.longitude ?? '';
  selecionar(`#sugestoes-${campo}`).hidden = true;
  selecionar('#dialogoCoordenadas').showModal();
}

function atualizarResumo(resumo, redefinirReferencia = false) {
  estado.resumo = resumo;
  estado.previsoes = {};
  const sider = resumo.composicoes.filter(item => item.grupo === 'SIDER');
  selecionar('#totalFrota').textContent = sider.length;
  selecionar('#indicadorFrota').textContent = sider.length;
  selecionar('#subtituloBase').textContent = `Base de posições: ${formatarData(resumo.referencia)} / ${resumo.contagens.Vazio || 0} registros SIDER vazios`;
  selecionar('#resumoFrota').textContent = `${sider.length} registros SIDER. Posições da base de ${formatarData(resumo.referencia)}.`;
  selecionar('#pastaBases').textContent = resumo.pasta;
  if (redefinirReferencia || !selecionar('#referencia').value) {
    selecionar('#referencia').value = resumo.referencia;
  }
  selecionar('#listaBases').innerHTML = resumo.bases.map(base => {
    const cadastro = base.chave === 'RELACAOCLIENTESCOORDENADAS' ? 'clientes' : base.chave === 'RELACAOVEICULOS' ? 'veiculos' : null;
    return `<div class="base-linha"><i data-lucide="${base.carregada ? 'file-spreadsheet' : 'file-question'}"></i><div>${cadastro ? `<button class="abrir-cadastro" data-cadastro="${cadastro}">${escaparHtml(base.nome)}<i data-lucide="chevron-right"></i></button>` : `<h3>${escaparHtml(base.nome)}</h3>`}<small>${base.nome === 'Viagens históricas' ? 'Médias SIDER por cliente + município / sem fila e espera' : base.nome === 'Cargas históricas' ? `${formatoNumero.format(resumo.cargasConsolidadas)} cargas consolidadas / sem somar pesos repetidos` : 'Cadastro operacional'}</small></div><strong>${base.registros == null ? '--' : formatoNumero.format(base.registros)}</strong><span class="etiqueta">${base.carregada ? 'Carregada' : 'Não carregada'}</span></div>`;
  }).join('');
  renderizarFrota();
  atualizarIcones();
  desenharPosicoes();
  return atualizarPrevisoes();
}

async function atualizarPrevisoes() {
  if (!estado.resumo) return;
  const sequencia = (estado.sequencias.previsoes || 0) + 1;
  estado.sequencias.previsoes = sequencia;
  selecionar('#resumoPrevisoes').textContent = 'Calculando locais e horários de liberação...';
  try {
    const previsoes = await consultarApi('/api/disponibilidades', { referencia: selecionar('#referencia').value,
      parametros: obterParametros(), ajustes: estado.ajustes });
    if (sequencia !== estado.sequencias.previsoes) return;
    estado.previsoes = previsoes;
    const lista = Object.values(previsoes);
    selecionar('#resumoPrevisoes').textContent = `${lista.filter(item => item.tipo === 'automatica').length} estimativas automáticas / ${lista.filter(item => item.tipo === 'manual').length} manuais / ${lista.filter(item => !item.disponivel).length} pendentes. Referência: ${formatarData(selecionar('#referencia').value)}.`;
    renderizarFrota();
    desenharPosicoes();
  } catch (erro) {
    if (sequencia !== estado.sequencias.previsoes) return;
    estado.previsoes = {};
    selecionar('#resumoPrevisoes').textContent = `Previsões indisponíveis: ${erro.message}`;
    renderizarFrota();
    desenharPosicoes();
  }
}

function renderizarFrota() {
  if (!estado.resumo) return;
  const termo = selecionar('#filtroFrota').value.toLocaleLowerCase('pt-BR');
  const somenteVazios = selecionar('#somenteVazios').checked;
  const itens = estado.resumo.composicoes.filter(item => item.grupo === 'SIDER'
    && (!somenteVazios || item.situacao === 'Vazio')
    && `${item.placa} ${item.carreta} ${item.cidade} ${item.situacao}`.toLocaleLowerCase('pt-BR').includes(termo));
  selecionar('#tabelaFrota').innerHTML = itens.map(item => {
    const previsao = estado.previsoes[item.placa];
    const ajuste = estado.ajustes[item.placa];
    const rotulo = ({ automatica: 'Estimada por etapas', manual: 'Previsão manual', vazio: 'Disponível na referência' }[previsao?.tipo] || 'Previsão pendente')
      + (ajuste?.cargaAgendadaEm || ajuste?.descargaAgendadaEm ? ' / Com agendamento' : '');
    return `<tr><td><span class="placa">${escaparHtml(item.placa)}</span><small>${escaparHtml(item.carreta || 'Sem carreta')} ${item.engatada ? '' : '/ Desengatada'}</small></td><td><span class="situacao ${item.situacao.toLowerCase()}">${escaparHtml(item.situacao)}</span></td><td>${escaparHtml(item.cidade)}<small>${formatarData(item.ultimaPosicao)}</small></td><td>${item.capacidade == null ? 'Cadastro incompleto' : `${formatoNumero.format(item.capacidade / 1000)} t`}<small>${item.eixos == null ? 'Eixos não informados' : `${item.eixos} eixos`}</small></td><td><div class="previsao-linha">${previsao?.disponivel ? formatarData(previsao.disponivelEm) : 'Pendente'}<button class="botao-icone" data-previsao="${escaparHtml(item.placa)}" title="Ver etapas da liberação" aria-label="Ver etapas da liberação ${escaparHtml(item.placa)}"><i data-lucide="list-tree"></i></button></div><small>${escaparHtml(previsao?.local?.nome || item.destinoAtual || '')}</small><span class="origem-disponibilidade">${rotulo}</span></td><td><button class="botao-icone" data-liberacao="${escaparHtml(item.placa)}" title="Editar liberação" aria-label="Editar liberação ${escaparHtml(item.placa)}"><i data-lucide="calendar-clock"></i></button></td></tr>`;
  }).join('') || '<tr><td colspan="6">Nenhuma composição encontrada.</td></tr>';
  selecionarTodos('#tabelaFrota [data-liberacao]').forEach(botao => {
    const item = itens.find(item => item.placa === botao.dataset.liberacao);
    if (item.situacao === 'Vazio') return;
    botao.insertAdjacentHTML('beforebegin', `<button class="botao-icone" data-agendamento="${escaparHtml(item.placa)}" title="Agendamento da operação atual" aria-label="Agendamento da operação atual ${escaparHtml(item.placa)}"><i data-lucide="calendar-days"></i></button>`);
  });
  atualizarIcones();
}

function abrirAgendamento(placa) {
  estado.placaEdicao = placa;
  const item = estado.resumo.composicoes.find(item => item.placa === placa);
  const ajuste = estado.ajustes[placa];
  selecionar('#placaAgendamento').textContent = `${placa} / ${item.situacao}`;
  selecionar('#cargaAgendadaEm').disabled = item.situacao !== 'Destinado';
  selecionar('#descargaAgendadaEm').disabled = !['Destinado', 'Carga', 'Viagem'].includes(item.situacao);
  selecionar('#cargaAgendadaEm').value = ajuste?.cargaAgendadaEm || '';
  selecionar('#descargaAgendadaEm').value = ajuste?.descargaAgendadaEm || '';
  selecionar('#operacaoAgendada').textContent = `${item.origemAtual || 'Origem não informada'} > ${item.destinoAtual || 'Destino não informado'}`;
  selecionar('#prioridadeManual').hidden = !ajuste?.disponivelEm && !ajuste?.local;
  selecionar('#dialogoAgendamento').showModal();
}

function abrirLiberacao(placa) {
  estado.placaEdicao = placa;
  const ajuste = estado.ajustes[placa];
  const previsao = estado.previsoes[placa];
  selecionar('#placaLiberacao').textContent = placa;
  selecionar('#disponivelEm').value = ajuste?.disponivelEm || previsao?.disponivelEm?.slice(0, 16) || selecionar('#referencia').value;
  selecionarLocal('liberacao', ajuste?.local || previsao?.local || null);
  selecionar('#dialogoLiberacao').showModal();
}

async function carregarParametrosCombustivel() {
  const formulario = selecionar('#formularioParametros');
  formulario.inert = true;
  try {
    const configuracao = await consultarApi('/api/parametros/combustivel');
    selecionar('#precoCombustivel').value = configuracao.precoCombustivel ?? '';
    selecionar('#consumoMedio').value = configuracao.consumoMedio ?? '';
  } finally { formulario.inert = false; }
}

async function aplicarParametros(evento) {
  evento.preventDefault();
  const botao = evento.target.querySelector('[type="submit"]');
  if (botao.disabled) return;
  botao.disabled = true;
  try {
    const parametros = obterParametros();
    await consultarApi('/api/parametros/combustivel', {precoCombustivel:parametros.precoCombustivel, consumoMedio:parametros.consumoMedio}, 'PUT');
    invalidarResultado(); atualizarRodape(); atualizarTemposAtendimento(); await atualizarPrevisoes();
    mostrarMensagem('Parâmetros aplicados. Combustível e consumo médio salvos. Avalie a carga novamente para atualizar o resultado.', true);
  } catch (erro) { mostrarMensagem('Não foi possível salvar os parâmetros: ' + erro.message); }
  finally { botao.disabled = false; }
}

function obterParametros() {
  return { cenario: selecionar('#cenario').value,
    precoCombustivel: selecionar('#precoCombustivel').value === '' ? null : Number(selecionar('#precoCombustivel').value),
    consumoMedio: selecionar('#consumoMedio').value === '' ? null : Number(selecionar('#consumoMedio').value),
    velocidade: Number(selecionar('#velocidade').value), acrescimo: Number(selecionar('#acrescimo').value),
    toleranciaPosicao: Number(selecionar('#toleranciaPosicao').value), aceitarDesatualizadas: selecionar('#aceitarDesatualizadas').checked,
    considerarOcupados: selecionar('#considerarOcupados').checked,
    tempoCargaRestante: Number(selecionar('#tempoCargaRestante').value),
    tempoDescargaRestante: Number(selecionar('#tempoDescargaRestante').value) };
}

function obterCarga() {
  return { obrigatoria: selecionar('#cargaObrigatoria').checked, identificacao: selecionar('#identificacao').value, origem: estado.locais.origem || null,
    destino: estado.locais.destino || null, referencia: selecionar('#referencia').value,
    coleta: selecionar('#coleta').value, peso: Number(selecionar('#peso').value),
    frete: selecionar('#frete').value === '' ? null : Number(selecionar('#frete').value),
    tempoCarga: selecionar('#historico-carga').checked ? null : Number(selecionar('#tempoCarga').value),
    tempoDescarga: selecionar('#historico-descarga').checked ? null : Number(selecionar('#tempoDescarga').value) };
}

async function simularCarga(evento) {
  evento?.preventDefault();
  if (!selecionar('#formularioCarga').reportValidity()) return;
  selecionar('#mensagem').hidden = true;
  const botao = selecionar('#botaoSimular');
  const versaoResultado = estado.versaoResultadoSimulacao || 0;
  botao.disabled = true;
  try {
    const carga = obterCarga();
    const resultado = await consultarApi('/api/simular', { carga, parametros: obterParametros(), ajustes: estado.ajustes });
    const reservas = await consultarApi('/api/reservas');
    const cargaReservada = estado.planejamentoEdicao && reservas.some(item => item.carga === estado.planejamentoEdicao.id);
    if (versaoResultado !== (estado.versaoResultadoSimulacao || 0)) return;
    atualizarSimulacaoAposReservas(reservas);
    if (cargaReservada) return;
    if (resultado.aprovadas.some(item => reservas.some(reserva => [reserva.placa, reserva.carreta].includes(item.placa) || [reserva.placa, reserva.carreta].includes(item.carreta)))) {
      limparResultadoSimulacao();
      mostrarMensagem('As reservas mudaram durante o cálculo. Avalie as composições novamente.');
      return;
    }
    estado.resultado = { ...resultado, carga };
    for (const item of [...resultado.aprovadas, ...resultado.excluidas]) {
      if (item.previsaoDisponibilidade) estado.previsoes[item.placa] = item.previsaoDisponibilidade;
    }
    estado.resultadosObsoletos = false;
    renderizarResultado();
  } catch (erro) { invalidarResultado(); mostrarMensagem(erro.message); }
  finally { botao.disabled = false; }
}

function renderizarResultado() {
  const resultado = estado.resultado;
  if (!resultado) return;
  selecionar('#indicadorViaveis').textContent = resultado.aprovadas.length;
  selecionar('#indicadorDistancia').textContent = resultado.aprovadas.length ? `${formatoNumero.format(Math.min(...resultado.aprovadas.map(item => item.distancia)))} km` : '--';
  selecionar('#quantidadeResultados').textContent = resultado.aprovadas.length;
  const horasAteColeta = (new Date(resultado.carga.coleta) - new Date(resultado.carga.referencia)) / 3600000;
  selecionar('#contextoPrazo').textContent = `${resultado.parametros.cenario} / ${formatoNumero.format(horasAteColeta)} h até a coleta`;
  selecionar('#pesos').innerHTML = Object.entries(resultado.pesos).map(([criterio, peso]) => `<div class="peso"><div class="peso-titulo"><span>${nomesCriterios[criterio]}</span><strong>${Math.round(peso * 100)}%</strong></div><div class="trilho"><span style="width:${peso * 100}%"></span></div></div>`).join('');
  selecionar('#resumoResultado').textContent = `${resultado.carga.identificacao} / Coleta ${formatarData(resultado.carga.coleta)}${resultado.distanciaCarregada != null ? ` / Trecho carregado estimado: ${formatoNumero.format(resultado.distanciaCarregada)} km` : ''}`;
  selecionar('#ranking').innerHTML = resultado.aprovadas.map((item, indice) => `<tr><td><div class="composicao-celula"><span class="posicao-ranking">${indice + 1}</span><div><span class="placa">${escaparHtml(item.placa)}</span><small>${escaparHtml(item.carreta)} / ${item.eixos ?? '--'} eixos</small></div></div></td><td>${formatoNumero.format(item.distancia)} km<small>${formatarDuracao(item.horasDeslocamento)} estimadas</small></td><td>${formatarData(item.chegada)}<small class="${item.folga < 1 ? 'foco-folga' : ''}">${formatarDuracao(item.folga)} de folga</small><span class="indicador-situacao ${item.folga < 1 ? 'sinal-atencao' : 'sinal-ok'}" title="Folga inferior a uma hora recebe sinal de atenção, sem alterar a pontuação"><i data-lucide="${item.folga < 1 ? 'triangle-alert' : 'check'}"></i>${item.folga < 1 ? 'Folga curta' : 'No prazo'}</span></td><td><span class="nota">${formatoNumero.format(item.nota)}</span><small>de 100</small></td><td><button class="botao-icone" data-detalhe="${indice}" title="Ver justificativa" aria-label="Ver justificativa de ${escaparHtml(item.placa)}"><i data-lucide="chevron-right"></i></button></td></tr>`).join('');
  selecionarTodos('#ranking tr').forEach((linha, indice) => {
    const placa = resultado.aprovadas[indice].placa;
    linha.lastElementChild.insertAdjacentHTML('afterbegin', `<button class="botao-icone" data-localizar-placa="${escaparHtml(placa)}" title="Localizar no mapa" aria-label="Localizar ${escaparHtml(placa)} no mapa"><i data-lucide="map-pin"></i></button>`);
  });
  selecionar('#estadoVazio').hidden = resultado.aprovadas.length > 0;
  selecionarTodos('#ranking tr').forEach((linha, indice) => {
    const custo = resultado.custosCombustivel?.[resultado.aprovadas[indice].placa];
    if (custo != null) linha.children[1].insertAdjacentHTML('beforeend', `<small>Combustível: ${formatoDinheiro.format(custo)}</small>`);
    const previsao = resultado.aprovadas[indice].previsaoDisponibilidade;
    const rotulo = previsao.tipo === 'vazio' ? 'Vazio na referência' : `${previsao.tipo === 'manual' ? 'Manual' : 'Estimada'} / libera ${formatarData(previsao.disponivelEm)}`;
    linha.querySelector('.composicao-celula > div').insertAdjacentHTML('beforeend', `<span class="origem-disponibilidade">${rotulo}</span>`);
  });
  if (!resultado.aprovadas.length) selecionar('#estadoVazio').innerHTML = '<i data-lucide="search-x"></i><h3>Nenhuma composição elegível.</h3><p>Confira os motivos de exclusão abaixo.</p>';
  selecionar('#painelExcluidas').hidden = !resultado.excluidas.length;
  selecionar('#tituloExcluidas').textContent = `${resultado.excluidas.length} composições não elegíveis`;
  selecionar('#listaExcluidas').innerHTML = resultado.excluidas.map(item => `<div class="item-excluido"><div><strong>${escaparHtml(item.placa)}</strong><br><span class="indicador-situacao sinal-erro"><i data-lucide="circle-x"></i>Não elegível</span></div><span>${item.motivos.map(escaparHtml).join('<br>')}</span></div>`).join('');
  selecionar('#avisosResultado').innerHTML = resultado.avisos.map(aviso => `<p>${escaparHtml(aviso)}</p>`).join('');
  selecionar('#exportarResultado').disabled = !resultado.aprovadas.length;
  atualizarIcones();
  desenharPosicoes();
}

function renderizarJustificativaComposicao(item, pesos, obsoleto = false) {
  return `<p class="placa">${escaparHtml(item.placa)} + ${escaparHtml(item.carreta)}</p><p>${item.cidade ? escaparHtml(item.cidade) : 'Local não registrado'}${item.capacidade == null ? '' : ' / ' + formatoNumero.format(item.capacidade / 1000) + ' t de capacidade útil'}</p><div class="detalhes-grade"><div><span>Disponível a partir de</span><strong>${formatarData(item.disponivelEm)}</strong></div><div><span>Chegada estimada</span><strong>${formatarData(item.chegada)}</strong></div><div><span>Distância até a coleta</span><strong>${formatoNumero.format(item.distancia)} km</strong></div><div><span>Folga para a coleta</span><strong>${formatarDuracao(item.folga)}</strong></div><div><span>Fim estimado da operação</span><strong>${item.liberacao ? formatarData(item.liberacao) : 'Destino não informado'}</strong></div><div><span>Receita por dia até a liberação</span><strong>${item.faturamentoDia == null ? 'Não calculada' : formatoDinheiro.format(item.faturamentoDia)}</strong></div></div><h2>Contribuições para a nota</h2>${Object.entries(item.contribuicoes || {}).map(([criterio, contribuicao]) => `<div class="contribuicao"><span>${nomesCriterios[criterio]} / ${Math.round(pesos[criterio] * 100)}%</span><strong>${formatoNumero.format(contribuicao)} pontos</strong></div>`).join('')}<div class="contribuicao"><strong>Nota final</strong><strong>${formatoNumero.format(item.nota)} / 100</strong></div><div class="avisos">${(item.avisos || []).map(aviso => `<p>${escaparHtml(aviso)}</p>`).join('')}<p>Chegada dentro do prazo; equipamento SIDER e peso compatíveis.</p>${obsoleto ? '<p>Dados alterados. Esta justificativa pertence à avaliação anterior.</p>' : ''}</div>` + renderizarEtapas(item.previsaoDisponibilidade);
}

function abrirDetalhes(indice) {
  selecionar('#dialogoDetalhes .titulo-painel h2').textContent = 'Detalhes da recomendação';
  const item = estado.resultado.aprovadas[indice];
  selecionar('#conteudoDetalhes').innerHTML = renderizarJustificativaComposicao(item, estado.resultado.pesos, estado.resultadosObsoletos);
  selecionar('#dialogoDetalhes').showModal();
}

function renderizarEtapas(previsao) {
  if (!previsao) return '';
  const nomes = { automatica: 'Automática por etapas', manual: 'Informada manualmente', vazio: 'Veículo vazio', indisponivel: 'Dados insuficientes' };
  return `<section class="etapas-liberacao"><h2>Liberação da operação atual</h2><p>${nomes[previsao.tipo]}${previsao.disponivel ? ` / ${formatarData(previsao.disponivelEm)}` : ''}</p><p>${escaparHtml(previsao.local?.nome || '')}</p>${previsao.etapas.map(etapa => `<div class="etapa-liberacao"><span>${escaparHtml(etapa.nome)}${etapa.distancia ? ` / ${formatoNumero.format(etapa.distancia)} km` : ''}</span><strong>${formatarDuracao(etapa.horas)}</strong></div>`).join('')}<div class="avisos">${[...previsao.avisos, ...previsao.motivos].map(texto => `<p>${escaparHtml(texto)}</p>`).join('')}</div></section>`;
}

function abrirEtapasLiberacao(placa) {
  const previsao = estado.previsoes[placa];
  if (!previsao) { mostrarMensagem('Recalcule as previsões para conferir as etapas.'); return; }
  selecionar('#dialogoDetalhes .titulo-painel h2').textContent = `Liberação / ${placa}`;
  selecionar('#conteudoDetalhes').innerHTML = renderizarEtapas(previsao);
  selecionar('#dialogoDetalhes').showModal();
}

function desenharPosicoes() {
  atualizarMapaPosicoes();
}

async function carregarExemplo() {
  const consultaMenu = estado.consultaMenu;
  try {
    selecionar('#cargaObrigatoria').checked = false;
    estado.planejamentoEdicao = null; atualizarContextoPlanejamento();
    const origens = await consultarApi('/api/locais?q=Sao%20Paulo');
    const destinos = await consultarApi('/api/locais?q=Ponta%20Grossa');
    if (consultaMenu !== estado.consultaMenu) return;
    const origem = origens.find(local => local.id.startsWith('municipio-') && local.cidade.endsWith('/SP')) || origens.find(local => local.latitude != null);
    const destino = destinos.find(local => local.id.startsWith('municipio-')) || destinos.find(local => local.latitude != null);
    if (!origem) throw new Error('Exemplo indisponível: selecione um local de coleta da sua base.');
    selecionarLocal('origem', origem);
    selecionarLocal('destino', destino || null);
    selecionar('#identificacao').value = 'Carga de teste / São Paulo';
    selecionar('#peso').value = 28000;
    selecionar('#frete').value = 4000;
    const coleta = new Date(selecionar('#referencia').value);
    coleta.setDate(coleta.getDate() + 1); coleta.setHours(10, 0, 0, 0);
    selecionar('#coleta').value = paraEntradaData(coleta);
    await simularCarga();
  } catch (erro) { mostrarMensagem(erro.message); }
}

async function buscarCargas() {
  try {
    const consulta = selecionar('#filtroCargas').value;
    const itens = await consultarApi(`/api/cargas?q=${encodeURIComponent(consulta)}`);
    selecionar('#listaCargas').innerHTML = itens.map(item => `<button class="carga-historica" data-carga="${escaparHtml(item.numero)}"><strong>Carga ${escaparHtml(item.numero)}</strong> / ${escaparHtml(item.situacao)}<small>${escaparHtml(item.origem)} → ${escaparHtml(item.destino)}</small><small>${item.linhas} linha(s) de origem${item.revisar ? ' / Conferir documentos e peso' : ''}</small></button>`).join('') || '<p>Nenhuma carga encontrada.</p>';
  } catch (erro) { mostrarMensagem(erro.message); }
}

async function usarCargaHistorica(numero) {
  try {
    selecionar('#cargaObrigatoria').checked = false;
    estado.planejamentoEdicao = null; atualizarContextoPlanejamento();
    const carga = await consultarApi(`/api/carga/${encodeURIComponent(numero)}`);
    selecionar('#identificacao').value = `Simulação da carga ${carga.numero}`;
    selecionar('#peso').value = carga.peso > 0 ? carga.peso : '';
    selecionar('#frete').value = '';
    selecionarLocal('origem', carga.localOrigem);
    selecionarLocal('destino', carga.localDestino);
    if (!carga.localOrigem) selecionar('#busca-origem').value = carga.origem;
    if (!carga.localDestino) selecionar('#busca-destino').value = carga.destino;
    selecionar('#dialogoHistorico').close();
    mostrarMensagem(`Carga ${carga.numero} selecionada para uma nova simulação. Confira peso, locais e horário. ${carga.revisar ? 'Há documentos repetidos ou etapas que exigem revisão; o peso da primeira linha foi mantido sem somar.' : ''} O horário histórico não foi usado como disponibilidade atual.`, true);
    invalidarResultado();
  } catch (erro) { mostrarMensagem(erro.message); }
}

async function importarBases(arquivos) {
  if (!arquivos.length || processamentoImportacao.ativo) return;
  const botao = selecionar('#abrirImportacao'); botao.disabled = true;
  let quantidade = 0;
  const avisosImportacao = [];
  try {
    iniciarProcessamentoImportacao(arquivos);
    await new Promise(requestAnimationFrame);
    for (const [indice, arquivo] of Array.from(arquivos).entries()) {
      atualizarEtapaImportacao('Lendo arquivo', indice);
      if (arquivo.size > 17 * 1024 * 1024) throw new Error('Cada arquivo deve ter no máximo 17 MB.');
      const conteudo = await new Promise((resolver, rejeitar) => { const leitor = new FileReader(); leitor.onload = () => resolver(String(leitor.result).split(',')[1]); leitor.onerror = () => rejeitar(new Error('Não foi possível ler o arquivo.')); leitor.readAsDataURL(arquivo); });
      atualizarEtapaImportacao('Validando planilha e gravando no banco');
      const resumo = await consultarApi('/api/importar', { nome: arquivo.name, conteudo });
      quantidade++;
      avisosImportacao.push(...(resumo.avisosImportacao || []));
      atualizarEtapaImportacao('Atualizando dados históricos');
      await carregarMediasAtendimento();
      estado.ajustes = {};
      estado.locais = {};
      estado.planejamentoEdicao = null; atualizarContextoPlanejamento();
      ['origem', 'destino', 'liberacao'].forEach(campo => selecionarLocal(campo, null));
      atualizarEtapaImportacao('Atualizando composições e previsões de disponibilidade');
      await atualizarResumo(resumo, true);
      registrarTempoImportacao(arquivo);
    }
    invalidarResultado();
    trocarPagina('bases');
    mostrarMensagem(`${quantidade} base(s) importada(s). Tempo total: ${formatarTempoProcessamento(performance.now()-processamentoImportacao.inicio)}. ${avisosImportacao.join(' ')}`, true);
  } catch (erro) { mostrarMensagem(`${quantidade} base(s) importada(s). ${erro.message} ${avisosImportacao.join(' ')}`); }
  finally { encerrarProcessamentoImportacao(); botao.disabled = false; selecionar('#arquivosBases').value = ''; }
}

function exportarResultado() {
  if (!estado.resultado || estado.resultadosObsoletos) return;
  const resultado = estado.resultado;
  const linhas = [['Carga', 'Referência', 'Coleta', 'Posição', 'Tração', 'Carreta', 'Distância km', 'Chegada', 'Folga h', 'Nota', 'Peso distância', 'Peso prazo', 'Peso faturamento', 'Velocidade km/h', 'Acréscimo %', 'Avisos']];
  resultado.aprovadas.forEach((item, indice) => linhas.push([resultado.carga.identificacao, resultado.carga.referencia, resultado.carga.coleta, indice + 1, item.placa, item.carreta, item.distancia.toFixed(2), item.chegada, item.folga.toFixed(2), item.nota.toFixed(2), resultado.pesos.distancia.toFixed(4), resultado.pesos.prazo.toFixed(4), resultado.pesos.faturamento.toFixed(4), resultado.parametros.velocidade, resultado.parametros.acrescimo, [...item.avisos, ...resultado.avisos].join(' / ')]));
  linhas[0].push('Cenário operacional', 'Combustível deslocamento R$', 'Preço R$/litro', 'Consumo km/l', 'Peso segurança', 'Nota segurança');
  linhas.slice(1).forEach((linha, indice) => linha.push(resultado.parametros.cenario,
    resultado.custosCombustivel?.[resultado.aprovadas[indice].placa] ?? '', resultado.parametros.precoCombustivel ?? '', resultado.parametros.consumoMedio ?? '', resultado.pesos.seguranca ?? '', resultado.aprovadas[indice].notas.seguranca ?? ''));
  const csv = linhas.map(linha => linha.map(valor => { const texto = String(valor ?? ''); return `"${(/^[=+@-]/.test(texto) ? "'" + texto : texto).replaceAll('"', '""')}"`; }).join(';')).join('\r\n');
  const endereco = URL.createObjectURL(new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a'); link.href = endereco; link.download = 'resultado-priorizacao.csv'; link.click(); setTimeout(() => URL.revokeObjectURL(endereco), 1000);
}

function registrarEventos() {
  selecionarTodos('[data-pagina]').forEach(botao => botao.addEventListener('click', () => recarregarMenu(botao.dataset.pagina)));
  selecionarTodos('[data-fechar]').forEach(botao => botao.addEventListener('click', () => selecionar(`#${botao.dataset.fechar}`).close()));
  selecionarTodos('.grupo-local').forEach(grupo => {
    const campo = grupo.dataset.campo;
    renderizarDetalheLocal(campo);
    selecionar(`#busca-${campo}`).addEventListener('input', () => {
      estado.locais[campo] = null; renderizarDetalheLocal(campo);
      estado.sequencias[campo] = (estado.sequencias[campo] || 0) + 1;
      if (campo !== 'liberacao' && !campo.startsWith('edicao-')) { atualizarTemposAtendimento(); invalidarResultado(); }
      clearTimeout(estado.temporizadores[campo]);
      estado.temporizadores[campo] = setTimeout(() => buscarLocais(campo), 220);
    });
    selecionar(`#busca-${campo}`).addEventListener('keydown', evento => {
      const sugestoes = selecionar(`#sugestoes-${campo}`);
      if (evento.key === 'Escape') { sugestoes.hidden = true; evento.target.setAttribute('aria-expanded', 'false'); }
      if (evento.key === 'ArrowDown' && !sugestoes.hidden) { evento.preventDefault(); sugestoes.querySelector('button')?.focus(); }
      if (evento.key === 'Enter' && !sugestoes.hidden) { evento.preventDefault(); sugestoes.querySelector('button')?.click(); }
    });
  });
  document.addEventListener('click', evento => {
    const coordenadas = evento.target.closest('[data-coordenadas]');
    if (coordenadas) abrirCoordenadas(coordenadas.dataset.coordenadas);
    const liberacao = evento.target.closest('[data-liberacao]');
    if (liberacao) abrirLiberacao(liberacao.dataset.liberacao);
    const agendamento = evento.target.closest('[data-agendamento]');
    if (agendamento) abrirAgendamento(agendamento.dataset.agendamento);
    const previsao = evento.target.closest('[data-previsao]');
    if (previsao) abrirEtapasLiberacao(previsao.dataset.previsao);
    const detalhe = evento.target.closest('[data-detalhe]');
    if (detalhe) abrirDetalhes(Number(detalhe.dataset.detalhe));
    const carga = evento.target.closest('[data-carga]');
    if (carga) usarCargaHistorica(carga.dataset.carga);
    const editarPlanejada = evento.target.closest('[data-editar-planejada]');
    const verReserva = evento.target.closest('[data-ver-reserva]');
    if (verReserva) abrirReservaPlanejada(verReserva.dataset.verReserva);
    if (editarPlanejada) abrirCargaPlanejada(editarPlanejada.dataset.editarPlanejada, false);
    const avaliarPlanejada = evento.target.closest('[data-avaliar-planejada]');
    if (avaliarPlanejada) abrirCargaPlanejada(avaliarPlanejada.dataset.avaliarPlanejada, true);
    const cancelarPlanejada = evento.target.closest('[data-cancelar-planejada]');
    if (cancelarPlanejada) abrirCancelamentoCarga(cancelarPlanejada.dataset.cancelarPlanejada);
    selecionarTodos('.grupo-local').forEach(grupo => { if (!grupo.contains(evento.target)) { grupo.querySelector('.sugestoes').hidden = true; grupo.querySelector('input').setAttribute('aria-expanded', 'false'); } });
  });
  selecionar('#formularioCarga').addEventListener('submit', simularCarga);
  selecionar('#tabelaPlanejamento').addEventListener('change', evento => {
    const id = evento.target.dataset.selecionarReserva;
    if (!id) return;
    if (evento.target.checked) estado.selecaoReservas.add(id); else estado.selecaoReservas.delete(id);
    atualizarSelecaoReservas();
  });
  selecionar('#selecionarReservasVisiveis').addEventListener('change', evento => {
    estado.selecaoReservas = new Set(evento.target.checked ? estado.reservasVisiveis : []);
    renderizarPlanejamento();
  });
  selecionar('#cancelarReservasLote').addEventListener('click', abrirCancelamentoReservasLote);
  selecionar('#confirmarCancelamentoLote').addEventListener('click', cancelarReservasLote);
  selecionar('#cancelarReservaPlanejada').addEventListener('click', () => {
    const reserva = estado.reservaEmConsulta;
    if (!reserva) return;
    selecionar('#descricaoCancelarReserva').textContent = 'Liberar ' + reserva.placa + ' e ' + reserva.carreta + ' da carga ' + reserva.identificacao + '? As demais reservas não serão alteradas.';
    selecionar('#erroCancelamentoReserva').hidden = true;
    selecionar('#dialogoCancelarReserva').showModal();
  });
  selecionar('#confirmarCancelamentoReserva').addEventListener('click', cancelarReservaPlanejada);
  selecionar('#formularioEdicaoCarga').addEventListener('submit', salvarEdicaoCarga);
  selecionar('#dialogoEdicaoCarga').addEventListener('close', () => {
    for (const campo of ['edicao-origem', 'edicao-destino']) {
      clearTimeout(estado.temporizadores[campo]);
      estado.sequencias[campo] = (estado.sequencias[campo] || 0) + 1;
    }
  });
  for (const tipo of ['Carga', 'Descarga']) selecionar('#edicaoHistorico' + tipo).addEventListener('change', () => {
    selecionar('#edicaoTempo' + tipo).disabled = selecionar('#edicaoHistorico' + tipo).checked;
  });
  selecionar('#salvarCargaPrevista').addEventListener('click', salvarCargaPrevista);
  selecionar('#novaCargaPrevista').addEventListener('click', novaCargaPrevista);
  selecionar('#novaCargaFormulario').addEventListener('click', novaCargaPrevista);
  selecionar('#atualizarPlanejamento').addEventListener('click', carregarPlanejamento);
  selecionar('#confirmarCancelamentoCarga').addEventListener('click', cancelarCargaPrevista);
  for (const id of ['filtroPlanejamento', 'situacaoPlanejamento', 'coletaPlanejamentoDe', 'coletaPlanejamentoAte']) selecionar('#' + id).addEventListener('input', renderizarPlanejamento);
  selecionar('#limparFiltrosPlanejamento').addEventListener('click', () => {
    selecionar('#filtroPlanejamento').value = ''; selecionar('#situacaoPlanejamento').value = 'PENDENTE';
    selecionar('#coletaPlanejamentoDe').value = ''; selecionar('#coletaPlanejamentoAte').value = ''; renderizarPlanejamento();
  });
  selecionar('#formularioCarga').addEventListener('input', invalidarResultado);
  for (const tipo of ['carga', 'descarga']) selecionar(`#historico-${tipo}`).addEventListener('change', atualizarTemposAtendimento);
  selecionar('#carregarExemplo').addEventListener('click', carregarExemplo);
  selecionar('#filtroFrota').addEventListener('input', renderizarFrota);
  selecionar('#somenteVazios').addEventListener('change', renderizarFrota);
  selecionar('#recalcularPrevisoes').addEventListener('click', atualizarPrevisoes);
  selecionar('#formularioLiberacao').addEventListener('submit', evento => {
    evento.preventDefault();
    const local = estado.locais.liberacao;
    if (!local || local.latitude == null || local.longitude == null) { mostrarMensagem('Selecione um local de liberação com coordenadas.'); return; }
    estado.ajustes[estado.placaEdicao] = { ...estado.ajustes[estado.placaEdicao], disponivelEm: selecionar('#disponivelEm').value, local };
    selecionar('#dialogoLiberacao').close(); atualizarPrevisoes(); invalidarResultado();
    mostrarMensagem('Previsão registrada somente para esta sessão de simulação.', true);
  });
  selecionar('#removerLiberacao').addEventListener('click', () => {
    const ajuste = estado.ajustes[estado.placaEdicao];
    if (ajuste) { delete ajuste.disponivelEm; delete ajuste.local; }
    selecionar('#dialogoLiberacao').close(); atualizarPrevisoes(); invalidarResultado();
  });
  selecionar('#formularioAgendamento').addEventListener('submit', evento => {
    evento.preventDefault();
    const carga = selecionar('#cargaAgendadaEm'), descarga = selecionar('#descargaAgendadaEm');
    estado.ajustes[estado.placaEdicao] = { ...estado.ajustes[estado.placaEdicao],
      cargaAgendadaEm: carga.disabled ? null : carga.value || null,
      descargaAgendadaEm: descarga.disabled ? null : descarga.value || null };
    selecionar('#dialogoAgendamento').close(); invalidarResultado(); atualizarPrevisoes();
    mostrarMensagem('Agendamento aplicado nesta sessão. Ao avaliar a carga, os horários ficam registrados com a simulação no PostgreSQL.', true);
  });
  selecionar('#removerAgendamento').addEventListener('click', () => {
    const ajuste = estado.ajustes[estado.placaEdicao];
    if (ajuste) { delete ajuste.cargaAgendadaEm; delete ajuste.descargaAgendadaEm; }
    selecionar('#dialogoAgendamento').close(); invalidarResultado(); atualizarPrevisoes();
  });
  selecionar('#formularioCoordenadas').addEventListener('submit', evento => {
    evento.preventDefault();
    const latitude = Number(selecionar('#latitudeManual').value), longitude = Number(selecionar('#longitudeManual').value);
    if (latitude === 0 && longitude === 0) { mostrarMensagem('O ponto 0, 0 não representa um local operacional válido.'); return; }
    selecionarLocal(estado.campoCoordenadas, { nome: selecionar('#nomeCoordenadas').value, cidade: 'Ponto manual', latitude, longitude, fonte: 'manual' });
    selecionar('#dialogoCoordenadas').close();
  });
  selecionar('#formularioParametros').addEventListener('submit', aplicarParametros);
  selecionar('#formularioParametros').addEventListener('input', invalidarResultado);
  selecionar('#buscarHistorico').addEventListener('click', () => { selecionar('#dialogoHistorico').showModal(); buscarCargas(); });
  selecionar('#filtroCargas').addEventListener('input', () => { clearTimeout(estado.temporizadores.cargas); estado.temporizadores.cargas = setTimeout(buscarCargas, 220); });
  selecionar('#abrirImportacao').addEventListener('click', () => selecionar('#arquivosBases').click());
  selecionar('#arquivosBases').addEventListener('change', evento => importarBases([...evento.target.files]));
  selecionar('#recarregarBases').addEventListener('click', async () => {
    try {
      selecionar('#recarregarBases').disabled = true;
      const resumo = await consultarApi('/api/recarregar', {});
      await carregarMediasAtendimento();
      estado.ajustes = {}; estado.planejamentoEdicao = null; atualizarContextoPlanejamento();
      ['origem', 'destino', 'liberacao'].forEach(campo => selecionarLocal(campo, null));
      atualizarResumo(resumo, true); invalidarResultado(); mostrarMensagem('Bases relidas. Confira novamente os locais da carga e as previsões.', true);
    } catch (erro) { mostrarMensagem(erro.message); }
    finally { selecionar('#recarregarBases').disabled = false; }
  });
  selecionar('#exportarResultado').addEventListener('click', exportarResultado);
  window.addEventListener('resize', desenharPosicoes);
}

function atualizarRodape() {
  selecionar('#rodapeParametros').textContent = `${selecionar('#velocidade').value} km/h / Haversine + ${selecionar('#acrescimo').value}%`;
}

async function iniciarAplicacao() {
  registrarEventos(); atualizarIcones();
  try { await carregarParametrosCombustivel(); await carregarMediasAtendimento(); atualizarResumo(await consultarApi('/api/resumo')); await carregarPlanejamento(); }
  catch (erro) { mostrarMensagem(`Falha ao carregar a base: ${erro.message}`); }
}

iniciarAplicacao();
