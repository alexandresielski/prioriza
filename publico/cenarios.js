estado.cenarioAtual = null;
estado.cenarios = [];
estado.cargasCenario = [];
estado.selecaoCenario = new Set();
estado.escolhasCenario = {};
estado.cenarioDesatualizado = false;
estado.cenarioOcupado = false;
estado.acaoCenario = null;
estado.cenarioVisualizacao = false;
function invalidarCenario() {
  if (!estado.cenarioAtual || estado.cenarioAtual.situacao !== 'SIMULADO') return;
  estado.cenarioDesatualizado = true;
  renderizarTabelaCenarios();
}
function cargasLivresCenario() { return estado.cargasCenario.filter(item => item.situacao === 'PENDENTE' && !item.alocada); }
function selecionarCargasCenario(ids) {
  estado.cenarioAtual = null; estado.pedidoCenario = null; estado.cenarioVisualizacao = false;
  estado.cenarioDesatualizado = false; estado.escolhasCenario = {};
  estado.selecaoCenario = new Set(ids); renderizarTabelaCenarios();
}
function limparCenario() {
  selecionar('#dialogoDetalhes').close();
  estado.versaoTelaCenario = (estado.versaoTelaCenario || 0) + 1;
  selecionar('#erroRecalculoCenario').hidden = true;
  estado.cenarioAtual = null; estado.pedidoCenario = null; estado.selecaoCenario = new Set();
  estado.escolhasCenario = {}; estado.cenarioDesatualizado = false; estado.cenarioVisualizacao = false;
  renderizarTabelaCenarios();
}
function atualizarAcoesCenario() {
  const atual = estado.cenarioAtual, ocupado = estado.cenarioOcupado || estado.menuAtualizando === 'cenarios';
  const livre = cargasLivresCenario(); const selecionadas = livre.filter(item => estado.selecaoCenario.has(item.id)).length;
  const somenteLeitura = estado.cenarioVisualizacao || (atual && atual.situacao !== 'SIMULADO');
  const botao = selecionar('#simularCenario');
  botao.disabled = ocupado || somenteLeitura || selecionadas === 0 || selecionadas > 100;
  botao.classList.toggle('comparando-cenario', ocupado && estado.acaoCenario === 'comparacao');
  botao.innerHTML = ocupado && estado.acaoCenario === 'comparacao' ? '<i data-lucide="loader-circle"></i>Comparando...' : '<i data-lucide="git-compare-arrows"></i>Comparar cargas';
  selecionar('#novaComparacaoCenario').disabled = ocupado; selecionar('#atualizarCenarios').disabled = ocupado;
  selecionar('#selecionarTodasCenario').disabled = ocupado || somenteLeitura || !livre.length;
  selecionar('#selecionarTodasCenario').checked = livre.length > 0 && selecionadas === livre.length;
  selecionar('#selecionarTodasCenario').indeterminate = selecionadas > 0 && selecionadas < livre.length;
  selecionar('#confirmarCenario').disabled = ocupado || !atual || atual.situacao !== 'SIMULADO' || estado.cenarioDesatualizado || !atual.recomendacoes.some(item => item.placa);
  selecionar('#recalcularEscolhas').disabled = ocupado || !atual || atual.situacao !== 'SIMULADO' || !estado.pedidoCenario || selecionadas === 0 || selecionadas > 100;
  selecionar('#confirmarCenario').classList.toggle('comparando-cenario', ocupado && estado.acaoCenario === 'confirmacao');
  selecionar('#confirmarCenario').innerHTML = ocupado && estado.acaoCenario === 'confirmacao' ? '<i data-lucide="loader-circle"></i>Confirmando...' : '<i data-lucide="check"></i>Confirmar reservas';
  selecionar('#resumoCenarios').textContent = somenteLeitura ? 'Cenário registrado / visualizacao das alocações.' : `${livre.length} cargas livres / ${selecionadas} selecionadas${selecionadas > 100 ? ' / limite de 100 por comparação' : ''}`;
  selecionar('#modoOperacionalCenario').disabled = ocupado || somenteLeitura;
  selecionar('#referenciaCenario').textContent = `Referência: ${formatarData(estado.cenarioVisualizacao ? estado.pedidoCenario?.referencia : selecionar('#referencia').value)}`;
  selecionar('#resultadoCenario').hidden = !atual;
  if (atual) {
    const rotulo = estado.cenarioDesatualizado && atual.situacao === 'SIMULADO' ? 'Dados alterados / recalcule antes de confirmar' : ({SIMULADO:'Proposta / reservas não confirmadas',CONFIRMADO:'Reservas confirmadas',CANCELADO:'Cenário encerrado'}[atual.situacao] || atual.situacao);
    selecionar('#situacaoCenario').textContent = rotulo;
    selecionar('#situacaoCenario').classList.toggle('proposta-obsoleta', estado.cenarioDesatualizado);
  }
  atualizarIcones();
}
function renderizarTabelaCenarios() {
  const atual = estado.cenarioAtual;
  const recomendacoes = new Map((atual?.recomendacoes || []).map(item => [item.carga,item]));
  const registros = new Map(estado.cargasCenario.map(item => [item.id,item]));
  const somenteLeitura = estado.cenarioVisualizacao || (atual && atual.situacao !== 'SIMULADO');
  const ids = somenteLeitura ? [...recomendacoes.keys()] : [...new Set([...cargasLivresCenario().map(item => item.id), ...recomendacoes.keys()])];
  ids.sort((a,b) => String(registros.get(a)?.carga.coleta || recomendacoes.get(a)?.coleta).localeCompare(String(registros.get(b)?.carga.coleta || recomendacoes.get(b)?.coleta)) || a.localeCompare(b));
  selecionar('#cargasCenario').innerHTML = ids.map(id => {
    const registro = registros.get(id), opcao = recomendacoes.get(id), carga = registro?.carga;
    const identificacao = carga?.identificacao || opcao?.identificacao || id;
    const selecionada = estado.selecaoCenario.has(id);
    const bloqueada = estado.cenarioOcupado || somenteLeitura || registro?.alocada || registro?.situacao === 'CANCELADA';
    const rota = carga ? `<div class="rota-cenario"><i data-lucide="map-pin"></i><span>${escaparHtml(carga.origem?.nome)}<small>${escaparHtml(carga.origem?.cidade)}</small></span></div><div class="rota-cenario"><i data-lucide="flag"></i><span>${escaparHtml(carga.destino?.nome || '--')}<small>${escaparHtml(carga.destino?.cidade || '')}</small></span></div>` : '<span class="sem-proposta">Trajeto indisponível neste registro</span>';
    let composicao = '<span class="sem-proposta">Aguardando comparação</span>';
    if (opcao) {
      composicao = opcao.placa ? `<strong class="placa-cenario">${escaparHtml(opcao.placa)}</strong><small>Carreta ${escaparHtml(opcao.carreta || '--')}</small>` : '<span class="selo-cenario erro"><i data-lucide="circle-x"></i>Sem composição livre</span>';
      if (atual.situacao === 'SIMULADO') {
        const alternativas = (atual.alternativas || []).filter(item => item.carga === id);
        if (alternativas.length) composicao += `<select data-escolher-cenario="${id}" aria-label="Composição da carga ${escaparHtml(identificacao)}" ${estado.cenarioOcupado ? 'disabled' : ''}><option value="">Sugestão automática</option>${alternativas.map(item => `<option value="${escaparHtml(item.placa)}" ${estado.escolhasCenario[id] === item.placa ? 'selected' : ''}>${escaparHtml(item.placa)} + ${escaparHtml(item.carreta)}</option>`).join('')}</select>`;
      }
    }
    const nota = opcao?.nota == null ? '<span class="sem-proposta">--</span>' : `<strong class="nota-cenario">${formatoNumero.format(opcao.nota)}</strong><small>de 100 / ${formatoNumero.format(opcao.distancia)} km</small><div class="escala-nota-cenario" aria-hidden="true"><span style="width:${Math.max(0,Math.min(100,opcao.nota))}%"></span></div>`;
    const folga = opcao?.folga == null ? '--' : formatarDuracao(opcao.folga);
    let situacao = '<span class="sem-proposta">Não comparada</span>';
    if (opcao) {
      const classe = estado.cenarioDesatualizado && atual.situacao === 'SIMULADO' ? 'atencao' : !opcao.placa ? 'erro' : opcao.folga < 1 ? 'atencao' : 'ok';
      const rotulo = estado.cenarioDesatualizado && atual.situacao === 'SIMULADO' ? 'Recalcular' : !opcao.placa ? 'Não atendida' : atual.situacao === 'CONFIRMADO' ? 'Reservada' : atual.situacao === 'CANCELADO' ? 'Encerrada' : opcao.folga < 1 ? 'Folga curta' : 'Viável';
      situacao = `<button type="button" class="selo-cenario ${classe}" data-justificativa-cenario="${id}" title="Ver justificativa da composição" aria-label="Ver justificativa da carga ${escaparHtml(identificacao)}"><i data-lucide="${classe === 'ok' ? 'check' : classe === 'erro' ? 'circle-x' : 'triangle-alert'}"></i>${rotulo}<i data-lucide="chevron-right"></i></button>`;
    }
    return `<tr data-carga-cenario="${id}" class="${opcao && !opcao.placa ? 'linha-sem-atendimento' : selecionada ? 'linha-selecionada' : ''}"><td><input type="checkbox" name="cargaCenario" value="${id}" aria-label="Selecionar carga ${escaparHtml(identificacao)}" ${selecionada ? 'checked' : ''} ${bloqueada ? 'disabled' : ''}></td><td><strong>${escaparHtml(identificacao)}</strong><small>${formatarData(carga?.coleta || opcao?.coleta)}</small>${carga?.obrigatoria || opcao?.obrigatoria ? '<span class="prioridade-cenario">Obrigatória</span>' : ''}</td><td>${rota}</td><td>${composicao}</td><td>${nota}</td><td><span class="folga-cenario">${folga}</span></td><td>${situacao}</td></tr>`;
  }).join('') || '<tr><td colspan="7">Nenhuma carga pendente livre.</td></tr>';
  atualizarAcoesCenario();
}
function montarJustificativaCenario(id) {
  const opcao = estado.cenarioAtual?.recomendacoes.find(item => item.carga === id);
  if (!opcao) return '';
  const antigo = estado.cenarioDesatualizado && estado.cenarioAtual.situacao === 'SIMULADO';
  if (!opcao.placa) return '<div class="avisos"><p>' + escaparHtml(opcao.motivo || 'Sem composição viável livre.') + '</p></div>';
  const avaliacao = opcao.avaliacao || {};
  const item = { ...avaliacao, ...opcao, disponivelEm: avaliacao.disponivelEm, chegada: avaliacao.chegada,
    contribuicoes: avaliacao.contribuicoes || {}, avisos: [...(avaliacao.avisos || [])] };
  if (!opcao.avaliacao) item.avisos.push('Detalhes não preservados neste registro. Compare novamente.');
  return renderizarJustificativaComposicao(item, avaliacao.pesos || {}, antigo);
}

