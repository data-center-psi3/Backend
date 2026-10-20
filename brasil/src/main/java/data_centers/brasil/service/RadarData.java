package data_centers.brasil.service;

import data_centers.brasil.repository.RadarRepository.Row;
import data_centers.brasil.exception.InvalidFilterException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/** Normalização somente para consulta; nunca escreve sobre os dados originais. */
final class RadarData {
    private RadarData() {}
    record StateInfo(String name, String region, String ibgePrefix) {}
    static final Map<String, StateInfo> STATES = Map.ofEntries(
        state("AC", "Acre", "Norte", "12"), state("AL", "Alagoas", "Nordeste", "27"),
        state("AP", "Amapá", "Norte", "16"), state("AM", "Amazonas", "Norte", "13"),
        state("BA", "Bahia", "Nordeste", "29"), state("CE", "Ceará", "Nordeste", "23"),
        state("DF", "Distrito Federal", "Centro-Oeste", "53"), state("ES", "Espírito Santo", "Sudeste", "32"),
        state("GO", "Goiás", "Centro-Oeste", "52"), state("MA", "Maranhão", "Nordeste", "21"),
        state("MT", "Mato Grosso", "Centro-Oeste", "51"), state("MS", "Mato Grosso do Sul", "Centro-Oeste", "50"),
        state("MG", "Minas Gerais", "Sudeste", "31"), state("PA", "Pará", "Norte", "15"),
        state("PB", "Paraíba", "Nordeste", "25"), state("PR", "Paraná", "Sul", "41"),
        state("PE", "Pernambuco", "Nordeste", "26"), state("PI", "Piauí", "Nordeste", "22"),
        state("RJ", "Rio de Janeiro", "Sudeste", "33"), state("RN", "Rio Grande do Norte", "Nordeste", "24"),
        state("RS", "Rio Grande do Sul", "Sul", "43"), state("RO", "Rondônia", "Norte", "11"),
        state("RR", "Roraima", "Norte", "14"), state("SC", "Santa Catarina", "Sul", "42"),
        state("SP", "São Paulo", "Sudeste", "35"), state("SE", "Sergipe", "Nordeste", "28"),
        state("TO", "Tocantins", "Norte", "17"));
    private static Map.Entry<String, StateInfo> state(String uf, String name, String region, String prefix) {
        return Map.entry(uf, new StateInfo(name, region, prefix));
    }
    static String clean(String value) {
        if (value == null) return null;
        String s = value.replaceAll("[\\s\\p{Z}]+", " ").strip();
        return Set.of("", "—", "–", "-", "n/a", "na", "null", "não informado", "nao informado",
            "não informada", "nao informada", "não disponível", "nao disponivel").contains(s.toLowerCase(Locale.ROOT)) ? null : s;
    }
    static String key(String s) { s = clean(s); return s == null ? null : s.toLowerCase(Locale.ROOT); }
    static String cityKey(String s) {
        s = key(s);
        return s == null ? null : Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
    static String uf(String s) {
        s = clean(s);
        if (s == null) return null;
        s = s.toUpperCase(Locale.ROOT);
        return STATES.containsKey(s) ? s : null;
    }
    static String requireUf(String s) {
        String uf = uf(s);
        if (uf == null) throw new InvalidFilterException("UF inválida.");
        return uf;
    }
    static BigDecimal number(String raw, boolean count) {
        String s = clean(raw);
        if (s == null || !s.matches("[+-]?\\d+(?:[.,]\\d+)?")) return null;
        BigDecimal n = new BigDecimal(s.replace(',', '.'));
        if (count && (n.signum() < 0 || n.stripTrailingZeros().scale() > 0)) return null;
        return n;
    }
    record Entry(Row raw, String uf, String operator, String cityId, String ibge,
        BigDecimal latitude, BigDecimal longitude, BigDecimal networks, BigDecimal ix, BigDecimal carriers) {
        boolean geo() { return latitude != null && longitude != null; }
    }
    static Entry entry(Row r) {
        String uf = uf(r.uf()), ibge = clean(r.ibge());
        if (uf == null || ibge == null || !ibge.matches("\\d{7}") ||
            !ibge.startsWith(STATES.get(uf).ibgePrefix())) ibge = null;
        String city = cityKey(r.city());
        String cityId = ibge != null ? "ibge:" + ibge : uf != null && city != null ? uf + ":" + city : null;
        BigDecimal lat = number(r.latitude(), false), lon = number(r.longitude(), false);
        if (lat == null || lon == null || lat.abs().compareTo(BigDecimal.valueOf(90)) > 0 ||
            lon.abs().compareTo(BigDecimal.valueOf(180)) > 0) { lat = null; lon = null; }
        return new Entry(r, uf, key(r.operator()), cityId, ibge, lat, lon,
            number(r.networks(), true), number(r.ix(), true), number(r.carriers(), true));
    }
}
