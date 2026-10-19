package in.smarttech.dukaandost.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.smarttech.dukaandost.domain.Customer;
import in.smarttech.dukaandost.domain.DraftStatus;
import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderDraft;
import in.smarttech.dukaandost.domain.OrderItem;
import in.smarttech.dukaandost.domain.OrderStatus;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.domain.SourceType;
import in.smarttech.dukaandost.repository.CustomerRepository;
import in.smarttech.dukaandost.repository.OrderDraftRepository;
import in.smarttech.dukaandost.repository.OrderRepository;
import in.smarttech.dukaandost.repository.PaymentRepository;
import in.smarttech.dukaandost.validation.ExtractedOrder;
import in.smarttech.dukaandost.validation.ValidationIssue;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DraftWorkflowService {

    private final OrderDraftRepository draftRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final ExtractionService extractionService;
    private final ValidationService validationService;
    private final ObjectMapper objectMapper;

    public DraftWorkflowService(
            OrderDraftRepository draftRepository,
            OrderRepository orderRepository,
            CustomerRepository customerRepository,
            PaymentRepository paymentRepository,
            ExtractionService extractionService,
            ValidationService validationService,
            ObjectMapper objectMapper) {
        this.draftRepository = Objects.requireNonNull(draftRepository);
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.customerRepository = Objects.requireNonNull(customerRepository);
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.extractionService = Objects.requireNonNull(extractionService);
        this.validationService = Objects.requireNonNull(validationService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    private static final Logger log = LoggerFactory.getLogger(DraftWorkflowService.class);
    private static final int MAX_LOGGED_MESSAGE = 200;

    public OrderDraft create(String transcript) {
        requireTranscript(transcript);
        OrderDraft draft = draftRepository.save(new OrderDraft(transcript, SourceType.TEXT, DraftStatus.PROCESSING));
        return extractInto(draft);
    }

    @Transactional(readOnly = true)
    public OrderDraft get(UUID id) {
        return draftRepository.findById(id)
                .orElseThrow(() -> notFound("Draft not found: " + id));
    }

    public OrderDraft update(UUID id, String transcript) {
        requireTranscript(transcript);
        OrderDraft draft = draftRepository.findById(id)
                .orElseThrow(() -> notFound("Draft not found: " + id));
        ensureEditable(draft);
        draft.setTranscript(transcript);
        draft.setStatus(DraftStatus.PROCESSING);
        draftRepository.save(draft);
        return extractInto(draft);
    }

    public OrderDraft retryExtraction(UUID id, String editedTranscript) {
        OrderDraft draft = draftRepository.findById(id)
                .orElseThrow(() -> notFound("Draft not found: " + id));
        DraftStatus status = draft.getStatus();
        if (status != DraftStatus.EXTRACTION_FAILED && status != DraftStatus.READY
                && status != DraftStatus.NEEDS_REVIEW) {
            throw conflict("Extraction can only be rerun for failed or unconfirmed drafts.");
        }
        if (editedTranscript != null) {
            requireTranscript(editedTranscript);
            draft.setTranscript(editedTranscript);
        }
        draft.setStatus(DraftStatus.PROCESSING);
        draftRepository.save(draft);
        return extractInto(draft);
    }

    @Transactional
    public ConfirmationResult confirm(UUID id, boolean acknowledgedReview) {
        OrderDraft draft = draftRepository.findByIdForUpdate(id)
                .orElseThrow(() -> notFound("Draft not found: " + id));
        Order existing = orderRepository.findByOriginatingDraft_Id(id).orElse(null);
        if (existing != null) {
            return new ConfirmationResult(draft, existing);
        }
        if (draft.getStatus() == DraftStatus.DISCARDED) {
            throw conflict("Discarded drafts cannot be confirmed.");
        }

        ExtractedOrder extracted = extractedOrder(draft);
        boolean reviewRequired = draft.getStatus() == DraftStatus.NEEDS_REVIEW
                || extractionReviewRequired(draft);
        if (reviewRequired && !acknowledgedReview) {
            throw unprocessable("Review acknowledgement is required before confirmation.");
        }

        var prepared = validationService.prepareForConfirmation(extracted);
        if (prepared.normalizedOrder().total() == null) {
            throw unprocessable("Enter an order total before confirming.");
        }
        if (!prepared.canConfirm()) {
            throw unprocessable(prepared.issues().stream()
                    .filter(issue -> issue.severity() == in.smarttech.dukaandost.validation.ValidationSeverity.ERROR)
                    .map(ValidationIssue::message)
                    .findFirst()
                    .orElse("The draft cannot be confirmed."));
        }
        ExtractedOrder orderData = prepared.normalizedOrder();
        if (orderData.customerName() == null || orderData.customerName().isBlank()) {
            throw unprocessable("A customer name is required before confirmation.");
        }
        if (orderData.orderDate() == null) {
            throw unprocessable("An order date is required before confirmation.");
        }
        if (orderData.currency() == null || orderData.currency().isBlank()) {
            throw unprocessable("A currency is required before confirmation.");
        }

        Customer customer = customerRepository.findFirstByNameIgnoreCase(orderData.customerName())
                .orElseGet(() -> customerRepository.save(new Customer(
                        orderData.customerName(), orderData.customerPhone(),
                        orderData.preferredReminderLanguage())));
        if (customer.getPhone() == null && orderData.customerPhone() != null) {
            customer.setPhone(orderData.customerPhone());
        }
        if (customer.getPreferredReminderLanguage() == null
                && orderData.preferredReminderLanguage() != null) {
            customer.setPreferredReminderLanguage(orderData.preferredReminderLanguage());
        }

        Order order = new Order(customer, draft, orderData.entryType(), orderData.orderDate(),
                orderData.dueDate(), orderData.total(), orderData.currency(), OrderStatus.CONFIRMED,
                draft.getTranscript());
        for (var item : orderData.items()) {
            order.addItem(new OrderItem(item.description(), item.quantity(), item.unit(),
                    item.unitPrice(), item.lineTotal()));
        }
        try {
            order = orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException exception) {
            return new ConfirmationResult(draft,
                    orderRepository.findByOriginatingDraft_Id(id)
                            .orElseThrow(() -> exception));
        }
        if (orderData.advanceAmount() != null) {
            paymentRepository.save(new Payment(order, orderData.advanceAmount(),
                    orderData.advancePaidAt() == null ? null
                            : orderData.advancePaidAt().atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant(),
                    orderData.advanceNote()));
        }
        draft.setStatus(DraftStatus.CONFIRMED);
        draftRepository.save(draft);
        return new ConfirmationResult(draft, order);
    }

    @Transactional
    public OrderDraft discard(UUID id) {
        OrderDraft draft = draftRepository.findById(id)
                .orElseThrow(() -> notFound("Draft not found: " + id));
        if (orderRepository.findByOriginatingDraft_Id(id).isPresent()
                || draft.getStatus() == DraftStatus.CONFIRMED) {
            throw conflict("Confirmed orders cannot be discarded.");
        }
        draft.setStatus(DraftStatus.DISCARDED);
        return draftRepository.save(draft);
    }

    private OrderDraft extractInto(OrderDraft draft) {
        try {
            ExtractionResult result = extractionService.extract(draft.getTranscript());
            draft.setExtractionJson(toExtractionJson(result));
            draft.setReviewNotes(String.join("\n", result.reviewNotes()));
            draft.setStatus(result.reviewRequired() ? DraftStatus.NEEDS_REVIEW : DraftStatus.READY);
            return draftRepository.save(draft);
        } catch (Exception exception) {
            logExtractionFailure(draft, exception);
            draft.setStatus(DraftStatus.EXTRACTION_FAILED);
            draft.setReviewNotes("Couldn't read this note. Your text is saved. You can retry "
                    + "or enter the details manually.");
            draftRepository.save(draft);
            throw new ExtractionFailedException(draft.getId());
        }
    }

    // Logs only exception types and a bounded message; never the transcript, request body or API key.
    private void logExtractionFailure(OrderDraft draft, Exception exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = String.valueOf(exception.getMessage());
        String transcript = draft.getTranscript();
        if (transcript != null && !transcript.isBlank()) {
            message = message.replace(transcript, "[redacted]");
        }
        message = message.replaceAll("(?:sk-[A-Za-z0-9_-]+|AIza[0-9A-Za-z_-]{30,})", "[redacted]")
                .replaceAll("\\s+", " ");
        if (message.length() > MAX_LOGGED_MESSAGE) {
            message = message.substring(0, MAX_LOGGED_MESSAGE) + "...";
        }
        log.error("Extraction failed for draft {}: {}: {} (root cause {})",
                draft.getId(), exception.getClass().getName(), message, root.getClass().getName());
    }

    private JsonNode toExtractionJson(ExtractionResult result) {
        ObjectNode json = objectMapper.createObjectNode();
        json.set("extractedOrder", objectMapper.valueToTree(result.validationResult().normalizedOrder()));
        json.set("rawModelResponse", result.rawModelResponse());
        json.put("reviewRequired", result.reviewRequired());
        json.set("reviewNotes", objectMapper.valueToTree(result.reviewNotes()));
        json.set("validationIssues", objectMapper.valueToTree(result.validationResult().issues()));
        return json;
    }

    private ExtractedOrder extractedOrder(OrderDraft draft) {
        JsonNode json = draft.getExtractionJson();
        if (json == null || json.get("extractedOrder") == null) {
            throw unprocessable("The draft has no successful extraction.");
        }
        try {
            return objectMapper.treeToValue(json.get("extractedOrder"), ExtractedOrder.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Saved extraction data is invalid.", exception);
        }
    }

    private boolean extractionReviewRequired(OrderDraft draft) {
        return draft.getExtractionJson() != null
                && draft.getExtractionJson().path("reviewRequired").asBoolean(false);
    }

    private static void requireTranscript(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "transcript must not be blank");
        }
    }

    private static void ensureEditable(OrderDraft draft) {
        if (draft.getStatus() == DraftStatus.CONFIRMED || draft.getStatus() == DraftStatus.DISCARDED) {
            throw conflict("Only active drafts may be edited.");
        }
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException unprocessable(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public record ConfirmationResult(OrderDraft draft, Order order) {
    }
}
