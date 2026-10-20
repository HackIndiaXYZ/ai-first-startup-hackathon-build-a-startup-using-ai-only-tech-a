package in.smarttech.dukaandost.service;

import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderStatus;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.repository.OrderRepository;
import in.smarttech.dukaandost.security.CurrentOwner;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TodaySummaryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private final OrderRepository orderRepository;
    private final CurrentOwner currentOwner;
    private final Clock clock;

    public TodaySummaryService(OrderRepository orderRepository, CurrentOwner currentOwner, Clock clock) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.currentOwner = Objects.requireNonNull(currentOwner);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public TodaySummary summarize() {
        LocalDate businessDate = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        List<Order> confirmedOrders = orderRepository.findAllByOwnerId(currentOwner.id()).stream()
                .filter(order -> order.getStatus() == OrderStatus.CONFIRMED)
                .toList();
        List<Order> ordersToday = confirmedOrders.stream()
                .filter(order -> businessDate.equals(order.getOrderDate()))
                .toList();
        BigDecimal ordersValue = ordersToday.stream()
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paymentsToday = confirmedOrders.stream()
                .flatMap(order -> order.getPayments().stream())
                .filter(payment -> businessDate.equals(payment.getPaidAt().atZone(BUSINESS_ZONE).toLocalDate()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = confirmedOrders.stream()
                .map(order -> {
                    BigDecimal paid = order.getPayments().stream().map(Payment::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return order.getTotal().subtract(paid).max(BigDecimal.ZERO);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TodaySummary(businessDate, ordersToday.size(), ordersValue, paymentsToday, outstanding);
    }

    public record TodaySummary(
            LocalDate businessDate,
            long confirmedOrderCount,
            BigDecimal confirmedOrderTotal,
            BigDecimal paymentsRecordedToday,
            BigDecimal currentOutstandingDues) {
    }
}
