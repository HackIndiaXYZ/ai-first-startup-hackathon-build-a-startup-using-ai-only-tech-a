package in.smarttech.dukaandost.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.service.OrderPaymentService;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(paymentService)).build();
    }

    @Test
    void recordsPaymentThroughPostEndpoint() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = org.mockito.Mockito.mock(Order.class);
        Payment payment = org.mockito.Mockito.mock(Payment.class);
        when(payment.getOrder()).thenReturn(order);
        when(payment.getAmount()).thenReturn(new BigDecimal("25"));
        when(paymentService.recordPayment(any(), any(), any(), any()))
                .thenReturn(new OrderPaymentService.PaymentSummary(payment,
                        new BigDecimal("25"), new BigDecimal("75")));

        mockMvc.perform(post("/api/orders/{id}/payments", orderId)
                        .contentType("application/json")
                        .content("""
                                {"amount":25,"note":"cash"}
                                """))
                .andExpect(status().isCreated());

        verify(paymentService).recordPayment(any(), any(), any(), any());
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
        when(paymentService.outstanding()).thenReturn(List.of());

        mockMvc.perform(get("/api/orders/outstanding"))
                .andExpect(status().isOk());

        verify(paymentService).outstanding();
    }
}
