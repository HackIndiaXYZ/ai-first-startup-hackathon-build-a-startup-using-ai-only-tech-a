package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.smarttech.dukaandost.domain.Customer;
import in.smarttech.dukaandost.domain.DraftStatus;
import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderDraft;
import in.smarttech.dukaandost.domain.OrderEntryType;
import in.smarttech.dukaandost.domain.OrderStatus;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.repository.CustomerRepository;
import in.smarttech.dukaandost.repository.OrderDraftRepository;
import in.smarttech.dukaandost.repository.OrderRepository;
import in.smarttech.dukaandost.repository.PaymentRepository;
import in.smarttech.dukaandost.repository.ReminderLogRepository;
import in.smarttech.dukaandost.security.CurrentOwner;
import in.smarttech.dukaandost.validation.ExtractedOrder;
import in.smarttech.dukaandost.validation.ValidationResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** One account must never see or change another account's drafts, customers, orders or payments. */
class DataIsolationTest {

    private final UUID ownerA = UUID.randomUUID();
    private final UUID ownerB = UUID.randomUUID();

    private final Map<UUID, OrderDraft> drafts = new HashMap<>();
    private final Map<UUID, Order> ordersByDraft = new HashMap<>();
    private final List<Customer> customers = new ArrayList<>();

    private final OrderDraftRepository draftRepository = mock(OrderDraftRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final ReminderLogRepository reminderLogRepository = mock(ReminderLogRepository.class);
    private final ExtractionService extractionService = mock(ExtractionService.class);
    private final CurrentOwner currentOwner = mock(CurrentOwner.class);
    private DraftWorkflowService workflow;
    private OrderPaymentService payments;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        when(draftRepository.save(any(OrderDraft.class))).thenAnswer(call -> {
            OrderDraft draft = call.getArgument(0);
            if (draft.getId() == null) {
                ReflectionTestUtils.setField(draft, "id", UUID.randomUUID());
            }
            drafts.put(draft.getId(), draft);
            return draft;
        });
        when(draftRepository.findByIdAndOwnerId(any(), any())).thenAnswer(call ->
                Optional.ofNullable(drafts.get(call.<UUID>getArgument(0)))
                        .filter(d -> call.<UUID>getArgument(1).equals(d.getOwnerId())));
        when(draftRepository.findByIdForUpdate(any(), any())).thenAnswer(call ->
                Optional.ofNullable(drafts.get(call.<UUID>getArgument(0)))
                        .filter(d -> call.<UUID>getArgument(1).equals(d.getOwnerId())));
        when(customerRepository.findFirstByOwnerIdAndNameIgnoreCase(any(), any())).thenAnswer(call ->
                customers.stream()
                        .filter(c -> call.<UUID>getArgument(0).equals(c.getOwnerId())
                                && c.getName().equalsIgnoreCase(call.getArgument(1)))
                        .findFirst());
        when(customerRepository.save(any(Customer.class))).thenAnswer(call -> {
            Customer customer = call.getArgument(0);
            ReflectionTestUtils.setField(customer, "id", UUID.randomUUID());
            customers.add(customer);
            return customer;
        });
        when(orderRepository.findByOriginatingDraft_Id(any())).thenAnswer(call ->
                Optional.ofNullable(ordersByDraft.get(call.<UUID>getArgument(0))));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(call -> {
            Order order = call.getArgument(0);
            ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
            ordersByDraft.put(order.getOriginatingDraft().getId(), order);
            return order;
        });
        when(orderRepository.findByIdForUpdate(any(), any())).thenAnswer(call ->
                ordersByDraft.values().stream()
                        .filter(o -> o.getId().equals(call.getArgument(0))
                                && call.<UUID>getArgument(1).equals(o.getCustomer().getOwnerId()))
                        .findFirst());
        when(orderRepository.findByStatusAndOwnerId(any(), any())).thenAnswer(call ->
                ordersByDraft.values().stream()
                        .filter(o -> o.getStatus() == call.<OrderStatus>getArgument(0)
                                && call.<UUID>getArgument(1).equals(o.getCustomer().getOwnerId()))
                        .toList());
        when(paymentRepository.findByOrder_Id(any())).thenReturn(List.of());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(call -> call.getArgument(0));

        ExtractedOrder extracted = new ExtractedOrder("Asha", null, "hi", null, OrderEntryType.ORDER,
                LocalDate.of(2026, 10, 15), null, new BigDecimal("100"), "INR", List.of(), null, null,
                null, "Asha ko 100 ka maal.");
        when(extractionService.extract(any())).thenReturn(new ExtractionResult(
                new ValidationResult(extracted, List.of()), mapper.createObjectNode(), false, false, List.of()));

        ValidationService validation = new ValidationService(
                Clock.fixed(Instant.parse("2026-10-15T06:30:00Z"), ZoneId.of("Asia/Kolkata")));
        workflow = new DraftWorkflowService(draftRepository, orderRepository, customerRepository,
                paymentRepository, extractionService, validation, mapper, currentOwner);
        payments = new OrderPaymentService(orderRepository, paymentRepository, reminderLogRepository,
                currentOwner, Clock.fixed(Instant.parse("2026-10-15T06:30:00Z"),
                        ZoneId.of("Asia/Kolkata")));
    }

