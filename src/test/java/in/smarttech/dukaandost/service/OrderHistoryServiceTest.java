package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderEntryType;
import in.smarttech.dukaandost.domain.OrderStatus;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.domain.PaymentMethod;
import in.smarttech.dukaandost.repository.OrderRepository;
import in.smarttech.dukaandost.repository.PaymentRepository;
import in.smarttech.dukaandost.repository.ReminderLogRepository;
import in.smarttech.dukaandost.security.CurrentOwner;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OrderHistoryServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ReminderLogRepository reminderLogs = mock(ReminderLogRepository.class);
    private final CurrentOwner owner = mock(CurrentOwner.class);
    private final UUID ownerId = UUID.randomUUID();
    private final List<Payment> stored = new ArrayList<>();
    private OrderPaymentService service;

    @BeforeEach
    void setUp() {
        service = new OrderPaymentService(orders, payments, reminderLogs, owner,
                Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneId.of("Asia/Kolkata")));
        when(owner.id()).thenReturn(ownerId);
        when(payments.findByOrder_Id(any())).thenAnswer(call -> List.copyOf(stored));
        when(payments.save(any(Payment.class))).thenAnswer(call -> {
            Payment payment = call.getArgument(0);
            stored.add(payment);
            return payment;
        });
    }

    private Order order(String total) {
        return new Order(null, null, OrderEntryType.ORDER, LocalDate.of(2026, 10, 18), null,
                new BigDecimal(total), "INR", OrderStatus.CONFIRMED, "t");
    }

    @Test
    void partialPaymentsReduceTheBalanceStepByStep() {
        Order order = order("500");
        UUID id = UUID.randomUUID();
        when(orders.findByIdForUpdate(id, ownerId)).thenReturn(Optional.of(order));

        OrderPaymentService.PaymentSummary first =
                service.recordPayment(id, new BigDecimal("200"), null, null, PaymentMethod.CASH);
        assertThat(first.totalPaid()).isEqualByComparingTo("200");
        assertThat(first.outstandingBalance()).isEqualByComparingTo("300");
        assertThat(first.payment().getMethod()).isEqualTo(PaymentMethod.CASH);

        OrderPaymentService.PaymentSummary second =
                service.recordPayment(id, new BigDecimal("50"), null, null, PaymentMethod.UPI);
        assertThat(second.totalPaid()).isEqualByComparingTo("250");
        assertThat(second.outstandingBalance()).isEqualByComparingTo("250");
        assertThat(stored).hasSize(2);
    }

    @Test
    void historyListsPaymentsInDateOrderAndNeverShowsNegativeBalance() {
        Order order = mock(Order.class);
        when(order.getTotal()).thenReturn(new BigDecimal("500"));
        Payment later = new Payment(order, new BigDecimal("300"), Instant.parse("2026-10-19T10:00:00Z"), null,
                PaymentMethod.UPI);
        Payment earlier = new Payment(order, new BigDecimal("250"), Instant.parse("2026-10-18T10:00:00Z"), null,
                PaymentMethod.CASH);
        when(order.getPayments()).thenReturn(List.of(later, earlier));
        when(orders.findAllByOwnerId(ownerId)).thenReturn(List.of(order));

        OrderPaymentService.OrderSummary summary = service.history().getFirst();

        assertThat(summary.payments()).containsExactly(earlier, later);
        assertThat(summary.totalPaid()).isEqualByComparingTo("550");
        assertThat(summary.outstandingBalance()).isEqualByComparingTo("0");
    }

    @Test
    void receiptOfAnotherOwnersOrderIsNotFound() {
        UUID id = UUID.randomUUID();
        when(orders.findByIdAndOwnerId(id, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.receipt(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Order not found");
    }
}
