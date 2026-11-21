# Prioriza

Prototipo academico de apoio a decisao para planejamento de cargas, desenvolvido em Java 21, Spring Boot, PostgreSQL, HTML, CSS e JavaScript.

Esta distribuicao publica nao inclui banco de dados preenchido, senhas, planilhas operacionais, resultados da transportadora ou o prototipo anterior. Os identificadores dos testes e todos os exemplos de demonstracao sao ficticios. Os documentos numericos dos testes foram construidos com digitos verificadores intencionalmente invalidos; nao sao cadastros de empresas. As coordenadas dos exemplos representam referencias urbanas, nao estabelecimentos ou rastreamento real.

## Requisitos

- JDK 21 e Maven 3.9 ou superior, disponiveis em `JAVA_HOME` e no `PATH`.
- PostgreSQL 18 em banco exclusivo de demonstracao, ou Docker Compose para criar esse banco.
- Navegador web. Node.js 20 ou superior e Playwright sao necessarios apenas para os testes de interface.

Nao e necessario copiar executaveis, caches ou ferramentas de outra instalacao. O Maven baixa as dependencias declaradas em `pom.xml`.

## Configurar o banco

1. Copie `.env.example` para `.env` e preencha `BANCO_SENHA` com uma senha escolhida para sua instalacao. Nao publique `.env`.
2. Com Docker disponivel, execute `docker compose up -d --wait`. O banco de demonstracao usa a porta local `55433` e o nome `prioriza_demo`.
3. Sem Docker, crie um banco vazio e um usuario proprio no PostgreSQL. Ajuste `BANCO_URL`, `BANCO_USUARIO` e `BANCO_SENHA` no `.env`.

As migracoes em `src/main/resources/db/migration/` criam automaticamente as tabelas quando a aplicacao inicia. Esses arquivos contem estrutura e valores tecnicos iniciais, nao uma exportacao da operacao. Use somente um banco de demonstracao; testes e importacoes nao devem apontar para um banco operacional.

## Executar no Windows

```powershell
.\gerar-demonstracao.ps1
.\iniciar.ps1 -Compilar -Demonstracao
```

Acesse `http://127.0.0.1:8766`. A porta pode ser alterada com `-Porta 8767` ou `PORTA_APLICACAO`.

O gerador cria cinco planilhas ficticias em `target/demonstracao/bases/`, com clientes Alfa e Beta, tres composicoes SIDER e tempos historicos artificiais. Os horarios de referencia sao calculados na geracao. As cargas futuras de exemplo ficam em `target/demonstracao/cargas-previstas.xlsx`; importe esse arquivo em Cargas previstas para testar Cenarios.

As bases sao carregadas apenas no primeiro inicio de um banco vazio. Gerar novamente os arquivos nao substitui automaticamente dados ja importados. Para atualizar uma demonstracao existente, importe as planilhas pela interface. Para um novo ensaio, configure outro banco vazio. Nao apague volumes ou bancos que contenham informacoes que precisam ser preservadas.

Sem dados de demonstracao, execute `.\iniciar.ps1 -Compilar` e utilize os cadastros e as importacoes para alimentar a aplicacao.

## Executar em outros ambientes

Defina as variaveis de ambiente `BANCO_URL`, `BANCO_USUARIO`, `BANCO_SENHA` e, opcionalmente, `PORTA_APLICACAO` e `PASTA_BASES`. Execute a partir da raiz do projeto:

```text
mvn clean package
java -jar target/prioriza-0.2.0.jar
```

Para usar os exemplos, defina `PASTA_BASES=./target/demonstracao/bases`. O Java nao le `.env` automaticamente; o carregamento desse arquivo e feito pelo iniciador PowerShell. A pasta `publico/` precisa permanecer junto a raiz de execucao, pois o servidor fornece seus arquivos diretamente.

## Testes

### Regras e importacoes

```text
mvn test
```

Os testes de regras utilizam dados ficticios e simulacoes de dependencias. Tambem geram arquivos temporarios de exemplo em `target/`, pasta ignorada pelo Git. Os testes de banco sao opcionais e ficam ignorados quando `PRIORIZA_TESTE_URL` nao esta definida.

### Integracao com PostgreSQL

Em um banco exclusivo de testes, defina `PRIORIZA_TESTE_URL`, `PRIORIZA_TESTE_USUARIO` e `PRIORIZA_TESTE_SENHA` e execute `mvn test`. Os testes criam um esquema de nome aleatorio, aplicam as migracoes e removem somente esse esquema ao final. O usuario precisa de permissao para criar esquemas. Nao utilize credenciais operacionais.

### Interface

Com a demonstracao em execucao:

```text
pnpm install --frozen-lockfile
pnpm exec playwright install chromium
pnpm test
```

O endereco padrao e `http://127.0.0.1:8766`, configuravel por `ENDERECO_PRIORIZA`. Opcionalmente, `NAVEGADOR_EXECUTAVEL` permite selecionar um navegador Chromium ja instalado. Os testes de interface interceptam as gravacoes e utilizam respostas ficticias; eles nao confirmam reservas no banco. As consultas de resumo e disponibilidade usam a demonstracao em execucao. Capturas e resultados ficam em `test-results/` e nao entram no repositório.

## Estrutura

- `src/main/java/`: modelos, controladores, servicos e repositorios.
- `src/main/resources/db/migration/`: estrutura versionada do banco.
- `src/test/java/`: testes Java e gerador de dados ficticios.
- `publico/`: interface e bibliotecas locais utilizadas.
- `testes/`: validacao da interface com dados ficticios.
- `.env.example`: modelo de configuracao, sem senha.
- `compose.yaml`: banco opcional de demonstracao.

## Limites do prototipo

As distancias usam Haversine com acrescimo percentual; nao sao rotas rodoviarias. A velocidade media, os tempos historicos e os parametros geram estimativas sem fila, restricoes viarias ou pausas de jornada. A ponderacao dinamica e inspirada em atencao e usa regras explicitas, sem rede neural treinada. A distribuicao de varias cargas nao garante otimo global. A estimativa de combustivel nao equivale ao custo total ou lucro. O mapa-base do OpenStreetMap requer internet; os calculos permanecem locais.

O servidor e configurado para escutar apenas em `127.0.0.1`. Nao ha autenticacao ou preparacao para exposicao publica do portal. Repositorio publico de codigo nao significa servidor publicamente acessivel.

## Privacidade e dependencias

Nao adicione planilhas reais, banco preenchido, dumps, capturas identificaveis, logs ou credenciais. Alterar apenas nomes nao torna uma base anonima: placas, documentos, coordenadas, horarios e rotas tambem podem identificar a operacao.

Os avisos de licenca dos icones e do Leaflet estao preservados em `publico/LICENCA-ICONES.txt` e `publico/bibliotecas/leaflet-1.9.4/LICENSE`. As dependencias Java e de testes estao declaradas em seus respectivos manifestos. Esta entrega nao define uma nova licenca para o codigo autoral do projeto.
