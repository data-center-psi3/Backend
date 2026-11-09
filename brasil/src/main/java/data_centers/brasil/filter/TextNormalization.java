package data_centers.brasil.filter;

import jakarta.persistence.criteria.*;
import java.text.Normalizer;
import java.util.*;

/** Portuguese names: exact normalized comparison, preserving the stored display values. */
public final class TextNormalization {
    private TextNormalization() {}
    private static final String ACCENTS = "áàâãäåéèêëíìîïóòôõöúùûüçñýÿ";
    private static final String PLAIN =   "aaaaaaeeeeiiiiooooouuuucnyy";
    private static final Map<String,String> STATES = Map.ofEntries(
        Map.entry("acre","AC"),Map.entry("alagoas","AL"),Map.entry("amapa","AP"),Map.entry("amazonas","AM"),
        Map.entry("bahia","BA"),Map.entry("ceara","CE"),Map.entry("distrito federal","DF"),Map.entry("espirito santo","ES"),
        Map.entry("goias","GO"),Map.entry("maranhao","MA"),Map.entry("mato grosso","MT"),Map.entry("mato grosso do sul","MS"),
        Map.entry("minas gerais","MG"),Map.entry("para","PA"),Map.entry("paraiba","PB"),Map.entry("parana","PR"),
        Map.entry("pernambuco","PE"),Map.entry("piaui","PI"),Map.entry("rio de janeiro","RJ"),Map.entry("rio grande do norte","RN"),
        Map.entry("rio grande do sul","RS"),Map.entry("rondonia","RO"),Map.entry("roraima","RR"),Map.entry("santa catarina","SC"),
        Map.entry("sao paulo","SP"),Map.entry("sergipe","SE"),Map.entry("tocantins","TO"));
    public static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .replaceAll("[\\s\\u00a0]+", " ").strip().toLowerCase(Locale.ROOT);
    }
    public static String state(String value) {
        String normalized=normalize(value);
        String uf=STATES.getOrDefault(normalized,normalized.toUpperCase(Locale.ROOT));
        if (!STATES.containsValue(uf)) throw new IllegalArgumentException("Estado ou UF inválido.");
        return uf;
    }
    public static boolean applies(FilterCondition c) {
        return Set.of("cidade","estado","nome","empresa").contains(c.field().name())
            && Set.of(FilterCondition.Operator.EQ,FilterCondition.Operator.IN).contains(c.operator());
    }
    public static String value(FilterCondition c, Object value) {
        if (c.field().name().equals("estado")) {
            try { return normalize(state(value.toString())); } catch (IllegalArgumentException ignored) {}
        }
        return normalize(value.toString());
    }
    public static Expression<String> expression(Expression<String> path, CriteriaBuilder cb) {
        var translated=cb.function("translate",String.class,cb.lower(path),cb.literal(ACCENTS),cb.literal(PLAIN));
        var combining=cb.function("regexp_replace",String.class,translated,cb.literal("[\u0300-\u036f]"),cb.literal(""),cb.literal("g"));
        var noNbsp=cb.function("replace",String.class,combining,cb.literal("\u00a0"),cb.literal(" "));
        var spaces=cb.function("regexp_replace",String.class,noNbsp,cb.literal("\\s+"),cb.literal(" "),cb.literal("g"));
        return cb.trim(spaces);
    }
    public static String sql(String column) {
        String translated="TRANSLATE(LOWER("+column+"), '"+ACCENTS+"', '"+PLAIN+"')";
        return "TRIM(REGEXP_REPLACE(REPLACE(REGEXP_REPLACE("+translated+", '[\u0300-\u036f]', '', 'g'), '\u00a0', ' '), '\\s+', ' ', 'g'))";
    }
}
