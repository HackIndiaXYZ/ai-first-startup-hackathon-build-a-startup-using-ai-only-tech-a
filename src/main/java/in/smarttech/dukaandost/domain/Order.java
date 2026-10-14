package in.smarttech.dukaandost.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "originating_draft_id", nullable = false, unique = true)
    private OrderDraft originatingDraft;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32)
    private OrderEntryType entryType;

    @NotNull
    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(nullable = false, columnDefinition = "text")
    private String transcript;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order")
    private List<Payment> payments = new ArrayList<>();

    protected Order() {
    }

    public Order(Customer customer, OrderDraft originatingDraft, OrderEntryType entryType,
                 LocalDate orderDate, LocalDate dueDate, BigDecimal total, String currency,
                 OrderStatus status, String transcript) {
        this.customer = customer;
        this.originatingDraft = originatingDraft;
        this.entryType = entryType;
        this.orderDate = orderDate;
        this.dueDate = dueDate;
        this.total = total;
        this.currency = currency;
        this.status = status;
        this.transcript = transcript;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public OrderDraft getOriginatingDraft() {
        return originatingDraft;
    }

    public OrderEntryType getEntryType() {
        return entryType;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCurrency() {
        return currency;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getTranscript() {
        return transcript;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public List<Payment> getPayments() {
        return List.copyOf(payments);
    }
}
