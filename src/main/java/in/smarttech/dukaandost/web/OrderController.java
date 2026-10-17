package in.smarttech.dukaandost.web;

import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.service.OrderPaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderPaymentService paymentService;

    public OrderController(OrderPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse recordPayment(
            @PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
        OrderPaymentService.PaymentSummary summary =
                paymentService.recordPayment(id, request.amount(), request.paidAt(), request.note());
        return PaymentResponse.from(summary);
    }

    @GetMapping("/outstanding")
    public List<OutstandingOrderResponse> outstanding() {
        return paymentService.outstanding().stream()
                .map(OutstandingOrderResponse::from)
                .toList();
    }

    public record PaymentRequest(
            @NotNull @Positive BigDecimal amount,
            Instant paidAt,
            String note) {
    }

    public record PaymentResponse(
            UUID id,
            UUID orderId,
            BigDecimal amount,
            Instant paidAt,
            String note,
            BigDecimal totalPaid,
            BigDecimal outstandingBalance) {

        static PaymentResponse from(OrderPaymentService.PaymentSummary summary) {
            Payment payment = summary.payment();
            return new PaymentResponse(payment.getId(), payment.getOrder().getId(),
                    payment.getAmount(), payment.getPaidAt(), payment.getNote(),
                    summary.totalPaid(), summary.outstandingBalance());
        }
    }

    public record OutstandingOrderResponse(
            UUID orderId,
            UUID customerId,
            String customerName,
            BigDecimal total,
            BigDecimal totalPaid,
            BigDecimal outstandingBalance,
            String currency,
            String status) {

        static OutstandingOrderResponse from(OrderPaymentService.OutstandingOrder summary) {
            Order order = summary.order();
            return new OutstandingOrderResponse(order.getId(), order.getCustomer().getId(),
                    order.getCustomer().getName(), order.getTotal(), summary.totalPaid(),
                    summary.outstandingBalance(), order.getCurrency(), order.getStatus().name());
        }
    }
}
