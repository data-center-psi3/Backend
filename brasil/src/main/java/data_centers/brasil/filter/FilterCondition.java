package data_centers.brasil.filter;

import java.util.List;

public record FilterCondition(FilterField field, Operator operator, List<Object> values) {
    public enum Operator { DEFAULT, EQ, MIN, MAX, IN, IS_NULL }
}
