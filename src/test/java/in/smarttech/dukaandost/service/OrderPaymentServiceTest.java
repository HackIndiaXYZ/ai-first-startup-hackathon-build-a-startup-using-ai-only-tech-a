package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderStatus;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.domain.ReminderAction;
import in.smarttech.dukaandost.domain.ReminderLog;
import in.smarttech.dukaandost.domain.Customer;
import in.smarttech.dukaandost.repository.OrderRepository;
import in.smarttech.dukaandost.repository.PaymentRepository;
import in.smarttech.dukaandost.repository.ReminderLogRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OrderPaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ReminderLogRepository reminderLogRepository;
    @Mock
    private Order order;

    private OrderPaymentService service;
    private UUID orderId;
    private final UUID ownerId = UUID.randomUUID();
    private final in.smarttech.dukaandost.security.CurrentOwner currentOwner =
            org.mockito.Mockito.mock(in.smarttech.dukaandost.security.CurrentOwner.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"),
            ZoneId.of("Asia/Kolkata"));

    @BeforeEach
    void setUp() {
        service = new OrderPaymentService(orderRepository, paymentRepository, reminderLogRepository,
                currentOwner, clock);
        orderId = UUID.randomUUID();
        lenient().when(currentOwner.id()).thenReturn(ownerId);
        lenient().when(orderRepository.findByIdForUpdate(orderId, ownerId)).thenReturn(Optional.of(order));
        lenient().when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        lenient().when(order.getTotal()).thenReturn(new BigDecimal("100.00"));
        lenient().when(paymentRepository.findByOrder_Id(orderId)).thenReturn(List.of());
    }

    @Test
    void recordsPartialPaymentAndReturnsBalance() {
        Payment saved = new Payment(order, new BigDecimal("30.00"),
                Instant.parse("2026-10-17T18:00:00Z"), "cash");
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);

        OrderPaymentService.PaymentSummary result = service.recordPayment(
                orderId, new BigDecimal("30.00"), saved.getPaidAt(), saved.getNote());

        assertThat(result.totalPaid()).isEqualByComparingTo("30");
        assertThat(result.outstandingBalance()).isEqualByComparingTo("70");
        verify(orderRepository).findByIdForUpdate(orderId, ownerId);
    }

    @Test
    void recordsFullPaymentWithZeroOutstandingBalance() {
        Payment saved = new Payment(order, new BigDecimal("100.00"), null, null);
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);

        OrderPaymentService.PaymentSummary result =
                service.recordPayment(orderId, new BigDecimal("100.00"), null, null);

        assertThat(result.outstandingBalance()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsOverpaymentBeforeSaving() {
        assertThatThrownBy(() -> service.recordPayment(orderId, new BigDecimal("100.01"), null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void rejectsInvalidAmount() {
        assertThatThrownBy(() -> service.recordPayment(orderId, BigDecimal.ZERO, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> service.recordPayment(orderId, new BigDecimal("-1"), null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void rejectsCancelledAndMissingOrders() {
        when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.recordPayment(orderId, BigDecimal.ONE, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cancelled");

        UUID missingId = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(missingId, ownerId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordPayment(missingId, BigDecimal.ONE, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void calculatesOutstandingOrdersAndExcludesPaidOrders() {
        Order outstandingOrder = org.mockito.Mockito.mock(Order.class);
        Order paidOrder = org.mockito.Mockito.mock(Order.class);
        Customer customer = customer();
        Payment payment = org.mockito.Mockito.mock(Payment.class);
        when(outstandingOrder.getId()).thenReturn(UUID.randomUUID());
        when(outstandingOrder.getCustomer()).thenReturn(customer);
        when(outstandingOrder.getOrderDate()).thenReturn(LocalDate.of(2026, 10, 14));
        when(outstandingOrder.getCurrency()).thenReturn("INR");
        when(outstandingOrder.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(outstandingOrder.getTotal()).thenReturn(new BigDecimal("100"));
        when(outstandingOrder.getPayments()).thenReturn(List.of(payment));
        when(payment.getAmount()).thenReturn(new BigDecimal("40"));
        when(paidOrder.getTotal()).thenReturn(new BigDecimal("50"));
        when(paidOrder.getPayments()).thenReturn(List.of(
                new Payment(paidOrder, new BigDecimal("50"), null, null)));
        when(orderRepository.findByStatusAndOwnerId(OrderStatus.CONFIRMED, ownerId))
                .thenReturn(List.of(outstandingOrder, paidOrder));

        List<OrderPaymentService.OutstandingOrder> result = service.outstanding();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().totalPaid()).isEqualByComparingTo("40");
        assertThat(result.getFirst().outstandingBalance()).isEqualByComparingTo("60");
        assertThat(result.getFirst().daysPending()).isEqualTo(7);
        assertThat(result.getFirst().customerName()).isEqualTo("Asha");
    }

    @Test
    void overdueDaysUseKolkataBusinessDateAndNeverGoNegative() {
        Order eightDaysOld = dueOrder(LocalDate.of(2026, 10, 13));
        Order futureDated = dueOrder(LocalDate.of(2026, 10, 22));
        when(orderRepository.findByStatusAndOwnerId(OrderStatus.CONFIRMED, ownerId))
                .thenReturn(List.of(eightDaysOld, futureDated));

        List<OrderPaymentService.OutstandingOrder> result = service.outstanding();

        assertThat(result).extracting(OrderPaymentService.OutstandingOrder::daysPending)
                .containsExactly(8L, 0L);
    }

    @Test
    void dueDateStatusesCoverMissingTodayFutureAndOverdueDatesInKolkata() {
        Order withoutDueDate = dueOrder(LocalDate.of(2026, 10, 13), null);
        Order dueToday = dueOrder(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 21));
        Order dueLater = dueOrder(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 24));
        Order overdue = dueOrder(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 17));
        when(orderRepository.findByStatusAndOwnerId(OrderStatus.CONFIRMED, ownerId))
                .thenReturn(List.of(withoutDueDate, dueToday, dueLater, overdue));

        List<OrderPaymentService.OutstandingOrder> result = service.outstanding();

        assertThat(result).extracting(OrderPaymentService.OutstandingOrder::dueStatus)
                .containsExactly("Due date not set", "Due today", "Due in 3 days", "4 days overdue");
    }

    @Test
    void dueDtoIncludesLatestReminderDateInKolkata() {
        Order due = dueOrder(LocalDate.of(2026, 10, 13));
        ReminderLog latest = new ReminderLog(ownerId, UUID.randomUUID(), UUID.randomUUID(),
                "ENGLISH", ReminderAction.WHATSAPP_OPENED, Instant.parse("2026-10-20T20:00:00Z"));
        when(orderRepository.findByStatusAndOwnerId(OrderStatus.CONFIRMED, ownerId)).thenReturn(List.of(due));
        when(reminderLogRepository.findFirstByOwnerIdAndOrderIdOrderByOccurredAtDesc(
                any(), any())).thenReturn(Optional.of(latest));

        OrderPaymentService.OutstandingOrder result = service.outstanding().getFirst();

        assertThat(result.lastRemindedAt()).isEqualTo(LocalDate.of(2026, 10, 21));
    }

    private Order dueOrder(LocalDate orderDate) {
        return dueOrder(orderDate, null);
    }

    private Order dueOrder(LocalDate orderDate, LocalDate dueDate) {
        Order due = org.mockito.Mockito.mock(Order.class);
        Customer dueCustomer = customer();
        when(due.getId()).thenReturn(UUID.randomUUID());
        when(due.getCustomer()).thenReturn(dueCustomer);
        when(due.getOrderDate()).thenReturn(orderDate);
        when(due.getDueDate()).thenReturn(dueDate);
        when(due.getTotal()).thenReturn(new BigDecimal("500"));
        when(due.getPayments()).thenReturn(List.of());
        when(due.getCurrency()).thenReturn("INR");
        when(due.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        return due;
    }

    private static Customer customer() {
        Customer customer = org.mockito.Mockito.mock(Customer.class);
        when(customer.getId()).thenReturn(UUID.randomUUID());
        when(customer.getName()).thenReturn("Asha");
        when(customer.getPhone()).thenReturn("+919876543210");
        return customer;
    }
}
