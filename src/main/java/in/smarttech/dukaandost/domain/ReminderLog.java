package in.smarttech.dukaandost.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reminder_logs")
public class ReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 16)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReminderAction action;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ReminderLog() {
    }

    public ReminderLog(UUID ownerId, UUID orderId, UUID customerId,
                       String language, ReminderAction action, Instant occurredAt) {
        this.ownerId = ownerId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.language = language;
        this.action = action;
        this.occurredAt = occurredAt;
    }

    @PrePersist
    void setOccurredAtIfMissing() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getLanguage() {
        return language;
    }

    public ReminderAction getAction() {
        return action;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
