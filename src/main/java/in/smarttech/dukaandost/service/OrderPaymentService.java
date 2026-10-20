package in.smarttech.dukaandost.service;

import in.smarttech.dukaandost.domain.Order;
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
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final CurrentOwner currentOwner;
    private final Clock clock;
    private NotificationService notificationService;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    public OrderPaymentService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                               ReminderLogRepository reminderLogRepository, CurrentOwner currentOwner, Clock clock) {
        this.currentOwner = Objects.requireNonNull(currentOwner);
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.reminderLogRepository = Objects.requireNonNull(reminderLogRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Autowired(required = false)
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public PaymentSummary recordPayment(UUID orderId, BigDecimal amount, Instant paidAt, String note) {
        return recordPayment(orderId, amount, paidAt, note, null);
    }

    @Transactional
    public PaymentSummary recordPayment(UUID orderId, BigDecimal amount, Instant paidAt, String note,
                                        PaymentMethod method) {
        validateAmount(amount);
        Order order = orderRepository.findByIdForUpdate(orderId, currentOwner.id())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Order not found: " + orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw error(HttpStatus.CONFLICT, "Payments cannot be recorded for cancelled orders.");
        }

        BigDecimal totalPaid = totalPaid(orderId);
        BigDecimal outstanding = balance(order.getTotal(), totalPaid);
        if (amount.compareTo(outstanding) > 0) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Payment exceeds the outstanding balance of " + outstanding + ".");
        }

        Payment payment = paymentRepository.save(new Payment(order, amount, paidAt, note, method));
        if (notificationService != null) {
            notificationService.paymentReceived(currentOwner.id(), order, payment);
        }
        BigDecimal updatedTotalPaid = totalPaid.add(payment.getAmount());
        return new PaymentSummary(payment, updatedTotalPaid, balance(order.getTotal(), updatedTotalPaid));
    }

    @Transactional(readOnly = true)
    public List<OrderSummary> history() {
        return orderRepository.findAllByOwnerId(currentOwner.id()).stream()
                .map(OrderPaymentService::summarize)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderSummary receipt(UUID orderId) {
        Order order = orderRepository.findByIdAndOwnerId(orderId, currentOwner.id())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Order not found: " + orderId));
        order.getItems().size();
        return summarize(order);
    }

    private static OrderSummary summarize(Order order) {
        List<Payment> payments = order.getPayments().stream()
                .sorted(Comparator.comparing(Payment::getPaidAt))
                .toList();
        BigDecimal paid = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderSummary(order, payments, paid, balance(order.getTotal(), paid));
    }

    /** Outstanding balance: total minus recorded payments, never below zero. */
    static BigDecimal balance(BigDecimal total, BigDecimal paid) {
        return total.subtract(paid).max(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<OutstandingOrder> outstanding() {
        UUID ownerId = currentOwner.id();
        LocalDate businessDate = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        return orderRepository.findByStatusAndOwnerId(OrderStatus.CONFIRMED, ownerId).stream()
                .map(order -> outstandingOrder(order, ownerId, businessDate))
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentTarget> outstandingForCustomer(UUID customerId) {
        UUID ownerId = currentOwner.id();
        LocalDate businessDate = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        return orderRepository.findByStatusAndOwnerId(OrderStatus.CONFIRMED, ownerId).stream()
                .filter(order -> order.getCustomer().getId().equals(customerId))
                .map(order -> outstandingOrder(order, ownerId, businessDate))
                .flatMap(Optional::stream)
                .map(order -> new PaymentTarget(order.orderId(), order.orderDate(), order.total(),
                        order.outstandingBalance(), order.currency()))
                .toList();
    }

    private Optional<OutstandingOrder> outstandingOrder(Order order, UUID ownerId, LocalDate businessDate) {
        BigDecimal totalPaid = order.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = balance(order.getTotal(), totalPaid);
        if (outstanding.signum() <= 0) {
            return Optional.empty();
        }
        LocalDate lastRemindedAt = reminderLogRepository
                .findFirstByOwnerIdAndOrderIdOrderByOccurredAtDesc(ownerId, order.getId())
                .map(log -> log.getOccurredAt().atZone(BUSINESS_ZONE).toLocalDate())
                .orElse(null);
        return Optional.of(new OutstandingOrder(order.getId(), order.getCustomer().getId(),
                order.getCustomer().getName(), order.getCustomer().getPhone(),
                order.getOrderDate(), order.getTotal(), totalPaid, outstanding,
                order.getCurrency(), order.getStatus().name(),
                daysPending(order.getOrderDate(), businessDate),
                dueStatus(order.getDueDate(), businessDate), lastRemindedAt));
    }

    private static String dueStatus(LocalDate dueDate, LocalDate businessDate) {
        if (dueDate == null) {
            return "Due date not set";
        }
        long days = ChronoUnit.DAYS.between(businessDate, dueDate);
        if (days == 0) {
            return "Due today";
        }
        if (days > 0) {
            return "Due in " + days + (days == 1 ? " day" : " days");
        }
        long overdueDays = Math.abs(days);
        return overdueDays + (overdueDays == 1 ? " day overdue" : " days overdue");
    }

    private static long daysPending(LocalDate orderDate, LocalDate businessDate) {
        return orderDate == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(orderDate, businessDate));
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

    public record OrderSummary(Order order, List<Payment> payments, BigDecimal totalPaid,
                               BigDecimal outstandingBalance) {
    }

    public record OutstandingOrder(
            UUID orderId,
            UUID customerId,
            String customerName,
            String customerPhone,
            LocalDate orderDate,
            BigDecimal total,
            BigDecimal totalPaid,
            BigDecimal outstandingBalance,
            String currency,
            String status,
            long daysPending,
            String dueStatus,
            LocalDate lastRemindedAt) {
    }

    public record PaymentTarget(UUID orderId, LocalDate orderDate, BigDecimal total,
                                BigDecimal outstandingBalance, String currency) {
    }
}
