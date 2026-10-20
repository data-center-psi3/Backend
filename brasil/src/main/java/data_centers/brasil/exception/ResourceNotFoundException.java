package data_centers.brasil.exception;
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
    public ResourceNotFoundException(String resource, Integer id) { super(resource + " não encontrado: " + id); }
}
