package in.smarttech.dukaandost.service;

import in.smarttech.dukaandost.domain.NotificationType;
import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.Owner;
import in.smarttech.dukaandost.domain.OwnerNotification;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.repository.OwnerNotificationRepository;
import in.smarttech.dukaandost.repository.OwnerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

    private final OwnerNotificationRepository notifications;
    private final OwnerRepository owners;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public NotificationService(OwnerNotificationRepository notifications, OwnerRepository owners,
                               ApplicationEventPublisher events, Clock clock) {
        this.notifications = notifications;
        this.owners = owners;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void orderConfirmed(UUID ownerId, Order order) {
        create(ownerId, NotificationType.ORDER_CONFIRMED, order.getId(), "Order confirmed",
                "Order for " + safeName(order) + " was confirmed.");
    }

    @Transactional
    public void paymentReceived(UUID ownerId, Order order, Payment payment) {
        create(ownerId, NotificationType.PAYMENT_RECEIVED, payment.getId(), "Payment received",
                "Received " + payment.getAmount().toPlainString() + " from " + safeName(order) + ".");
    }

    @Transactional
    public void overdue(UUID ownerId, Order order, BigDecimal outstanding) {
        create(ownerId, NotificationType.OVERDUE, order.getId(), "Order overdue",
                safeName(order) + " has " + outstanding.toPlainString() + " outstanding.");
    }

    @Transactional(readOnly = true)
    public NotificationList list(UUID ownerId) {
        List<NotificationResponse> rows = notifications.findTop50ByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(NotificationService::response).toList();
        return new NotificationList(rows, notifications.countByOwnerIdAndReadAtIsNull(ownerId));
    }

    @Transactional
    public void markRead(UUID ownerId, UUID notificationId) {
        OwnerNotification notification = notifications.findByIdAndOwnerId(notificationId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found."));
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
            notifications.save(notification);
        }
    }

    @Transactional
    public void clearAll(UUID ownerId) {
        notifications.deleteAllByOwnerId(ownerId);
    }

    private void create(UUID ownerId, NotificationType kind, UUID referenceId, String title, String message) {
        Owner owner = owners.findById(ownerId).orElse(null);
        if (owner == null || !owner.isInAppNotifications() || !enabled(owner, kind)) return;
        if (referenceId != null && notifications.existsByOwnerIdAndKindAndReferenceId(ownerId, kind, referenceId)) {
            return;
        }
        OwnerNotification notification = notifications.save(new OwnerNotification(
                ownerId, kind, referenceId, title, message, clock.instant()));
        events.publishEvent(new NotificationEvent(ownerId, response(notification)));
    }

    private static boolean enabled(Owner owner, NotificationType kind) {
        return switch (kind) {
            case ORDER_CONFIRMED -> owner.isNewOrderNotifications();
            case PAYMENT_RECEIVED -> owner.isPaymentReceivedNotifications();
            case OVERDUE -> owner.isOverdueNotifications();
            case PAYMENT_DUE -> owner.isPaymentDueNotifications();
            case DAILY_SUMMARY -> owner.isDailySummaryNotifications();
        };
    }

    private static String safeName(Order order) {
        String name = order.getCustomer().getName();
        return name == null || name.isBlank() ? "a customer" : name;
    }

    private static NotificationResponse response(OwnerNotification row) {
        return new NotificationResponse(row.getId(), row.getKind().name(), row.getTitle(),
                row.getMessage(), row.getCreatedAt(), row.getReadAt());
    }

    public record NotificationEvent(UUID ownerId, NotificationResponse notification) {
    }

    public record NotificationResponse(UUID id, String kind, String title, String message,
                                       java.time.Instant createdAt, java.time.Instant readAt) {
    }

    public record NotificationList(List<NotificationResponse> notifications, long unreadCount) {
    }
}
