package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.smarttech.dukaandost.domain.Customer;
import in.smarttech.dukaandost.domain.DraftStatus;
import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderDraft;
import in.smarttech.dukaandost.domain.OrderEntryType;
import in.smarttech.dukaandost.domain.OrderStatus;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.domain.SourceType;
import in.smarttech.dukaandost.repository.CustomerRepository;
import in.smarttech.dukaandost.repository.OrderDraftRepository;
import in.smarttech.dukaandost.repository.OrderRepository;
import in.smarttech.dukaandost.repository.PaymentRepository;
import in.smarttech.dukaandost.validation.ExtractedOrder;
import in.smarttech.dukaandost.validation.ValidationResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DraftWorkflowServiceTest {

    @Mock
    private OrderDraftRepository draftRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ExtractionService extractionService;

    private DraftWorkflowService service;
    private ObjectMapper objectMapper;
    private ExtractedOrder extractedOrder;
    private ExtractionResult extractionResult;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        ValidationService validationService = new ValidationService(
                Clock.fixed(Instant.parse("2026-10-15T06:30:00Z"), ZoneId.of("Asia/Kolkata")));
        service = new DraftWorkflowService(draftRepository, orderRepository, customerRepository,
                paymentRepository, extractionService, validationService, objectMapper);
        extractedOrder = new ExtractedOrder("Asha", "+919876543210", "hi", null,
                OrderEntryType.ORDER, LocalDate.of(2026, 10, 15), null,
                new BigDecimal("100"), "INR", List.of(), new BigDecimal("20"),
                null, "cash", "Asha ko 100 ka maal, 20 advance.");
        extractionResult = new ExtractionResult(
                new ValidationResult(extractedOrder, List.of()),
                objectMapper.createObjectNode(), false, false, List.of());
    }

    @Test
    void createsDraftAndPersistsExtractedFields() {
        OrderDraft draft = new OrderDraft("Asha ko 100 ka maal.", SourceType.TEXT, DraftStatus.PROCESSING);
        when(draftRepository.save(any(OrderDraft.class))).thenReturn(draft);
        when(extractionService.extract(draft.getTranscript())).thenReturn(extractionResult);

        OrderDraft result = service.create(draft.getTranscript());

        assertThat(result.getStatus()).isEqualTo(DraftStatus.READY);
        assertThat(result.getExtractionJson().get("extractedOrder").get("customerName").asText())
                .isEqualTo("Asha");
        verify(extractionService).extract(draft.getTranscript());
    }

    @Test
    void rejectsBlankTranscriptBeforePersistence() {
        assertThatThrownBy(() -> service.create("  "))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("transcript must not be blank");

        verify(draftRepository, never()).save(any());
    }

    @Test
    void updatesOnlyAnActiveDraftAndRerunsExtraction() {
        OrderDraft draft = new OrderDraft("old", SourceType.TEXT, DraftStatus.READY);
        when(draftRepository.findById(any())).thenReturn(Optional.of(draft));
        when(extractionService.extract("new")).thenReturn(extractionResult);
        when(draftRepository.save(draft)).thenReturn(draft);

        OrderDraft result = service.update(UUID.randomUUID(), "new");

        assertThat(result.getTranscript()).isEqualTo("new");
        assertThat(result.getStatus()).isEqualTo(DraftStatus.READY);
        verify(extractionService).extract("new");
    }

    @Test
    void requiresAcknowledgementAndTotalBeforeConfirmation() {
        OrderDraft draft = draftWithExtraction(extractedOrder);
        extractedOrder = new ExtractedOrder("Asha", null, "hi", null, OrderEntryType.ORDER,
                LocalDate.of(2026, 10, 15), null, null, "INR", List.of(), null, null, null, "text");
        draft.setStatus(DraftStatus.NEEDS_REVIEW);
        draft.setExtractionJson(extractionJson(extractedOrder, true));
        when(draft.getStatus()).thenReturn(DraftStatus.NEEDS_REVIEW);
        when(draft.getExtractionJson()).thenReturn(extractionJson(extractedOrder, true));
        when(draftRepository.findByIdForUpdate(draft.getId())).thenReturn(Optional.of(draft));
        when(orderRepository.findByOriginatingDraft_Id(draft.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(draft.getId(), false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("acknowledgement");
        assertThatThrownBy(() -> service.confirm(draft.getId(), true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("order total");
    }

    @Test
    void discardsDraftButNotConfirmedOrder() {
        OrderDraft draft = org.mockito.Mockito.mock(OrderDraft.class);
        UUID draftId = UUID.randomUUID();
        when(draft.getStatus()).thenReturn(DraftStatus.READY);
        when(draftRepository.findById(draftId)).thenReturn(Optional.of(draft));
        when(orderRepository.findByOriginatingDraft_Id(draftId)).thenReturn(Optional.empty());
        when(draftRepository.save(draft)).thenReturn(draft);

        service.discard(draftId);
        verify(draft).setStatus(DraftStatus.DISCARDED);

        OrderDraft confirmedDraft = org.mockito.Mockito.mock(OrderDraft.class);
        UUID confirmedId = UUID.randomUUID();
        when(confirmedDraft.getStatus()).thenReturn(DraftStatus.CONFIRMED);
        when(draftRepository.findById(confirmedId)).thenReturn(Optional.of(confirmedDraft));
        assertThatThrownBy(() -> service.discard(confirmedId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Confirmed orders");
    }

    @Test
    void repeatedConfirmationReturnsExistingOrderWithoutSavingAnother() {
        OrderDraft draft = org.mockito.Mockito.mock(OrderDraft.class);
        UUID draftId = UUID.randomUUID();
        Customer customer = new Customer("Asha", "+919876543210", null);
        Order existing = new Order(customer, draft, OrderEntryType.ORDER,
                LocalDate.of(2026, 10, 15), null, new BigDecimal("100"), "INR",
                OrderStatus.CONFIRMED, "text");
        when(draftRepository.findByIdForUpdate(draftId)).thenReturn(Optional.of(draft));
        when(orderRepository.findByOriginatingDraft_Id(draftId)).thenReturn(Optional.of(existing));

        DraftWorkflowService.ConfirmationResult result = service.confirm(draftId, false);

        assertThat(result.order()).isSameAs(existing);
        verify(orderRepository, never()).saveAndFlush(any());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    private OrderDraft draftWithExtraction(ExtractedOrder order) {
        UUID id = UUID.randomUUID();
        OrderDraft draft = org.mockito.Mockito.mock(OrderDraft.class);
        when(draft.getId()).thenReturn(id);
        when(draft.getStatus()).thenReturn(DraftStatus.READY);
        return draft;
    }

    private ObjectNode extractionJson(ExtractedOrder order, boolean reviewRequired) {
        ObjectNode json = objectMapper.createObjectNode();
        json.set("extractedOrder", objectMapper.valueToTree(order));
        json.put("reviewRequired", reviewRequired);
        json.set("reviewNotes", objectMapper.createArrayNode());
        return json;
    }
}
