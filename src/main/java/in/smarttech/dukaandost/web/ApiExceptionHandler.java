package in.smarttech.dukaandost.web;

import in.smarttech.dukaandost.service.ExtractionFailedException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String EXTRACTION_FAILURE_MESSAGE =
            "Couldn't read this note. Your text is saved. You can retry "
                    + "or enter the details manually.";

    @ExceptionHandler(ExtractionFailedException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ExtractionFailureResponse handleExtractionFailure(ExtractionFailedException exception) {
        return new ExtractionFailureResponse(EXTRACTION_FAILURE_MESSAGE, exception.getDraftId());
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> handleStatus(
            org.springframework.web.server.ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Request failed." : exception.getReason();
        return org.springframework.http.ResponseEntity.status(exception.getStatusCode())
                .body(java.util.Map.of("message", message));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public java.util.Map<String, String> handleValidation(
            org.springframework.web.bind.MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof org.springframework.validation.FieldError field
                        ? field.getField() + " " + field.getDefaultMessage()
                        : error.getDefaultMessage())
                .findFirst().orElse("Invalid request.");
        return java.util.Map.of("message", message);
    }

    public record ExtractionFailureResponse(String message, UUID draftId) {
    }
}
