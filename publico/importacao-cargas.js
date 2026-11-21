let pedidoImportacaoCargas = null;
selecionar('#importarCargasPrevistas').addEventListener('click', () => selecionar('#arquivoCargasPrevistas').click());
selecionar('#arquivoCargasPrevistas').addEventListener('change', async evento => {
  const arquivo = evento.target.files[0]; evento.target.value = '';
  if (!arquivo) return;
  const botao = selecionar('#importarCargasPrevistas'); botao.disabled = true;
  pedidoImportacaoCargas = null;
  try {
    if (arquivo.size > 10_000_000) throw new Error('Selecione uma planilha com no máximo 10 MB.');
    const conteudo = await new Promise((resolver, rejeitar) => {
      const leitor = new FileReader(); leitor.onload = () => resolver(String(leitor.result).split(',')[1]);
      leitor.onerror = () => rejeitar(new Error('Não foi possível ler a planilha.')); leitor.readAsDataURL(arquivo);
    });
    const pedido = {nome: arquivo.name, conteudo, referencia: selecionar('#referencia').value};
    const previa = await consultarApi('/api/planejamento/importacao/conferir', pedido);
    pedidoImportacaoCargas = pedido;
    selecionar('#resumoImportacaoCargas').textContent = `${previa.novas} novas / ${previa.existentes} já cadastradas / ${previa.erros} com erro. Referência: ${formatarData(pedido.referencia)}.`;
    selecionar('#previaImportacaoCargas').innerHTML = `<table><thead><tr><th>Linha / carga</th><th>Origem / destino</th><th>Coleta / peso</th><th>Conferencia</th></tr></thead><tbody>${previa.linhas.map(item => `<tr><td>${item.linha} / ${escaparHtml(item.identificacao)}</td><td>${item.carga ? `${escaparHtml(item.carga.origem.nome)}<small>${escaparHtml(item.carga.origem.cidade)}</small>${escaparHtml(item.carga.destino.nome)}<small>${escaparHtml(item.carga.destino.cidade)}</small>` : '--'}</td><td>${item.carga ? `${formatarData(item.carga.coleta)}<small>${formatoNumero.format(item.carga.peso)} kg</small>` : '--'}</td><td>${escaparHtml(item.situacao)}${item.erro ? `<small>${escaparHtml(item.erro)}</small>` : ''}${item.avisos.map(aviso => `<small>${escaparHtml(aviso)}</small>`).join('')}</td></tr>`).join('')}</tbody></table>`;
    selecionar('#confirmarImportacaoCargas').disabled = previa.erros > 0 || previa.novas === 0;
    selecionar('#dialogoImportacaoCargas').showModal(); atualizarIcones();
  } catch (erro) { mostrarMensagem(erro.message); } finally { botao.disabled = false; }
});
selecionar('#confirmarImportacaoCargas').addEventListener('click', async () => {
  if (!pedidoImportacaoCargas) return;
  const botao = selecionar('#confirmarImportacaoCargas'); botao.disabled = true;
  let importadas = false;
  try {
    if (selecionar('#referencia').value !== pedidoImportacaoCargas.referencia) throw new Error('A referência mudou. Selecione a planilha novamente para conferir.');
    const resultado = await consultarApi('/api/planejamento/importacao', pedidoImportacaoCargas);
    importadas = true; pedidoImportacaoCargas = null;
    selecionar('#dialogoImportacaoCargas').close(); invalidarResultado();
    await carregarPlanejamento(); await trocarPagina('cenarios');
    selecionarCargasCenario(resultado.cargas);
    // A carga permanece salva e selecionada se o calculo falhar; nao e necessario reimportar.
    const pedido = {cargas: resultado.cargas, referencia: selecionar('#referencia').value, parametros: obterParametros(), ajustes: estado.ajustes};
    const cenario = await consultarApi('/api/cenarios', pedido); estado.pedidoCenario = pedido;
    await carregarCenarios(); exibirCenario(cenario);
    mostrarMensagem(`${resultado.cargas.length} cargas importadas e avaliadas. Reservas ainda não confirmadas.`, true);
  } catch (erro) {
    mostrarMensagem(importadas ? `Cargas salvas. Não foi possível sugerir placas: ${erro.message} Selecione as cargas em Cenários para comparar novamente.` : erro.message);
  } finally { botao.disabled = false; }
});
