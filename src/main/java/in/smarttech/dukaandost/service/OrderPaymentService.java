package in.smarttech.dukaandost.service;

import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderStatus;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.repository.OrderRepository;
import in.smarttech.dukaandost.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public OrderPaymentService(OrderRepository orderRepository, PaymentRepository paymentRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
    }

    @Transactional
    public PaymentSummary recordPayment(UUID orderId, BigDecimal amount, Instant paidAt, String note) {
        validateAmount(amount);
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Order not found: " + orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw error(HttpStatus.CONFLICT, "Payments cannot be recorded for cancelled orders.");
        }

        BigDecimal totalPaid = totalPaid(orderId);
        BigDecimal outstanding = order.getTotal().subtract(totalPaid);
        if (amount.compareTo(outstanding) > 0) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Payment exceeds the outstanding balance of " + outstanding + ".");
        }

        Payment payment = paymentRepository.save(new Payment(order, amount, paidAt, note));
        BigDecimal updatedTotalPaid = totalPaid.add(payment.getAmount());
        return new PaymentSummary(payment, updatedTotalPaid, order.getTotal().subtract(updatedTotalPaid));
    }

    @Transactional(readOnly = true)
    public List<OutstandingOrder> outstanding() {
        return orderRepository.findByStatus(OrderStatus.CONFIRMED).stream()
                .map(order -> {
                    BigDecimal totalPaid = order.getPayments().stream()
                            .map(Payment::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new OutstandingOrder(order, totalPaid, order.getTotal().subtract(totalPaid));
                })
                .filter(summary -> summary.outstandingBalance().signum() > 0)
                .toList();
    }

    private BigDecimal totalPaid(UUID orderId) {
        return paymentRepository.findByOrder_Id(orderId).stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw error(HttpStatus.BAD_REQUEST, "Payment amount must be greater than zero.");
        }
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    public record PaymentSummary(Payment payment, BigDecimal totalPaid, BigDecimal outstandingBalance) {
    }

    public record OutstandingOrder(Order order, BigDecimal totalPaid, BigDecimal outstandingBalance) {
    }
}
