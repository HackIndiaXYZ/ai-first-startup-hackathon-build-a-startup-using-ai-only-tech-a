package in.smarttech.dukaandost.service;

import com.fasterxml.jackson.databind.JsonNode;
import in.smarttech.dukaandost.validation.ValidationResult;
import java.util.List;
import java.util.Objects;

public record ExtractionResult(
        ValidationResult validationResult,
        JsonNode rawModelResponse,
        boolean multipleCustomers,
        boolean reviewRequired,
        List<String> reviewNotes) {

    public ExtractionResult {
        Objects.requireNonNull(validationResult, "validationResult must not be null");
        rawModelResponse = Objects.requireNonNull(rawModelResponse, "rawModelResponse must not be null").deepCopy();
        reviewNotes = List.copyOf(reviewNotes);
    }

    @Override
    public JsonNode rawModelResponse() {
        return rawModelResponse.deepCopy();
    }
}
