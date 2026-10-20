package in.smarttech.dukaandost.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.smarttech.dukaandost.domain.Customer;
import in.smarttech.dukaandost.domain.NotificationType;
import in.smarttech.dukaandost.domain.Owner;
import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.repository.OwnerNotificationRepository;
import in.smarttech.dukaandost.repository.OwnerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class NotificationServiceTest {

    private final OwnerNotificationRepository notifications = mock(OwnerNotificationRepository.class);
    private final OwnerRepository owners = mock(OwnerRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final NotificationService service = new NotificationService(
            notifications, owners, events, Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void disabledInAppSettingPreventsPersistingNotification() {
        UUID ownerId = UUID.randomUUID();
        Owner owner = new Owner("Ravi", "ravi@example.com", "hash", null);
        owner.setInAppNotifications(false);
        when(owners.findById(ownerId)).thenReturn(Optional.of(owner));
        Order order = order();

        service.overdue(ownerId, order, java.math.BigDecimal.TEN);

        verify(notifications, never()).save(any());
    }

    @Test
    void notificationPreferenceFiltersTypeBeforePersistence() {
        UUID ownerId = UUID.randomUUID();
        Owner owner = new Owner("Ravi", "ravi@example.com", "hash", null);
        owner.setPaymentReceivedNotifications(false);
        when(owners.findById(ownerId)).thenReturn(Optional.of(owner));
        when(notifications.save(any())).thenAnswer(call -> call.getArgument(0));

        service.orderConfirmed(ownerId, order());

        verify(notifications).save(any());
        verify(events).publishEvent(any(NotificationService.NotificationEvent.class));
    }

    private static Order order() {
        Order order = mock(Order.class);
        when(order.getCustomer()).thenReturn(new Customer("Ramesh", null, null));
        when(order.getId()).thenReturn(UUID.randomUUID());
        return order;
    }
}
