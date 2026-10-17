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
import in.smarttech.dukaandost.repository.OrderRepository;
import in.smarttech.dukaandost.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
    private Order order;

    private OrderPaymentService service;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        service = new OrderPaymentService(orderRepository, paymentRepository);
        orderId = UUID.randomUUID();
        lenient().when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
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
        verify(orderRepository).findByIdForUpdate(orderId);
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
        when(orderRepository.findByIdForUpdate(missingId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordPayment(missingId, BigDecimal.ONE, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void calculatesOutstandingOrdersAndExcludesPaidOrders() {
        Order outstandingOrder = org.mockito.Mockito.mock(Order.class);
        Order paidOrder = org.mockito.Mockito.mock(Order.class);
        Payment payment = org.mockito.Mockito.mock(Payment.class);
        when(outstandingOrder.getTotal()).thenReturn(new BigDecimal("100"));
        when(outstandingOrder.getPayments()).thenReturn(List.of(payment));
        when(payment.getAmount()).thenReturn(new BigDecimal("40"));
        when(paidOrder.getTotal()).thenReturn(new BigDecimal("50"));
        when(paidOrder.getPayments()).thenReturn(List.of(
                new Payment(paidOrder, new BigDecimal("50"), null, null)));
        when(orderRepository.findByStatus(OrderStatus.CONFIRMED))
                .thenReturn(List.of(outstandingOrder, paidOrder));

        List<OrderPaymentService.OutstandingOrder> result = service.outstanding();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().totalPaid()).isEqualByComparingTo("40");
        assertThat(result.getFirst().outstandingBalance()).isEqualByComparingTo("60");
    }
}
