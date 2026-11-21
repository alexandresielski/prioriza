package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Modelos.*;
import br.com.tcc.prioriza.modelo.Cadastros.DadosCliente;
import br.com.tcc.prioriza.repositorio.RepositorioOperacional;
import br.com.tcc.prioriza.repositorio.RepositorioOperacional.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import static br.com.tcc.prioriza.servico.FuncoesDados.*;

@Service
public class ImportadorPlanilhas {
    private final RepositorioOperacional repositorio;
    private final MotorRecomendacao motor;
    private final ObjectMapper conversor;
    private final Path pastaBases;
    private static final Map<String, List<String>> CABECALHOS = Map.of(
            "RELACAOCLIENTESCOORDENADAS", List.of("Latitude", "Longitude", "Município"),
            "RELACAOVEICULOS", List.of("Placa", "Tara", "Cap. PBT", "Número de Eixos"),
            "RELACAOPOSICAOATUAL", List.of("Placa tração", "S. carreta", "Latitude"),
            "RELACAOCARGAS", List.of("Nº carga", "Coleta", "Entrega"),
            "RELACAOVIAGENS", List.of("Nº Viagem", "Frete Total", "Remetente", "Destinatário", "Carregamento", "Destino", "Grupo", "Tempo Carga", "Tempo Descarga"));

    public ImportadorPlanilhas(RepositorioOperacional repositorio, MotorRecomendacao motor, ObjectMapper conversor,
            @Value("${prioriza.pasta-bases}") String pastaBases) {
        this.repositorio = repositorio; this.motor = motor; this.conversor = conversor;
        this.pastaBases = Path.of(pastaBases).toAbsolutePath().normalize();
    }
    public String obterPastaBases() { return pastaBases.toString(); }

