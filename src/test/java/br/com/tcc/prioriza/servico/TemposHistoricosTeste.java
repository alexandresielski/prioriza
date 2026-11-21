package br.com.tcc.prioriza.servico;

import br.com.tcc.prioriza.modelo.Modelos.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TemposHistoricosTeste {
    private Map<String, Object> registro(int numero, String cidade, String tempo) {
        return new HashMap<>(Map.of("Nº Viagem", numero, "Grupo", "SIDER", "Remetente", "Cliente A",
                "Destinatário", "Cliente B", "Carregamento", cidade, "Destino", "Curitiba/PR",
                "Tempo Carga", tempo, "Tempo Descarga", "01:30"));
    }
    private List<Map<String, Object>> amostras(int quantidade) {
        List<Map<String, Object>> registros = new ArrayList<>();
        for (int indice = 0; indice < quantidade; indice++) registros.add(registro(indice, "Sao Paulo/SP", "01:" + (indice % 2 == 0 ? "00" : "10")));
        return registros;
    }
    @Test void interpretaDiasEHorasSemPerderDias() {
        assertEquals(25 + 4 / 60d, TemposHistoricos.converterHoras("01/ 01:04"));
        assertEquals(0, TemposHistoricos.converterHoras("  00:00"));
        assertNull(TemposHistoricos.converterHoras("01:99"));
        assertNull(TemposHistoricos.converterHoras("-01:00"));
        assertNull(TemposHistoricos.converterHoras(null));
    }
    @Test void excluiZerosInvalidosEExtremoSemAlterarOriginal() {
        var registros = amostras(20);
        registros.add(registro(20, "Sao Paulo/SP", "00:00"));
        registros.add(registro(21, "Sao Paulo/SP", "invalido"));
        registros.add(registro(22, "Sao Paulo/SP", "03/ 12:00"));
        var media = TemposHistoricos.buscarMedia(TemposHistoricos.calcularMedias(registros), "carga", "Cliente A", "São Paulo - SP (10 km)");
        assertNotNull(media); assertEquals(20, media.utilizados()); assertEquals(1, media.zeros());
        assertEquals(1, media.invalidos()); assertEquals(1, media.extremos()); assertEquals(65 / 60d, media.mediaHoras(), 1e-9);
        assertEquals("03/ 12:00", registros.getLast().get("Tempo Carga"));
    }
    @Test void separaMunicipioEEstadoSemFundirUnidades() {
        var registros = amostras(10);
        for (int indice = 10; indice < 20; indice++) registros.add(registro(indice, "Sao Paulo/RS", "05:00"));
        var medias = TemposHistoricos.calcularMedias(registros);
        assertEquals(3, medias.size());
        assertEquals(5, TemposHistoricos.buscarMedia(medias, "carga", "Cliente A", "Sao Paulo/RS").mediaHoras());
        assertNull(TemposHistoricos.buscarMedia(medias, "carga", "Cliente A", "Sao Paulo"));
        assertNull(TemposHistoricos.buscarMedia(medias, "carga", "Cliente C", "Sao Paulo/SP"));
    }
    @Test void filtraSomenteSiderENaoDuplicaViagem() {
        var registros = amostras(10);
        registros.add(new HashMap<>(registros.getFirst()));
        var tanque = registro(100, "Sao Paulo/SP", "99:00"); tanque.put("Grupo", "TANQUE"); registros.add(tanque);
        var medias = TemposHistoricos.calcularMedias(registros);
        assertEquals(10, medias.getFirst().total()); assertEquals(2, medias.size());
    }
    @Test void cargaZeroNaoDescartaDescargaValida() {
        var registros = amostras(10);
        registros.forEach(registro -> registro.put("Tempo Carga", "00:00"));
        var medias = TemposHistoricos.calcularMedias(registros);
        assertNull(TemposHistoricos.buscarMedia(medias, "carga", "Cliente A", "Sao Paulo/SP"));
        assertEquals(1.5, TemposHistoricos.buscarMedia(medias, "descarga", "Cliente B", "Curitiba/PR").mediaHoras());
    }
    @Test void poucosRegistrosUsamAlternativaEManualPrevalece() {
        var medias = TemposHistoricos.calcularMedias(amostras(9));
        var tempo = TemposHistoricos.estimarTempo(medias, "carga", "Cliente A", "Sao Paulo/SP", null, 2);
        assertEquals("configurado", tempo.fonte()); assertEquals(2, tempo.horas());
        tempo = TemposHistoricos.estimarTempo(TemposHistoricos.calcularMedias(amostras(10)), "carga", "Cliente A", "Sao Paulo/SP", 3d, 2);
        assertEquals("manual", tempo.fonte()); assertEquals(3, tempo.horas());
    }
    @Test void exigeDezUtilizadosDepoisDoFiltro() {
        var registros = amostras(9); registros.add(registro(10, "Sao Paulo/SP", "90:00"));
        var media = TemposHistoricos.calcularMedias(registros).getFirst();
        assertEquals(9, media.utilizados()); assertEquals(1, media.extremos()); assertFalse(media.aplicavel());
    }
    @Test void dispersaoZeroNaoEliminaTodosOsTempos() {
        var registros = amostras(10); registros.forEach(registro -> registro.put("Tempo Carga", "01:00"));
        var media = TemposHistoricos.calcularMedias(registros).getFirst();
        assertEquals(10, media.utilizados()); assertEquals(1, media.mediaHoras()); assertNull(media.limiteSuperior());
    }
}
