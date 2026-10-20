package data_centers.brasil.filter;

public record FilterField(String name, String jpaPath, String sqlColumn, Type type, boolean aggregate) {
    public enum Type { TEXT, INTEGER, LONG, DECIMAL, DATE }
    public static FilterField of(String name, String path, String column, Type type) {
        return new FilterField(name, path, column, type, false);
    }
}
