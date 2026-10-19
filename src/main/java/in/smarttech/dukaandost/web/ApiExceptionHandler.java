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

    public record ExtractionFailureResponse(String message, UUID draftId) {
    }
}
