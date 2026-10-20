package in.smarttech.dukaandost.service;

import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderStatus;
import in.smarttech.dukaandost.repository.OrderRepository;
import in.smarttech.dukaandost.repository.OwnerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OverdueNotificationScheduler {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private final OwnerRepository owners;
    private final OrderRepository orders;
    private final NotificationService notifications;
    private final Clock clock;

    public OverdueNotificationScheduler(OwnerRepository owners, OrderRepository orders,
                                        NotificationService notifications, Clock clock) {
        this.owners = owners;
        this.orders = orders;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Kolkata")
    public void notifyOverdueOrders() {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        owners.findAll().forEach(owner -> orders.findByStatusAndOwnerId(OrderStatus.CONFIRMED, owner.getId())
                .stream()
                .filter(order -> isOverdue(order, today))
                .forEach(order -> notifications.overdue(owner.getId(), order, outstanding(order))));
    }

    private static boolean isOverdue(Order order, LocalDate today) {
        return order.getDueDate() != null && order.getDueDate().isBefore(today)
                && outstanding(order).signum() > 0;
    }

    private static BigDecimal outstanding(Order order) {
        BigDecimal paid = order.getPayments().stream().map(payment -> payment.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return order.getTotal().subtract(paid).max(BigDecimal.ZERO);
    }
}
