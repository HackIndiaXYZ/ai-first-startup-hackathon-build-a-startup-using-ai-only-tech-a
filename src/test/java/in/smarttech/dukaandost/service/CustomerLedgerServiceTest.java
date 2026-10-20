package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.smarttech.dukaandost.domain.Customer;
import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderStatus;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.domain.PaymentMethod;
import in.smarttech.dukaandost.repository.CustomerRepository;
import in.smarttech.dukaandost.repository.OrderRepository;
import in.smarttech.dukaandost.security.CurrentOwner;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CustomerLedgerServiceTest {

    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CurrentOwner currentOwner = mock(CurrentOwner.class);
    private final UUID ownerId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final Customer customer = mock(Customer.class);
    private CustomerLedgerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerLedgerService(customers, orders, currentOwner);
        when(currentOwner.id()).thenReturn(ownerId);
        when(customer.getId()).thenReturn(customerId);
        when(customer.getName()).thenReturn("Ramesh");
        when(customer.getPhone()).thenReturn("+919876543210");
        when(customers.findByIdAndOwnerId(customerId, ownerId)).thenReturn(Optional.of(customer));
    }

    @Test
    void showsOrderAndPaymentsWithChronologicalRunningBalances() {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(orderId);
        when(order.getCustomer()).thenReturn(customer);
        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(order.getOrderDate()).thenReturn(LocalDate.of(2026, 10, 18));
        when(order.getTotal()).thenReturn(new BigDecimal("500"));
        when(order.getCurrency()).thenReturn("INR");
        Payment first = new Payment(order, new BigDecimal("200"),
                Instant.parse("2026-10-19T08:00:00Z"), null, PaymentMethod.CASH);
        Payment second = new Payment(order, new BigDecimal("50"),
                Instant.parse("2026-10-20T08:00:00Z"), "UPI", PaymentMethod.UPI);
        when(order.getPayments()).thenReturn(List.of(first, second));
        when(orders.findAllByCustomerIdAndOwnerId(customerId, ownerId)).thenReturn(List.of(order));

        CustomerLedgerService.CustomerLedger ledger = service.ledger(customerId);

        assertThat(ledger.currentOutstanding()).isEqualByComparingTo("250");
        assertThat(ledger.entries()).extracting(CustomerLedgerService.LedgerEntry::type)
                .containsExactly("ORDER", "PAYMENT", "PAYMENT");
        assertThat(ledger.entries()).extracting(CustomerLedgerService.LedgerEntry::runningBalance)
                .containsExactly(new BigDecimal("500"), new BigDecimal("300"), new BigDecimal("250"));
        assertThat(ledger.entries().get(1).amount()).isEqualByComparingTo("200");
        assertThat(ledger.entries().get(2).amount()).isEqualByComparingTo("50");
        verify(orders).findAllByCustomerIdAndOwnerId(customerId, ownerId);
    }

    @Test
    void hidesAnotherOwnersCustomerLedger() {
        when(customers.findByIdAndOwnerId(customerId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ledger(customerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Customer not found");
    }

    @Test
    void cancelledOrderRemainsInHistoryButDoesNotIncreaseOutstanding() {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(orderId);
        when(order.getCustomer()).thenReturn(customer);
        when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);
        when(order.getOrderDate()).thenReturn(LocalDate.of(2026, 10, 18));
        when(order.getTotal()).thenReturn(new BigDecimal("500"));
        when(order.getCurrency()).thenReturn("INR");
        when(order.getPayments()).thenReturn(List.of(new Payment(order, new BigDecimal("200"),
                Instant.parse("2026-10-19T08:00:00Z"), null)));
        when(orders.findAllByCustomerIdAndOwnerId(customerId, ownerId)).thenReturn(List.of(order));

        CustomerLedgerService.CustomerLedger ledger = service.ledger(customerId);

        assertThat(ledger.currentOutstanding()).isEqualByComparingTo("0");
        assertThat(ledger.entries()).hasSize(2);
        assertThat(ledger.entries()).allSatisfy(entry ->
                assertThat(entry.runningBalance()).isEqualByComparingTo("0"));
    }
}