    @Transactional(rollbackFor = Exception.class)
    public void atualizarIdentificadoresClientes() throws IOException {
        byte[] arquivo = repositorio.obterArquivoClientes();
        if (arquivo != null) repositorio.atualizarNomesClientes(prepararLocais(lerPlanilha(arquivo, 0)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void atualizarMediasIniciais() throws IOException {
        byte[] arquivo = repositorio.obterViagensSemMedias();
        if (arquivo == null) return;
        repositorio.substituirMedias(TemposHistoricos.calcularMedias(lerPlanilha(arquivo, 1)));
        repositorio.marcarMediasAtualizadas();
    }

    @Transactional(rollbackFor = Exception.class)
    public void carregarBasesIniciais() throws IOException {
        if (repositorio.contarImportacoes() != 0) return;
        carregarArquivos();
    }

    @Transactional(rollbackFor = Exception.class)
    public void recarregarBases() throws IOException { carregarArquivos(); }

    private void carregarArquivos() throws IOException {
        Map<String, Path> arquivos = new TreeMap<>();
        for (Path pasta : List.of(pastaBases)) {
            if (!Files.isDirectory(pasta)) continue;
            try (var caminhos = Files.list(pasta)) {
                caminhos.filter(caminho -> caminho.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
                        .forEach(caminho -> arquivos.put(obterTipo(caminho.getFileName().toString()), caminho));
            }
        }
        for (String tipo : List.of("RELACAOCLIENTESCOORDENADAS", "RELACAOVEICULOS", "RELACAOPOSICAOATUAL", "RELACAOCARGAS", "RELACAOVIAGENS")) {
            Path caminho = arquivos.get(tipo);
            if (caminho != null) importar(caminho.getFileName().toString(), Files.readAllBytes(caminho));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public List<String> importar(String nome, byte[] conteudo) throws IOException {
        String tipo = obterTipo(nome);
        if (!CABECALHOS.containsKey(tipo)) throw new IllegalArgumentException("Nome de arquivo desconhecido. Utilize os nomes das bases do projeto.");
        if (conteudo.length > 17 * 1024 * 1024) throw new IllegalArgumentException("Cada arquivo deve ter no máximo 17 MB.");
        List<Map<String, Object>> registros = lerPlanilha(conteudo, tipo.equals("RELACAOVIAGENS") ? 1 : 0);
        if (registros.isEmpty() || !registros.getFirst().keySet().containsAll(CABECALHOS.get(tipo))) throw new IllegalArgumentException("Layout diferente do esperado ou planilha vazia.");
        List<String> avisos = new ArrayList<>();
        switch (tipo) {
            case "RELACAOCLIENTESCOORDENADAS" -> avisos.addAll(Optional.ofNullable(repositorio.substituirLocais(prepararLocais(registros))).orElse(List.of()));
            case "RELACAOVEICULOS" -> avisos.addAll(Optional.ofNullable(repositorio.substituirVeiculos(registros)).orElse(List.of()));
            case "RELACAOPOSICAOATUAL" -> repositorio.substituirPosicoes(prepararPosicoes(registros, avisos));
            case "RELACAOCARGAS" -> repositorio.substituirCargas(consolidarCargas(registros));
            case "RELACAOVIAGENS" -> {
                repositorio.substituirViagens(registros);
                repositorio.substituirMedias(TemposHistoricos.calcularMedias(registros));
            }
            default -> throw new IllegalArgumentException("Base desconhecida.");
        }
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
            repositorio.gravarImportacao(tipo, nome, conteudo, hash, registros.size());
            if (tipo.equals("RELACAOCLIENTESCOORDENADAS")) repositorio.marcarClientesAtualizados();
            if (tipo.equals("RELACAOVIAGENS")) repositorio.marcarMediasAtualizadas();
        } catch (NoSuchAlgorithmException erro) { throw new IllegalStateException(erro); }
        return avisos;
    }

    private String obterTipo(String nome) { return normalizarTexto(nome.replaceFirst("(?i)\\.xlsx$", "")); }

    public List<Map<String, Object>> lerPlanilha(byte[] conteudo, int linhaCabecalho) throws IOException {
        try (Workbook planilha = WorkbookFactory.create(new ByteArrayInputStream(conteudo))) {
            Sheet aba = planilha.getSheetAt(0);
            Row cabecalho = aba.getRow(linhaCabecalho);
            if (cabecalho == null) throw new IllegalArgumentException("Planilha sem cabecalho.");
            DataFormatter formatador = new DataFormatter(Locale.US);
            List<String> colunas = new ArrayList<>();
            for (int coluna = 0; coluna < cabecalho.getLastCellNum(); coluna++) colunas.add(formatador.formatCellValue(cabecalho.getCell(coluna)).strip());
            List<Map<String, Object>> registros = new ArrayList<>();
            for (int indice = linhaCabecalho + 1; indice <= aba.getLastRowNum(); indice++) {
                Row linha = aba.getRow(indice);
                if (linha == null) continue;
                Map<String, Object> registro = new LinkedHashMap<>();
                boolean preenchido = false;
                for (int coluna = 0; coluna < colunas.size(); coluna++) {
                    Cell celula = linha.getCell(coluna);
                    Object valor = lerCelula(celula);
                    if (!texto(valor).isBlank()) preenchido = true;
                    registro.put(colunas.get(coluna), valor);
                }
                if (preenchido) registros.add(registro);
            }
            return registros;
        }
    }

    private Object lerCelula(Cell celula) {
        if (celula == null) return null;
        CellType tipo = celula.getCellType() == CellType.FORMULA ? celula.getCachedFormulaResultType() : celula.getCellType();
        return switch (tipo) {
            case NUMERIC -> DateUtil.isCellDateFormatted(celula) ? celula.getLocalDateTimeCellValue() : celula.getNumericCellValue();
            case BOOLEAN -> celula.getBooleanCellValue();
            case STRING -> celula.getStringCellValue();
            default -> null;
        };
    }

    private List<RegistroLocal> prepararLocais(List<Map<String, Object>> clientes) {
        Map<String, List<Local>> pontos = new LinkedHashMap<>();
        for (Map<String, Object> cliente : clientes) {
            String cidade = texto(cliente.get("Município")) + "/" + texto(cliente.get("U.F."));
            Local ponto = new Local(null, cidade, cidade, converterNumero(cliente.get("Latitude")), converterNumero(cliente.get("Longitude")), "cliente", "");
            if (motor.validarCoordenadas(ponto) && !texto(cliente.get("Município")).isBlank() && !texto(cliente.get("U.F.")).isBlank()) pontos.computeIfAbsent(cidade, chave -> new ArrayList<>()).add(ponto);
        }
        Map<String, Local> municipios = new LinkedHashMap<>();
        for (var entrada : pontos.entrySet()) {
            String cidade = entrada.getKey();
            municipios.put(normalizarTexto(cidade), new Local("municipio-" + normalizarTexto(cidade), cidade, cidade,
                    mediana(entrada.getValue().stream().map(Local::latitude).toList()), mediana(entrada.getValue().stream().map(Local::longitude).toList()),
                    "municipio", "Referencia municipal estimada pela mediana dos pontos cadastrados"));
        }
        List<RegistroLocal> locais = new ArrayList<>();
        municipios.values().forEach(local -> locais.add(new RegistroLocal(local, List.of())));
        for (int indice = 0; indice < clientes.size(); indice++) {
            Map<String, Object> cliente = clientes.get(indice);
            String nome = texto(cliente.get("Nome fantasia"));
            if (nome.isBlank()) nome = texto(cliente.get("Razão social"));
            if (nome.isBlank()) continue;
            String cidade = texto(cliente.get("Município")) + "/" + texto(cliente.get("U.F."));
            Double latitude = converterNumero(cliente.get("Latitude")), longitude = converterNumero(cliente.get("Longitude"));
            String fonte = "cliente", detalhe = texto(cliente.get("Referência geográfica endereço padrão"));
            if (!motor.validarCoordenadas(new Local(null, nome, cidade, latitude, longitude, fonte, detalhe))) {
                Local municipio = municipios.get(normalizarTexto(cidade));
                latitude = municipio == null ? null : municipio.latitude(); longitude = municipio == null ? null : municipio.longitude();
                fonte = municipio == null ? "ausente" : "municipio"; detalhe = "Sem coordenada do cliente; referência municipal estimada pela base";
            }
            List<String> nomes = new ArrayList<>(List.of(normalizarTexto(cliente.get("Razão social")), normalizarTexto(cliente.get("Nome fantasia"))));
            for (var campo : cliente.entrySet()) if (normalizarTexto(campo.getKey()).contains("CNPJ")) {
                String cnpj = texto(campo.getValue()).replaceAll("[^0-9]", "");
                if (!cnpj.isEmpty() && cnpj.length() <= 14) nomes.add("0".repeat(14 - cnpj.length()) + cnpj);
            }
            nomes = nomes.stream().filter(valor -> !valor.isBlank()).distinct().toList();
            String cnpj=nomes.stream().filter(valor->valor.matches("[0-9]{14}")).findFirst().orElse("");
            var dados=new DadosCliente(cnpj,texto(cliente.get("Razão social")),texto(cliente.get("Nome fantasia")),texto(cliente.get("Município")),texto(cliente.get("U.F.")).toUpperCase(Locale.ROOT),
                texto(cliente.get("Referência geográfica endereço padrão")),"cliente".equals(fonte)?latitude:null,"cliente".equals(fonte)?longitude:null);
            locais.add(new RegistroLocal(new Local("cliente-" + indice, nome, cidade, latitude, longitude, fonte, detalhe), nomes,dados));
        }
        return locais;
    }

    private Double mediana(List<Double> valores) {
        List<Double> ordenados = valores.stream().sorted().toList(); int meio = ordenados.size() / 2;
        return ordenados.size() % 2 == 0 ? (ordenados.get(meio - 1) + ordenados.get(meio)) / 2 : ordenados.get(meio);
    }
    private List<PosicaoBanco> prepararPosicoes(List<Map<String, Object>> registros, List<String> avisos) {
        Map<String, PosicaoBanco> posicoes = new LinkedHashMap<>();
        Map<String, List<PosicaoBanco>> grupos = new LinkedHashMap<>();
        for (var registro : registros) {
            String placa = normalizarTexto(registro.get("Placa tração"));
            if (placa.isBlank()) throw new IllegalArgumentException("Posição sem placa de tracao. Confira a planilha antes de importar.");
            PosicaoBanco posicao = new PosicaoBanco(placa, normalizarTexto(registro.get("Placa carreta")),
                texto(registro.get("Grupo carreta")), texto(registro.get("S. carreta")), normalizarTexto(registro.get("Engatada")).equals("SIM"),
                texto(registro.get("Município referência")), converterNumero(registro.get("Latitude")), converterNumero(registro.get("Longitude")),
                converterData(registro.get("Última posição")), texto(registro.get("Origem")), texto(registro.get("Remetente")),
                texto(registro.get("Destino")), texto(registro.get("Destinatário")));
            grupos.computeIfAbsent(placa, ignorada -> new ArrayList<>()).add(posicao);
        }
        for (var entrada : grupos.entrySet()) {
            var grupo = entrada.getValue();
            LocalDateTime maisRecente = grupo.stream().map(PosicaoBanco::data).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            List<PosicaoBanco> candidatas = grupo.stream().filter(item -> Objects.equals(item.data(), maisRecente)).distinct().toList();
            if (candidatas.size() != 1) throw new IllegalArgumentException("Placa " + entrada.getKey()
                    + " tem posicoes conflitantes no mesmo horario ou sem data. Confira as linhas duplicadas; a base anterior foi preservada.");
            PosicaoBanco escolhida = candidatas.getFirst();
            posicoes.put(entrada.getKey(), escolhida);
            if (grupo.size() > 1) avisos.add("Placa " + entrada.getKey() + ": " + grupo.size()
                    + " linhas consolidadas. Mantida a linha completa da posicao "
                    + (maisRecente == null ? "sem data (duplicatas identicas)" : "mais recente, " + formatarData(maisRecente))
                    + ", situacao " + escolhida.situacao() + ", grupo " + escolhida.grupo()
                    + ", carreta " + (escolhida.carreta().isBlank() ? "não informada" : escolhida.carreta()) + ".");
        }
        return new ArrayList<>(posicoes.values());
    }

    private List<CargaHistorica> consolidarCargas(List<Map<String, Object>> registros) {
        Map<String, List<Map<String, Object>>> grupos = new LinkedHashMap<>();
        for (var registro : registros) if (!texto(registro.get("Nº carga")).isBlank()) grupos.computeIfAbsent(texto(registro.get("Nº carga")), chave -> new ArrayList<>()).add(registro);
        List<CargaHistorica> cargas = new ArrayList<>();
        for (var entrada : grupos.entrySet()) {
            var grupo = entrada.getValue(); var primeiro = grupo.getFirst();
            List<String> documentos = grupo.stream().map(registro -> texto(registro.get("Nº documento"))).distinct().sorted().toList();
            cargas.add(new CargaHistorica(entrada.getKey(), texto(primeiro.get("Coleta")), texto(primeiro.get("Entrega")),
                    texto(primeiro.get("Origem")), texto(primeiro.get("Destino")), converterNumero(primeiro.get("Peso (kg)")),
                    texto(primeiro.get("Data Carregamento Início")), texto(primeiro.get("Situação")), grupo.size(), documentos, grupo.size() > 1, null, null));
        }
        return cargas;
    }
}
