package in.smarttech.dukaandost.service;

import java.util.UUID;

public class ExtractionFailedException extends RuntimeException {

    private final UUID draftId;

    public ExtractionFailedException(UUID draftId) {
        super("Order extraction failed for draft " + draftId);
        this.draftId = draftId;
    }

    public UUID getDraftId() {
        return draftId;
    }
}
