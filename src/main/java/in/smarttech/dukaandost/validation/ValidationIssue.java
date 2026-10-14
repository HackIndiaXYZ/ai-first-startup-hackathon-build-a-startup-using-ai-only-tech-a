package in.smarttech.dukaandost.validation;

public record ValidationIssue(
        ValidationCode code,
        ValidationSeverity severity,
        String field,
        String message) {
}