    private void actAs(UUID owner) {
        when(currentOwner.id()).thenReturn(owner);
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void draftsAreInvisibleAndImmutableToOtherAccounts() {
        actAs(ownerA);
        OrderDraft draft = workflow.create("Asha ko 100 ka maal.");
        UUID id = draft.getId();
        assertThat(draft.getOwnerId()).isEqualTo(ownerA);
        assertThat(workflow.get(id)).isSameAs(draft);

        actAs(ownerB);
        assertNotFound(() -> workflow.get(id));
        assertNotFound(() -> workflow.update(id, "hijack"));
        assertNotFound(() -> workflow.retryExtraction(id, "hijack"));
        assertNotFound(() -> workflow.confirm(id, true));
        assertNotFound(() -> workflow.discard(id));

        assertThat(draft.getTranscript()).isEqualTo("Asha ko 100 ka maal.");
        assertThat(draft.getStatus()).isEqualTo(DraftStatus.READY);
        verify(extractionService, times(1)).extract(any());
        assertThat(ordersByDraft).isEmpty();
    }

    @Test
    void confirmationCreatesAndReusesCustomersOnlyWithinTheOwnersAccount() {
        actAs(ownerA);
        UUID draftA = workflow.create("Asha ko 100 ka maal.").getId();
        Order orderA = workflow.confirm(draftA, true).order();

        actAs(ownerB);
        UUID draftB = workflow.create("Asha ko 100 ka maal.").getId();
        Order orderB = workflow.confirm(draftB, true).order();

        assertThat(orderA.getCustomer().getOwnerId()).isEqualTo(ownerA);
        assertThat(orderB.getCustomer().getOwnerId()).isEqualTo(ownerB);
        assertThat(orderB.getCustomer()).isNotSameAs(orderA.getCustomer());
        assertThat(customers).hasSize(2);
    }

    @Test
    void paymentsAndOutstandingListAreScopedToTheSignedInAccount() {
        actAs(ownerA);
        UUID draftA = workflow.create("Asha ko 100 ka maal.").getId();
        UUID orderId = workflow.confirm(draftA, true).order().getId();

        actAs(ownerB);
        assertNotFound(() -> payments.recordPayment(orderId, new BigDecimal("10"), null, null));
        verify(paymentRepository, never()).save(any());
        assertThat(payments.outstanding()).isEmpty();

        actAs(ownerA);
        assertThat(payments.outstanding()).hasSize(1);
        assertThat(payments.recordPayment(orderId, new BigDecimal("10"), null, null).payment().getAmount())
                .isEqualByComparingTo("10");
    }
}
