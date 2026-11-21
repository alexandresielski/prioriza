package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Modelos.*;
import br.com.tcc.prioriza.repositorio.RepositorioOperacional;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import static br.com.tcc.prioriza.servico.FuncoesDados.*;

@Service
public class ImportadorCargasPrevistas {
    public static final List<String> COLUNAS = List.of("Carga", "Origem", "Município origem", "Destino", "Município destino", "Coleta", "Peso kg", "Frete", "Obrigatoria");
    public record Pedido(String nome, String conteudo, String referencia) { }
    public record Linha(int linha, String identificacao, String situacao, Carga carga, List<String> avisos, String erro) { }
    public record Conferencia(List<Linha> linhas, int novas, int existentes, int erros) { }
    public record Importadas(List<UUID> cargas, int existentes) { }
    private final RepositorioOperacional repositorio;
    private final ServicoPlanejamento planejamento;
    public ImportadorCargasPrevistas(RepositorioOperacional repositorio, ServicoPlanejamento planejamento) {
        this.repositorio = repositorio; this.planejamento = planejamento;
    }
    @Transactional(readOnly = true)
    public Conferencia conferir(Pedido pedido) {
        exigir(pedido != null && texto(pedido.nome()).toLowerCase(Locale.ROOT).endsWith(".xlsx"), "Selecione uma planilha .xlsx do novo modelo de cargas previstas.");
        LocalDateTime referencia = converterData(pedido.referencia());
        exigir(referencia != null, "Informe a referência da simulação.");
        exigir(pedido.conteudo() != null && pedido.conteudo().length() <= 14_000_000, "A planilha deve ter no máximo 10 MB.");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(pedido.conteudo()); }
        catch (IllegalArgumentException erro) { throw new IllegalArgumentException("Conteúdo de arquivo inválido."); }
        List<Linha> linhas = new ArrayList<>();
        Map<String, List<CargaPlanejada>> cadastradas = new HashMap<>();
        for (CargaPlanejada item : repositorio.listarPlanejamento())
            cadastradas.computeIfAbsent(normalizarTexto(item.carga().identificacao()), chave -> new ArrayList<>()).add(item);
        Set<String> identificacoes = new HashSet<>();
        try (Workbook livro = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            exigir(livro.getNumberOfSheets() > 0, "Planilha sem abas.");
            Sheet aba = livro.getSheetAt(0);
            exigir(aba.getLastRowNum() <= 1000, "O arquivo excede 1000 linhas. Importe no máximo 100 cargas por vez.");
            Row cabecalho = aba.getRow(0);
            exigir(cabecalho != null && cabecalho.getLastCellNum() <= 30, "Cabecalho ausente ou inválido.");
            Map<String, Integer> colunas = new HashMap<>();
            DataFormatter formato = new DataFormatter(Locale.forLanguageTag("pt-BR"));
            for (Cell celula : cabecalho) {
                String nome = normalizarTexto(formato.formatCellValue(celula));
                if (!nome.isBlank()) exigir(colunas.put(nome, celula.getColumnIndex()) == null, "Coluna repetida no cabecalho.");
            }
            for (String nome : COLUNAS.subList(0, 7)) exigir(colunas.containsKey(normalizarTexto(nome)), "Coluna obrigatória ausente: " + nome + ". Baixe o novo modelo.");
            for (int numero = 1; numero <= aba.getLastRowNum(); numero++) {
                Row linha = aba.getRow(numero);
                if (linha == null || COLUNAS.stream().allMatch(nome -> textoCelula(linha, colunas, nome, formato).isBlank())) continue;
                exigir(linhas.size() < 100, "Importe no máximo 100 cargas por vez.");
                String id = textoCelula(linha, colunas, "Carga", formato);
                List<String> avisos = new ArrayList<>();
                try {
                    exigir(!normalizarTexto(id).isBlank(), "Informe a identificação da carga.");
                    exigir(identificacoes.add(normalizarTexto(id)), "Identificação repetida neste arquivo. Mantenha uma linha por carga.");
                    Local origem = local(linha, colunas, "Origem", "Municipio origem", formato, avisos);
                    Local destino = local(linha, colunas, "Destino", "Municipio destino", formato, avisos);
                    LocalDateTime coleta = data(linha.getCell(colunas.get("COLETA")), formato);
                    exigir(coleta != null, "Coleta inválida. Use dd/MM/aaaa HH:mm ou data e hora do Excel.");
                    exigir(!coleta.isBefore(referencia), "Coleta anterior a referência da simulação.");
                    String obrigatoria = normalizarTexto(textoCelula(linha, colunas, "Obrigatoria", formato));
                    exigir(Set.of("", "SIM", "NAO").contains(obrigatoria), "Obrigatória deve ser Sim, Não ou vazia.");
                    Carga carga = new Carga(id, origem, destino, formatarData(referencia), formatarData(coleta),
                        numero(linha, colunas, "Peso kg", formato), numero(linha, colunas, "Frete", formato), null, null, obrigatoria.equals("SIM"));
                    planejamento.validarCarga(carga);
                    List<CargaPlanejada> anteriores = cadastradas.getOrDefault(normalizarTexto(id), List.of());
                    if (!anteriores.isEmpty()) {
                        exigir(anteriores.size() == 1 && mesmaCarga(anteriores.getFirst().carga(), carga), "Identificação já cadastrada com outros dados. Edite a carga existente ou use outra identificação.");
                        avisos.add("Já cadastrada (" + anteriores.getFirst().situacao() + "); não será duplicada nem alterada.");
                    }
                    linhas.add(new Linha(numero + 1, id, anteriores.isEmpty() ? "NOVA" : "EXISTENTE", carga, avisos, null));
                } catch (IllegalArgumentException erro) { linhas.add(new Linha(numero + 1, id, "ERRO", null, avisos, erro.getMessage())); }
            }
        } catch (IOException erro) { throw new IllegalArgumentException("Não foi possível ler o arquivo .xlsx."); }
        catch (org.apache.poi.openxml4j.exceptions.InvalidOperationException erro) { throw new IllegalArgumentException("Arquivo .xlsx inválido."); }
        exigir(!linhas.isEmpty(), "A planilha não contém cargas. Preencha os dados abaixo do cabecalho.");
        return new Conferencia(linhas, (int) linhas.stream().filter(l -> l.situacao().equals("NOVA")).count(),
            (int) linhas.stream().filter(l -> l.situacao().equals("EXISTENTE")).count(), (int) linhas.stream().filter(l -> l.situacao().equals("ERRO")).count());
    }
    @Transactional
    public Importadas importar(Pedido pedido) {
        repositorio.bloquearPlanejamento();
        Conferencia conferencia = conferir(pedido);
        exigir(conferencia.erros() == 0, "Corrija os erros da planilha antes de importar. Nenhuma carga foi salva.");
        exigir(conferencia.novas() > 0, "Todas as cargas deste arquivo já estão cadastradas.");
        List<UUID> ids = new ArrayList<>();
        for (Linha linha : conferencia.linhas()) if (linha.situacao().equals("NOVA")) ids.add(repositorio.criarPlanejamento(linha.carga()).id());
        return new Importadas(ids, conferencia.existentes());
    }
    private Local local(Row linha, Map<String, Integer> colunas, String cliente, String cidade, DataFormatter formato, List<String> avisos) {
        String municipio = textoCelula(linha, colunas, cidade, formato);
        exigir(!municipio.isBlank(), "Informe " + cidade + " no formato Município/UF.");
        String entrada = textoCelula(linha, colunas, cliente, formato);
        var correspondencia = repositorio.resolverClienteImportacao(entrada, municipio);
        Local local = correspondencia == null ? null : correspondencia.local();
        if (local == null) local = repositorio.resolverOperacao("", municipio);
        else {
            avisos.add(cliente + ": cadastro selecionado " + local.nome() + " (" + local.id() + ").");
            if (correspondencia.encontrados() > 1) avisos.add(cliente + ": " + correspondencia.encontrados() + " correspondencias na cidade; usado o primeiro cadastro.");
        }
        exigir(local != null, "Local não encontrado: " + cliente + " / " + municipio + ". Confira o cadastro de coordenadas.");
        if (!"cliente".equals(local.fonte())) {
            avisos.add(cliente + ": coordenadas aproximadas do município " + local.cidade() + ".");
            String nome = textoCelula(linha, colunas, cliente, formato);
            if (!nome.isBlank() && (correspondencia == null || correspondencia.local() == null)) local = new Local(local.id(), nome, local.cidade(), local.latitude(), local.longitude(), local.fonte(), local.detalhe());
        }
        return local;
    }
    private static String textoCelula(Row linha, Map<String, Integer> colunas, String nome, DataFormatter formato) {
        Integer indice = colunas.get(normalizarTexto(nome));
        if (indice != null && (nome.equals("Origem") || nome.equals("Destino"))) {
            Cell celula = linha.getCell(indice);
            if (celula != null && celula.getCellType() == CellType.NUMERIC) return texto(celula.getNumericCellValue());
        }
        return indice == null ? "" : formato.formatCellValue(linha.getCell(indice)).strip();
    }
    private static Double numero(Row linha, Map<String, Integer> colunas, String nome, DataFormatter formato) {
        Integer indice = colunas.get(normalizarTexto(nome));
        Cell celula = indice == null ? null : linha.getCell(indice);
        if (celula == null || celula.getCellType() == CellType.BLANK) return null;
        exigir(celula.getCellType() != CellType.FORMULA, nome + ": informe um valor, sem formula.");
        if (celula.getCellType() == CellType.NUMERIC) return celula.getNumericCellValue();
        String valor = formato.formatCellValue(celula).strip();
        if (valor.isEmpty()) return null;
        // Texto em padrao brasileiro; valores numericos nativos do Excel dispensam conversao.
        exigir(valor.matches("[+-]?(?:[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+)(?:,[0-9]+)?"), nome + ": use número do Excel ou texto no formato 32.000,00.");
        Double numero = converterNumero(valor.replace(".", ""));
        exigir(numero != null, nome + " inválido.");
        return numero;
    }
    private static LocalDateTime data(Cell celula, DataFormatter formato) {
        if (celula == null) return null;
        if (celula.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(celula)) return celula.getLocalDateTimeCellValue();
        if (celula.getCellType() == CellType.FORMULA) return null;
        return converterData(formato.formatCellValue(celula));
    }
    private static boolean mesmaCarga(Carga a, Carga b) {
        return Objects.equals(a.identificacao(), b.identificacao()) && Objects.equals(a.origem(), b.origem()) && Objects.equals(a.destino(), b.destino())
            && Objects.equals(a.coleta(), b.coleta()) && Objects.equals(a.peso(), b.peso()) && Objects.equals(a.frete(), b.frete()) && Objects.equals(a.obrigatoria(), b.obrigatoria());
    }
    public byte[] modelo() {
        try (Workbook livro = new XSSFWorkbook(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Sheet aba = livro.createSheet("Cargas previstas");
            Row cabecalho = aba.createRow(0);
            CellStyle estilo = livro.createCellStyle(); Font fonte = livro.createFont(); fonte.setBold(true); estilo.setFont(fonte);
            estilo.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex()); estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (int i = 0; i < COLUNAS.size(); i++) {
                Cell celula = cabecalho.createCell(i); celula.setCellValue(COLUNAS.get(i)); celula.setCellStyle(estilo); aba.setColumnWidth(i, (i == 5 ? 24 : 28) * 256);
            }
            CellStyle data = livro.createCellStyle(); data.setDataFormat(livro.createDataFormat().getFormat("dd/mm/yyyy hh:mm")); aba.setDefaultColumnStyle(5, data);
            aba.createFreezePane(0, 1); aba.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 100, 0, 8));
            Sheet instrucoes = livro.createSheet("Preenchimento");
            List<String> textos = List.of("Uma linha por carga. Preencha somente a primeira aba; máximo 100 cargas por importação.",
                "Carga: identificacao unica. Reimportar a mesma carga nao cria duplicata; divergencias exigem revisao.",
                "Origem e Destino: nome completo, parte do nome ou CNPJ do cliente (com ou sem pontuacao). Municipio/UF sempre obrigatorio.",
                "CNPJ: prefira texto para preservar os zeros iniciais. A busca fica restrita a cidade informada; havendo varios clientes, usa o primeiro cadastro.",
                "Sem correspondencia, ou com Origem/Destino vazios, usa referencia municipal e avisa na conferencia.",
                "Municipio origem e Municipio destino: obrigatorios, no formato Sao Paulo/SP.",
                "Coleta: data e hora previstas, no formato 18/11/2026 10:00 ou data e hora do Excel; nao anterior a referencia da simulacao.",
                "Peso kg: obrigatorio, maior que zero, em quilogramas. Exemplo: 32000 equivale a 32 toneladas.",
                "Frete: opcional, valor total em reais. Vazio nao significa frete zero nem aciona automaticamente uma tabela de frete minimo.",
                "Obrigatoria: opcional, Sim ou Nao. Vazio equivale a Nao.",
                "Use numeros nativos do Excel; se digitar como texto, use padrao brasileiro: 32.000,00.",
                "Os tempos de carga e descarga usam as medias historicas e os parametros ja configurados.",
                "A sugestao usa referencia, parametros e posicoes atuais. Importar nao confirma reservas.");
            for (int i = 0; i < textos.size(); i++) instrucoes.createRow(i).createCell(0).setCellValue(textos.get(i));
            instrucoes.setColumnWidth(0, 160 * 256);
            livro.write(saida); return saida.toByteArray();
        } catch (IOException erro) { throw new IllegalStateException("Falha ao gerar modelo.", erro); }
    }
    private static void exigir(boolean condicao, String mensagem) { if (!condicao) throw new IllegalArgumentException(mensagem); }
}
