package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.smarttech.dukaandost.domain.Customer;
import in.smarttech.dukaandost.domain.NoteLanguage;
import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderStatus;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.domain.ReminderAction;
import in.smarttech.dukaandost.domain.ReminderLog;
import in.smarttech.dukaandost.repository.OrderRepository;
import in.smarttech.dukaandost.repository.ReminderLogRepository;
import in.smarttech.dukaandost.security.CurrentOwner;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class ReminderLogServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final ReminderLogRepository logs = mock(ReminderLogRepository.class);
    private final CurrentOwner currentOwner = mock(CurrentOwner.class);
    private final UUID ownerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"),
            ZoneId.of("Asia/Kolkata"));
    private ReminderLogService service;

    @BeforeEach
    void setUp() {
        service = new ReminderLogService(orders, logs, currentOwner, clock);
        when(currentOwner.id()).thenReturn(ownerId);
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(customerId);
        Order order = mock(Order.class);
        when(order.getCustomer()).thenReturn(customer);
        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(order.getTotal()).thenReturn(new BigDecimal("500"));
        when(order.getPayments()).thenReturn(List.of(new Payment(order, new BigDecimal("200"), null, null)));
        when(orders.findByIdAndOwnerId(orderId, ownerId)).thenReturn(Optional.of(order));
    }

    @Test
    void storesOwnerOrderCustomerTimeLanguageAndAction() {
        service.record(orderId, NoteLanguage.MARATHI, ReminderAction.SMS_OPENED);

        ArgumentCaptor<ReminderLog> captor = ArgumentCaptor.forClass(ReminderLog.class);
        verify(logs).save(captor.capture());
        ReminderLog saved = captor.getValue();
        assertThat(saved.getOwnerId()).isEqualTo(ownerId);
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getOccurredAt()).isEqualTo(clock.instant());
        assertThat(saved.getLanguage()).isEqualTo("MARATHI");
        assertThat(saved.getAction()).isEqualTo(ReminderAction.SMS_OPENED);
    }

    @Test
    void doesNotLogAnotherOwnersOrder() {
        when(orders.findByIdAndOwnerId(orderId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(orderId, NoteLanguage.ENGLISH, ReminderAction.DRAFT_CREATED))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Order not found");
        verify(logs, org.mockito.Mockito.never()).save(any(ReminderLog.class));
    }
}
