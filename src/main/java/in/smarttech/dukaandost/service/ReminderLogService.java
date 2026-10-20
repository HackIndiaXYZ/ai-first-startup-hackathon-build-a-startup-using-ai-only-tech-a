package in.smarttech.dukaandost.service;

import in.smarttech.dukaandost.domain.NoteLanguage;
import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderStatus;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.domain.ReminderAction;
import in.smarttech.dukaandost.domain.ReminderLog;
import in.smarttech.dukaandost.repository.OrderRepository;
import in.smarttech.dukaandost.repository.ReminderLogRepository;
import in.smarttech.dukaandost.security.CurrentOwner;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReminderLogService {

    private final OrderRepository orderRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final CurrentOwner currentOwner;
    private final Clock clock;

    public ReminderLogService(OrderRepository orderRepository, ReminderLogRepository reminderLogRepository,
                              CurrentOwner currentOwner, Clock clock) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.reminderLogRepository = Objects.requireNonNull(reminderLogRepository);
        this.currentOwner = Objects.requireNonNull(currentOwner);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public void record(UUID orderId, NoteLanguage language, ReminderAction action) {
        UUID ownerId = currentOwner.id();
        Order order = orderRepository.findByIdAndOwnerId(orderId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
        if (language == null || language == NoteLanguage.AUTO || action == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A supported language and reminder action are required.");
        }
        BigDecimal paid = order.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (order.getStatus() != OrderStatus.CONFIRMED
                || OrderPaymentService.balance(order.getTotal(), paid).signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Reminder logging is only available for confirmed orders with an outstanding balance.");
        }
        reminderLogRepository.save(new ReminderLog(ownerId, orderId, order.getCustomer().getId(),
                language.name(), action, clock.instant()));
    }
}