selecionar('#cargasCenario').addEventListener('click', evento => {
  const botao = evento.target.closest('[data-justificativa-cenario]');
  if (!botao) return;
  const opcao = estado.cenarioAtual?.recomendacoes.find(item => item.carga === botao.dataset.justificativaCenario);
  if (!opcao) return;
  selecionar('#dialogoDetalhes .titulo-painel h2').textContent = 'Detalhes da recomendação';
  selecionar('#conteudoDetalhes').innerHTML = montarJustificativaCenario(opcao.carga);
  selecionar('#dialogoDetalhes').showModal();
});

async function carregarCenarios() {
  try {
    estado.cargasCenario = await consultarApi('/api/planejamento');
    estado.cenarios = await consultarApi('/api/cenarios');
    const registro = estado.cenarios.find(item => item.id === estado.cenarioAtual?.id);
    if (registro) estado.cenarioAtual.situacao = registro.situacao;
    renderizarTabelaCenarios();
    atualizarIcones();
  } catch(erro) { mostrarMensagem(erro.message); }
}
function exibirCenario(resultado, situacao = resultado.situacao, historico = false) {
  selecionar('#dialogoDetalhes').close();
  selecionar('#erroRecalculoCenario').hidden = true;
  estado.cenarioAtual = {...resultado,situacao}; estado.cenarioVisualizacao = historico;
  estado.cenarioDesatualizado = false; estado.escolhasCenario = {...(resultado.escolhas || {})};
  estado.selecaoCenario = new Set(resultado.recomendacoes.map(item => item.carga));
  const medida = resultado.comparacao[0];
  selecionar('#indicadoresCenario').innerHTML = `<div><span>Cargas atendidas</span><strong>${medida.atendidas} / ${resultado.recomendacoes.length}</strong></div><div><span>Distância vazia estimada</span><strong>${formatoNumero.format(medida.distanciaVazia)} km</strong></div><div><span>Menor folga</span><strong>${formatarDuracao(medida.menorFolga)}</strong></div>`;
  renderizarTabelaCenarios();
}
selecionar('#selecionarTodasCenario').addEventListener('change', evento => {
  estado.selecaoCenario = new Set(evento.target.checked ? cargasLivresCenario().map(item => item.id) : []);
  invalidarCenario(); renderizarTabelaCenarios();
});
selecionar('#cargasCenario').addEventListener('change', evento => {
  if (evento.target.name === 'cargaCenario') {
    if (evento.target.checked) estado.selecaoCenario.add(evento.target.value); else estado.selecaoCenario.delete(evento.target.value);
  }
  if (evento.target.matches('[data-escolher-cenario]')) {
    const id = evento.target.dataset.escolherCenario;
    if (evento.target.value) estado.escolhasCenario[id] = evento.target.value; else delete estado.escolhasCenario[id];
  }
  invalidarCenario(); renderizarTabelaCenarios();
});
selecionar('#novaComparacaoCenario').addEventListener('click', limparCenario);
selecionar('#atualizarCenarios').addEventListener('click', async () => { invalidarCenario(); await carregarCenarios(); });
async function compararCargasCenario(pedido) {
  if (estado.cenarioOcupado) return;
  const versaoTela = estado.versaoTelaCenario || 0;
  estado.cenarioOcupado = true; estado.acaoCenario = 'comparacao'; renderizarTabelaCenarios();
  selecionar('#erroRecalculoCenario').hidden = true;
  try {
    const resultado = await consultarApi('/api/cenarios', pedido);
    if (versaoTela !== (estado.versaoTelaCenario || 0)) return;
    estado.pedidoCenario = pedido;
    await carregarCenarios();
    if (versaoTela === (estado.versaoTelaCenario || 0)) exibirCenario(resultado);
  } catch (erro) {
    if (versaoTela !== (estado.versaoTelaCenario || 0)) return;
    invalidarCenario();
    selecionar('#erroRecalculoCenario').textContent = 'Recálculo não aplicado: ' + erro.message;
    selecionar('#erroRecalculoCenario').hidden = false;
    selecionar('#erroRecalculoCenario').scrollIntoView({ block: 'center', behavior: 'smooth' });
  }
  finally { estado.cenarioOcupado = false; estado.acaoCenario = null; renderizarTabelaCenarios(); }
}
function montarPedidoCenario() {
  const cargas = cargasLivresCenario().filter(item => estado.selecaoCenario.has(item.id)).map(item => item.id);
  const selecionadas = new Set(cargas);
  const escolhas = Object.fromEntries(Object.entries(estado.escolhasCenario).filter(([id]) => selecionadas.has(id)));
  return { cargas, escolhas, referencia: selecionar('#referencia').value,
    parametros: { ...obterParametros(), cenario: selecionar('#modoOperacionalCenario').value }, ajustes: estado.ajustes };
}
selecionar('#modoOperacionalCenario').addEventListener('change', () => {
  invalidarCenario(); atualizarAcoesCenario();
});
selecionar('#simularCenario').addEventListener('click', async () => {
  await compararCargasCenario(montarPedidoCenario());
});
selecionar('#confirmarCenario').addEventListener('click', async () => {
  if (estado.cenarioOcupado) return;
  estado.cenarioOcupado = true; estado.acaoCenario = 'confirmacao'; renderizarTabelaCenarios();
  try {
    await consultarApi(`/api/cenarios/${estado.cenarioAtual.id}/confirmar`, {});
    limparResultadoSimulacao();
    await conferirReservasSimulacao();
    limparCenario();
    await carregarCenarios(); mostrarMensagem('Reservas confirmadas. Cavalo e carreta bloqueados para outros cenários até liberação.', true);
  } catch(erro) { invalidarCenario(); mostrarMensagem(erro.message); }
  finally { estado.cenarioOcupado = false; estado.acaoCenario = null; renderizarTabelaCenarios(); }
});
selecionar('#recalcularEscolhas').addEventListener('click', async () => {
 if (!estado.pedidoCenario) return;
 await compararCargasCenario(montarPedidoCenario());
});
