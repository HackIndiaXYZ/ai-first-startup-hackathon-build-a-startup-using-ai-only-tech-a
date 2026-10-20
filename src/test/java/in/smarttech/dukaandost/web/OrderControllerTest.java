package in.smarttech.dukaandost.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.NoteLanguage;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.service.OrderPaymentService;
import in.smarttech.dukaandost.service.PaymentReminderService;
import in.smarttech.dukaandost.service.TodaySummaryService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderPaymentService paymentService;
    @Mock
    private PaymentReminderService reminderService;
    @Mock
    private TodaySummaryService todaySummaryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new OrderController(paymentService, reminderService, todaySummaryService)).build();
    }

    @Test
    void recordsPaymentThroughPostEndpoint() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = org.mockito.Mockito.mock(Order.class);
        Payment payment = org.mockito.Mockito.mock(Payment.class);
        when(payment.getOrder()).thenReturn(order);
        when(payment.getAmount()).thenReturn(new BigDecimal("25"));
        when(paymentService.recordPayment(any(), any(), any(), any(), any()))
                .thenReturn(new OrderPaymentService.PaymentSummary(payment,
                        new BigDecimal("25"), new BigDecimal("75")));

        mockMvc.perform(post("/api/orders/{id}/payments", orderId)
                        .contentType("application/json")
                        .content("""
                                {"amount":25,"note":"cash"}
                                """))
                .andExpect(status().isCreated());

        verify(paymentService).recordPayment(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsNonPositivePaymentAtTheHttpBoundary() throws Exception {
        mockMvc.perform(post("/api/orders/{id}/payments", UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {"amount":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsOutstandingOrdersThroughGetEndpoint() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(paymentService.outstanding()).thenReturn(List.of(new OrderPaymentService.OutstandingOrder(
                orderId, customerId, "Asha", "+919876543210", java.time.LocalDate.of(2026, 10, 13),
                new BigDecimal("500"), new BigDecimal("200"), new BigDecimal("300"),
                "INR", "CONFIRMED", 8, "4 days overdue", java.time.LocalDate.of(2026, 10, 20))));

        mockMvc.perform(get("/api/orders/outstanding"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].daysPending").value(8))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].customerPhone").value("+919876543210"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].dueStatus").value("4 days overdue"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].lastRemindedAt[0]").value(2026))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].lastRemindedAt[1]").value(10))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].lastRemindedAt[2]").value(20));

        verify(paymentService).outstanding();
    }

    @Test
    void historyShowsDatedPaymentsMethodAndBalance() throws Exception {
        Order order = org.mockito.Mockito.mock(Order.class);
        in.smarttech.dukaandost.domain.Customer customer =
                org.mockito.Mockito.mock(in.smarttech.dukaandost.domain.Customer.class);
        when(customer.getName()).thenReturn("Sharma ji");
        when(customer.getPhone()).thenReturn("+919876543210");
        when(order.getCustomer()).thenReturn(customer);
        when(order.getTotal()).thenReturn(new BigDecimal("500"));
        when(order.getCurrency()).thenReturn("INR");
        when(order.getStatus()).thenReturn(in.smarttech.dukaandost.domain.OrderStatus.CONFIRMED);
        when(order.getOrderDate()).thenReturn(java.time.LocalDate.of(2026, 10, 18));
        List<Payment> payments = List.of(
                new Payment(order, new BigDecimal("200"), java.time.Instant.parse("2026-10-18T10:00:00Z"), null,
                        in.smarttech.dukaandost.domain.PaymentMethod.CASH),
                new Payment(order, new BigDecimal("50"), java.time.Instant.parse("2026-10-19T10:00:00Z"), null,
                        in.smarttech.dukaandost.domain.PaymentMethod.UPI));
        when(paymentService.history()).thenReturn(List.of(new OrderPaymentService.OrderSummary(
                order, payments, new BigDecimal("250"), new BigDecimal("250"))));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].outstandingBalance").value(250))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].payments.length()").value(2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].payments[1].method").value("UPI"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].customerPhone").value("+919876543210"));
        verify(order, never()).getItems();
    }

    @Test
    void receiptIncludesOrderItems() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = org.mockito.Mockito.mock(Order.class);
        in.smarttech.dukaandost.domain.Customer customer =
                org.mockito.Mockito.mock(in.smarttech.dukaandost.domain.Customer.class);
        when(customer.getName()).thenReturn("Asha");
        when(order.getCustomer()).thenReturn(customer);
        when(order.getOrderDate()).thenReturn(java.time.LocalDate.of(2026, 10, 20));
        when(order.getTotal()).thenReturn(new BigDecimal("500"));
        when(order.getCurrency()).thenReturn("INR");
        when(order.getStatus()).thenReturn(in.smarttech.dukaandost.domain.OrderStatus.CONFIRMED);
        when(order.getItems()).thenReturn(List.of(new in.smarttech.dukaandost.domain.OrderItem(
                "Tea", BigDecimal.ONE, "box", new BigDecimal("500"), new BigDecimal("500"))));
        when(paymentService.receipt(orderId)).thenReturn(new OrderPaymentService.OrderSummary(
                order, List.of(), BigDecimal.ZERO, new BigDecimal("500")));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.items[0].description").value("Tea"));

        verify(order).getItems();
    }

    @Test
    void rejectsUnknownPaymentMethod() throws Exception {
        mockMvc.perform(post("/api/orders/{id}/payments", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"amount\":10,\"method\":\"BITCOIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsReminderDraftForOwnerChosenLanguage() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(reminderService.draft(orderId, "HINGLISH"))
                .thenReturn(new PaymentReminderService.ReminderDraft(
                        "Namaste Asha, aapke order ke ₹300 baaki hain.", NoteLanguage.HINGLISH, false));

        mockMvc.perform(post("/api/orders/{id}/payment-reminder", orderId)
                        .contentType("application/json")
                        .content("{\"language\":\"HINGLISH\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.message").value("Namaste Asha, aapke order ke ₹300 baaki hain."))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.usedCustomerPreference").value(false));

        verify(reminderService).draft(orderId, "HINGLISH");
    }

    @Test
    void recordsReminderOpenEvent() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/orders/{id}/reminder-opened", orderId)
                        .contentType("application/json")
                        .content("{\"language\":\"HINGLISH\",\"channel\":\"WHATSAPP\"}"))
                .andExpect(status().isNoContent());

        verify(reminderService).recordOpened(orderId, "HINGLISH", "WHATSAPP");
    }

    @Test
    void returnsTodaySummaryWithKolkataBusinessDate() throws Exception {
        when(todaySummaryService.summarize()).thenReturn(new TodaySummaryService.TodaySummary(
                java.time.LocalDate.of(2026, 10, 20), 2, new BigDecimal("700"),
                new BigDecimal("150"), new BigDecimal("550")));

        mockMvc.perform(get("/api/orders/today-summary"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.businessDate[0]").value(2026))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.businessDate[1]").value(10))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.businessDate[2]").value(20))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.confirmedOrderCount").value(2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.paymentsRecordedToday").value(150));
    }
}
