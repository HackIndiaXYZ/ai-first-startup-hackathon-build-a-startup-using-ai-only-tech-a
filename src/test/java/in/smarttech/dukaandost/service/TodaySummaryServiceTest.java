package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderStatus;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.repository.OrderRepository;
import in.smarttech.dukaandost.security.CurrentOwner;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TodaySummaryServiceTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Test
    void usesKolkataMidnightBoundaryAndExcludesCancelledOrdersAndTheirPayments() {
        OrderRepository orders = mock(OrderRepository.class);
        CurrentOwner currentOwner = mock(CurrentOwner.class);
        when(currentOwner.id()).thenReturn(UUID.randomUUID());
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T18:30:00Z"), KOLKATA);

        Order placedToday = order(OrderStatus.CONFIRMED, TODAY, "500",
                List.of(payment("200", "2026-10-19T18:30:00Z"),
                        payment("50", "2026-10-19T18:29:59Z")));
        Order placedYesterday = order(OrderStatus.CONFIRMED, TODAY.minusDays(1), "1000",
                List.of(payment("100", "2026-10-20T00:00:00Z")));
        Order cancelledToday = order(OrderStatus.CANCELLED, TODAY, "900",
                List.of(payment("900", "2026-10-19T20:00:00Z")));
        Order tomorrow = order(OrderStatus.CONFIRMED, TODAY.plusDays(1), "700", List.of());
        when(orders.findAllByOwnerId(currentOwner.id()))
                .thenReturn(List.of(placedToday, placedYesterday, cancelledToday, tomorrow));

        TodaySummaryService.TodaySummary result =
                new TodaySummaryService(orders, currentOwner, clock).summarize();

        assertThat(result.businessDate()).isEqualTo(TODAY);
        assertThat(result.confirmedOrderCount()).isEqualTo(1);
        assertThat(result.confirmedOrderTotal()).isEqualByComparingTo("500");
        assertThat(result.paymentsRecordedToday()).isEqualByComparingTo("300");
        assertThat(result.currentOutstandingDues()).isEqualByComparingTo("1850");
    }

    private static Order order(OrderStatus status, LocalDate date, String total, List<Payment> payments) {
        Order order = mock(Order.class);
        when(order.getStatus()).thenReturn(status);
        when(order.getOrderDate()).thenReturn(date);
        when(order.getTotal()).thenReturn(new BigDecimal(total));
        when(order.getPayments()).thenReturn(payments);
        return order;
    }

    private static Payment payment(String amount, String instant) {
        Payment payment = mock(Payment.class);
        when(payment.getAmount()).thenReturn(new BigDecimal(amount));
        when(payment.getPaidAt()).thenReturn(Instant.parse(instant));
        return payment;
    }
}
