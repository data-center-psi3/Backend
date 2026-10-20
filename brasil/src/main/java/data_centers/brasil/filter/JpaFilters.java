package data_centers.brasil.filter;

import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;
import static data_centers.brasil.filter.FilterField.Type.TEXT;

public final class JpaFilters {
    private JpaFilters() {}
    public static <T> Specification<T> specification(List<FilterCondition> conditions) {
        return specification(conditions, "");
    }
    public static <T> Specification<T> specification(List<FilterCondition> conditions, String prefix) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            for (var condition : conditions) {
                Path<?> path = root;
                String fieldPath = prefix.isEmpty() ? condition.field().jpaPath() : prefix + "." + condition.field().jpaPath();
                for (String part : fieldPath.split("\\.")) path = path.get(part);
                predicates.add(predicate(path, condition, cb));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Predicate predicate(Path<?> path, FilterCondition condition, CriteriaBuilder cb) {
        Object value = condition.values().getFirst();
        boolean text = condition.field().type() == TEXT;
        Expression<?> expression = text ? cb.lower(path.as(String.class)) : path;
        return switch (condition.operator()) {
            case IS_NULL -> Boolean.TRUE.equals(value) ? cb.isNull(path) : cb.isNotNull(path);
            case MIN -> cb.greaterThanOrEqualTo((Expression) path, (Comparable) value);
            case MAX -> cb.lessThanOrEqualTo((Expression) path, (Comparable) value);
            case IN -> expression.in(condition.values().stream().map(v -> text ? v.toString().toLowerCase(Locale.ROOT) : v).toList());
            case EQ -> cb.equal(expression, text ? value.toString().toLowerCase(Locale.ROOT) : value);
            case DEFAULT -> text
                ? cb.like((Expression<String>) expression, "%" + SqlFilters.escapeLike(value.toString().toLowerCase(Locale.ROOT)) + "%", '!')
                : cb.equal(path, value);
        };
    }
}
