package in.smarttech.dukaandost.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderDraft;
import in.smarttech.dukaandost.service.DraftWorkflowService;
import in.smarttech.dukaandost.validation.ExtractedOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drafts")
public class DraftController {

    private final DraftWorkflowService workflowService;
    private final ObjectMapper objectMapper;

    public DraftController(DraftWorkflowService workflowService, ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DraftResponse create(@Valid @RequestBody TranscriptRequest request) {
        return response(workflowService.create(request.transcript()));
    }

    @GetMapping("/{id}")
    public DraftResponse get(@PathVariable UUID id) {
        return response(workflowService.get(id));
    }

    @PutMapping("/{id}")
    public DraftResponse update(@PathVariable UUID id, @Valid @RequestBody TranscriptRequest request) {
        return response(workflowService.update(id, request.transcript()));
    }

    @PostMapping("/{id}/extract")
    public DraftResponse extract(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ExtractRequest request) {
        return response(workflowService.retryExtraction(id, request == null ? null : request.transcript()));
    }

    @PostMapping("/{id}/confirm")
    public ConfirmationResponse confirm(
            @PathVariable UUID id, @Valid @RequestBody ConfirmRequest request) {
        DraftWorkflowService.ConfirmationResult result =
                workflowService.confirm(id, request.acknowledgedReview());
        return new ConfirmationResponse(response(result.draft()), OrderResponse.from(result.order()));
    }

    @PostMapping("/{id}/discard")
    public DraftResponse discard(@PathVariable UUID id) {
        return response(workflowService.discard(id));
    }

    private DraftResponse response(OrderDraft draft) {
        JsonNode extraction = draft.getExtractionJson();
        ExtractedOrder order = extraction == null || extraction.get("extractedOrder") == null
                ? null : objectMapper.convertValue(extraction.get("extractedOrder"), ExtractedOrder.class);
        boolean reviewRequired = extraction != null && extraction.path("reviewRequired").asBoolean(false);
        List<String> reviewNotes = extraction == null || extraction.get("reviewNotes") == null
                ? List.of() : objectMapper.convertValue(extraction.get("reviewNotes"), List.class);
        return new DraftResponse(draft.getId(), draft.getTranscript(), draft.getSourceType().name(),
                draft.getStatus().name(), order, reviewRequired, reviewNotes,
                draft.getReviewNotes(), draft.getCreatedAt(), draft.getUpdatedAt());
    }

    public record TranscriptRequest(@NotBlank String transcript) {
    }

    public record ExtractRequest(String transcript) {
    }

    public record ConfirmRequest(@NotNull Boolean acknowledgedReview) {
    }

    public record DraftResponse(
            UUID id,
            String transcript,
            String sourceType,
            String status,
            ExtractedOrder extractedOrder,
            boolean reviewRequired,
            List<String> reviewNotes,
            String persistedReviewNotes,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ConfirmationResponse(DraftResponse draft, OrderResponse order) {
    }

    public record OrderResponse(
            UUID id,
            UUID draftId,
            String customerName,
            String entryType,
            String orderDate,
            String dueDate,
            java.math.BigDecimal total,
            String currency,
            String status,
            List<OrderItemResponse> items) {

        static OrderResponse from(Order order) {
            return new OrderResponse(order.getId(), order.getOriginatingDraft().getId(),
                    order.getCustomer().getName(), order.getEntryType().name(),
                    order.getOrderDate().toString(),
                    order.getDueDate() == null ? null : order.getDueDate().toString(),
                    order.getTotal(), order.getCurrency(), order.getStatus().name(),
                    order.getItems().stream().map(item -> new OrderItemResponse(
                            item.getDescription(), item.getQuantity(), item.getUnit(),
                            item.getUnitPrice(), item.getLineTotal())).toList());
        }
    }

    public record OrderItemResponse(
            String description,
            java.math.BigDecimal quantity,
            String unit,
            java.math.BigDecimal unitPrice,
            java.math.BigDecimal lineTotal) {
    }
}
