package in.smarttech.dukaandost.validation;

import java.util.List;

public record ValidationResult(ExtractedOrder normalizedOrder, List<ValidationIssue> issues) {

    public ValidationResult {
        issues = List.copyOf(issues);
    }

    public boolean canConfirm() {
        return issues.stream().noneMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
    }

    public boolean hasIssue(ValidationCode code) {
        return issues.stream().anyMatch(issue -> issue.code() == code);
    }
}
