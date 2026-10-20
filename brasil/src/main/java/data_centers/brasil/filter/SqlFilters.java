package data_centers.brasil.filter;

import java.time.LocalDate;
import java.util.*;
import static data_centers.brasil.filter.FilterField.Type.TEXT;

public final class SqlFilters {
    private SqlFilters() {}
    public record Clause(String sql, List<Object> arguments) {}

    public static Clause clause(List<FilterCondition> conditions, boolean aggregate) {
        var predicates = new ArrayList<String>();
        var args = new ArrayList<Object>();
        for (var condition : conditions) {
            if (condition.field().aggregate() != aggregate) continue;
            // Colunas e expressões vêm exclusivamente da lista permitida em FilterResource.
            String column = condition.field().sqlColumn();
            boolean text = condition.field().type() == TEXT;
            String expression = text ? "LOWER(" + column + ")" : column;
            Object value = condition.values().getFirst();
            switch (condition.operator()) {
                case IS_NULL -> predicates.add(column + (Boolean.TRUE.equals(value) ? " IS NULL" : " IS NOT NULL"));
                case MIN -> { predicates.add(column + " >= ?"); args.add(jdbc(value)); }
                case MAX -> { predicates.add(column + " <= ?"); args.add(jdbc(value)); }
                case IN -> {
                    predicates.add(expression + " IN (" + String.join(", ", Collections.nCopies(condition.values().size(), "?")) + ")");
                    condition.values().forEach(v -> args.add(text ? v.toString().toLowerCase(Locale.ROOT) : jdbc(v)));
                }
                case EQ -> { predicates.add(expression + " = ?"); args.add(text ? value.toString().toLowerCase(Locale.ROOT) : jdbc(value)); }
                case DEFAULT -> {
                    predicates.add(text ? expression + " LIKE ? ESCAPE '!'" : column + " = ?");
                    args.add(text ? "%" + escapeLike(value.toString().toLowerCase(Locale.ROOT)) + "%" : jdbc(value));
                }
            }
        }
        return new Clause(predicates.isEmpty() ? "" : (aggregate ? " HAVING " : " WHERE ") + String.join(" AND ", predicates), List.copyOf(args));
    }
    private static Object jdbc(Object value) { return value instanceof LocalDate date ? java.sql.Date.valueOf(date) : value; }
    public static String escapeLike(String value) { return value.replace("!", "!!").replace("%", "!%").replace("_", "!_"); }
}
