package data_centers.brasil.filter;

import data_centers.brasil.exception.InvalidFilterException;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static data_centers.brasil.filter.FilterCondition.Operator.*;
import static data_centers.brasil.filter.FilterField.Type.*;

@Component
public class QueryFilterParser {
    private static final Set<String> CONTROLS = Set.of("page", "size", "sort");
    private static final Map<String, FilterCondition.Operator> SUFFIXES =
        Map.of("Eq", EQ, "Min", MIN, "Max", MAX, "In", IN, "IsNull", IS_NULL);

    public List<FilterCondition> parse(MultiValueMap<String, String> parameters, FilterResource resource) {
        var result = new ArrayList<FilterCondition>();
        parameters.forEach((key, rawValues) -> {
            if (CONTROLS.contains(key)) return;
            String name = key;
            var operator = DEFAULT;
            if (key.equals("cidades") && resource.regional()) { name = "cidade"; operator = IN; }
            else if (!resource.fields().containsKey(key)) {
                for (var suffix : SUFFIXES.entrySet()) {
                    if (key.endsWith(suffix.getKey())) {
                        name = key.substring(0, key.length() - suffix.getKey().length());
                        operator = suffix.getValue();
                        break;
                    }
                }
            }
            var field = resource.fields().get(name);
            if (field == null) throw new InvalidFilterException("Filtro desconhecido: " + key + ".");
            if ((operator == MIN || operator == MAX) && field.type() == TEXT) {
                throw new InvalidFilterException("Filtro " + key + " só é permitido para números e datas.");
            }
            var values = rawValues.stream().filter(Objects::nonNull).map(String::strip).filter(v -> !v.isEmpty()).toList();
            if (values.isEmpty()) return; // Campos vazios de formulários não restringem a consulta.
            var parsed = new ArrayList<Object>();
            if (operator == IN) {
                for (var value : values) {
                    for (var token : value.split(",", -1)) {
                        if (token.isBlank()) throw new InvalidFilterException("Lista vazia/inválida no filtro " + key + ".");
                        parsed.add(convert(field, token.strip(), key));
                        if (parsed.size() > 100) throw new InvalidFilterException("Filtro " + key + " aceita no máximo 100 valores.");
                    }
                }
            } else {
                if (values.size() != 1) throw new InvalidFilterException("Filtro " + key + " deve ser enviado uma vez. Use " + name + "In para múltiplos valores.");
                if (operator == IS_NULL) {
                    var value = values.getFirst().toLowerCase(Locale.ROOT);
                    if (!value.equals("true") && !value.equals("false")) throw new InvalidFilterException("Filtro " + key + " deve ser true ou false.");
                    parsed.add(Boolean.valueOf(value));
                } else parsed.add(convert(field, values.getFirst(), key));
            }
            result.add(new FilterCondition(field, operator, List.copyOf(parsed)));
        });
        return List.copyOf(result);
    }

    private Object convert(FilterField field, String value, String key) {
        try {
            return switch (field.type()) {
                case TEXT -> value;
                case INTEGER -> Integer.valueOf(value);
                case LONG -> Long.valueOf(value);
                case DECIMAL -> new BigDecimal(value);
                case DATE -> LocalDate.parse(value);
            };
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException ex) {
            throw new InvalidFilterException("Valor inválido no filtro " + key + ". Esperado: " + field.type() + (field.type() == DATE ? " (YYYY-MM-DD)" : "") + ".");
        }
    }
}
