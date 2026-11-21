# Validacao da versao publica

Validacao realizada em 21/11/2026, com Java 21, PostgreSQL 18 e navegador Chromium, exclusivamente com dados ficticios e banco separado da operacao.

## Resultados

- Compilacao e empacotamento Maven: aprovados.
- 128 testes Java: aprovados, sem falhas, erros ou testes ignorados nesta execucao. Incluem integracao com PostgreSQL.
- Sete migracoes aplicadas a banco e esquemas de teste vazios.
- Demonstracao: tres composicoes SIDER e medias historicas artificiais carregadas.
- Fluxo HTTP real: conferencia e importacao de duas cargas, simulacao, distribuicao sem repetir cavalo ou carreta, confirmacao de reservas, exclusao de placas reservadas das alternativas elegiveis e cancelamento em lote sem excluir as cargas.
- Persistencia dos parametros de combustivel e consumo: confirmada por gravacao e nova consulta.
- Interface: ranking, justificativas, limpeza ao trocar menus, modos operacionais, invalidacao e recalculo, reservas, cancelamento em lote e restauracao dos parametros apos recarregar a pagina. Gravacoes interceptadas neste teste de interface; a persistencia real foi exercitada separadamente pelo fluxo HTTP.
- Layout conferido em larguras de 1280, 1440 e 1920 pixels, sem transbordamento horizontal. Captura de demonstracao conferida visualmente.
- Auditoria dos arquivos candidatos ao Git: sem configuracoes privadas ou arquivos operacionais identificados pelos controles automaticos.

## Reproducao

Consulte LEIA-ME.md para requisitos, geracao dos exemplos, testes Java e testes da interface. Execute `pnpm auditar` para repetir a auditoria dos arquivos candidatos ao Git; Git deve estar instalado e disponivel.

Os arquivos em `target/`, `node_modules/` e `test-results/` sao gerados localmente e ignorados pelo Git. Os exemplos XLSX sao gerados a partir do codigo de teste, nao distribuidos como bases operacionais. A configuracao de validacao e o banco utilizado nesta verificacao nao integram o repositorio.

## Limites da verificacao

O arquivo Compose foi preparado, mas sua inicializacao com Docker nao foi executada nesta validacao. O PostgreSQL foi executado diretamente em instalacao local isolada. Nao foi realizado teste de instalacao em outro computador ou sistema operacional.

Os testes verificam os comportamentos descritos, nao comprovam ganhos operacionais em producao nem substituem uma revisao de seguranca. A auditoria automatica nao garante ausencia de qualquer informacao sensivel em futuras alteracoes: revise tambem novos documentos, imagens e historico de commits antes de publicar.

Nenhum envio para repositorio remoto ou publicacao do portal foi realizado. O servidor continua restrito ao computador local e nao possui autenticacao.
