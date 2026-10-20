package in.smarttech.dukaandost.service;

import in.smarttech.dukaandost.domain.Customer;
import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderStatus;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.repository.CustomerRepository;
import in.smarttech.dukaandost.repository.OrderRepository;
import in.smarttech.dukaandost.security.CurrentOwner;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerLedgerService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final CurrentOwner currentOwner;

    public CustomerLedgerService(CustomerRepository customers, OrderRepository orders, CurrentOwner currentOwner) {
        this.customers = customers;
        this.orders = orders;
        this.currentOwner = currentOwner;
    }

    @Transactional(readOnly = true)
    public CustomerLedger ledger(UUID customerId) {
        UUID ownerId = currentOwner.id();
        Customer customer = customers.findByIdAndOwnerId(customerId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found."));
        List<Order> customerOrders = orders.findAllByCustomerIdAndOwnerId(customerId, ownerId);
        List<LedgerEvent> timeline = new ArrayList<>();
        BigDecimal outstanding = BigDecimal.ZERO;
        for (Order order : customerOrders) {
            BigDecimal paid = order.getPayments().stream().map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean confirmed = order.getStatus() == OrderStatus.CONFIRMED;
            BigDecimal balance = confirmed
                    ? OrderPaymentService.balance(order.getTotal(), paid) : BigDecimal.ZERO;
            outstanding = outstanding.add(balance);
            Instant orderTime = order.getOrderDate().atStartOfDay(BUSINESS_ZONE).toInstant();
            timeline.add(new LedgerEvent(orderTime, 0, order.getId(), "ORDER",
                    order.getOrderDate(), order.getTotal(), confirmed ? order.getTotal() : BigDecimal.ZERO,
                    order.getCurrency(), null, null, order.getStatus().name()));
            for (Payment payment : order.getPayments()) {
                timeline.add(new LedgerEvent(payment.getPaidAt(), 1, order.getId(), "PAYMENT",
                        payment.getPaidAt().atZone(BUSINESS_ZONE).toLocalDate(), payment.getAmount(),
                        confirmed ? payment.getAmount().negate() : BigDecimal.ZERO,
                        order.getCurrency(), payment.getMethod() == null ? null : payment.getMethod().name(),
                        payment.getNote(), order.getStatus().name()));
            }
        }
        timeline.sort(Comparator.comparing(LedgerEvent::occurredAt).thenComparingInt(LedgerEvent::sequence));
        BigDecimal runningBalance = BigDecimal.ZERO;
        List<LedgerEntry> entries = new ArrayList<>();
        for (LedgerEvent event : timeline) {
            runningBalance = runningBalance.add(event.balanceChange());
            entries.add(new LedgerEntry(event.orderId(), event.type(), event.date(), event.amount(),
                    runningBalance, event.currency(), event.paymentMethod(), event.note(), event.orderStatus()));
        }
        return new CustomerLedger(customer.getId(), customer.getName(), customer.getPhone(),
                entries, outstanding);
    }

    private record LedgerEvent(Instant occurredAt, int sequence, UUID orderId, String type,
                               LocalDate date, BigDecimal amount, BigDecimal balanceChange,
                               String currency, String paymentMethod, String note, String orderStatus) {
    }

    public record LedgerEntry(UUID orderId, String type, LocalDate date, BigDecimal amount,
                              BigDecimal runningBalance, String currency, String paymentMethod,
                              String note, String orderStatus) {
    }

    public record CustomerLedger(UUID customerId, String customerName, String customerPhone,
                                 List<LedgerEntry> entries, BigDecimal currentOutstanding) {
        public CustomerLedger {
            entries = List.copyOf(entries);
        }
    }
}
