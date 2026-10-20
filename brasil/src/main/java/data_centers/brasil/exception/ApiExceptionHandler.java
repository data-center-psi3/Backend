package data_centers.brasil.exception;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InvalidFilterException.class)
    public ProblemDetail filter(InvalidFilterException ex) { return problem(400, ex.getMessage()); }
    @ExceptionHandler(CsvImportException.class)
    public ProblemDetail csv(CsvImportException ex) { return problem(400, ex.getMessage()); }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail notFound(ResourceNotFoundException ex) { return problem(404, ex.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException ex) {
        var result = problem(400, "Dados inválidos.");
        result.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new FieldError(e.getField(), e.getDefaultMessage())).toList());
        return result;
    }
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class,
        MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class,
        PropertyReferenceException.class})
    public ProblemDetail badRequest(Exception ex) { return problem(400, "Parâmetros ou corpo da requisição inválidos."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict(DataIntegrityViolationException ex) {
        return problem(409, "Operação viola uma restrição do banco. Verifique os vínculos e campos obrigatórios.");
    }
    @ExceptionHandler(ResourceConflictException.class)
    public ProblemDetail linkedResource(ResourceConflictException ex) { return problem(409, ex.getMessage()); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail uploadTooLarge(MaxUploadSizeExceededException ex) { return problem(413, "Arquivo excede o limite de 10 MB."); }
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail forbidden(AccessDeniedException ex) { return problem(403, "Acesso negado."); }
    private ProblemDetail problem(int status, String detail) { return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail); }
    public record FieldError(String field, String message) {}
}
