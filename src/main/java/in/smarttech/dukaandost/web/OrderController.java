package in.smarttech.dukaandost.web;

import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.NoteLanguage;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.domain.PaymentMethod;
import in.smarttech.dukaandost.service.OrderPaymentService;
import in.smarttech.dukaandost.service.PaymentReminderService;
import in.smarttech.dukaandost.service.TodaySummaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderPaymentService paymentService;
    private final PaymentReminderService reminderService;
    private final TodaySummaryService todaySummaryService;

    public OrderController(OrderPaymentService paymentService, PaymentReminderService reminderService,
                           TodaySummaryService todaySummaryService) {
        this.paymentService = paymentService;
        this.reminderService = reminderService;
        this.todaySummaryService = todaySummaryService;
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse recordPayment(
            @PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
        OrderPaymentService.PaymentSummary summary =
                paymentService.recordPayment(id, request.amount(), request.paidAt(), request.note(), request.method());
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
            String note,
            PaymentMethod method) {
    }

    @GetMapping
    public List<OrderHistoryResponse> history() {
        return paymentService.history().stream()
                .map(summary -> OrderHistoryResponse.from(summary, false))
                .toList();
    }

    @GetMapping("/{id}")
    public OrderHistoryResponse receipt(@PathVariable UUID id) {
        return OrderHistoryResponse.from(paymentService.receipt(id), true);
    }

    @PostMapping("/{id}/payment-reminder")
    public PaymentReminderResponse paymentReminder(
            @PathVariable UUID id, @RequestBody(required = false) PaymentReminderRequest request) {
        PaymentReminderService.ReminderDraft reminder = reminderService.draft(
                id, request == null ? null : request.language());
        return new PaymentReminderResponse(reminder.message(), reminder.language(),
                reminder.usedCustomerPreference());
    }

    @PostMapping("/{id}/reminder-opened")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reminderOpened(@PathVariable UUID id, @RequestBody ReminderOpenedRequest request) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Reminder language and channel are required.");
        }
        reminderService.recordOpened(id, request.language(), request.channel());
    }

    @GetMapping("/today-summary")
    public TodaySummaryService.TodaySummary todaySummary() {
        return todaySummaryService.summarize();
    }

    public record PaymentReminderRequest(String language) {
    }

    public record ReminderOpenedRequest(String language, String channel) {
    }

    public record PaymentReminderResponse(String message, NoteLanguage language, boolean usedCustomerPreference) {
    }

    public record PaymentEntry(UUID id, BigDecimal amount, Instant paidAt, PaymentMethod method, String note) {
    }

    public record ItemEntry(String description, BigDecimal quantity, String unit,
                            BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record OrderHistoryResponse(
            UUID orderId,
            UUID customerId,
            LocalDate orderDate,
            LocalDate dueDate,
            String customerName,
            String customerPhone,
            BigDecimal total,
            BigDecimal totalPaid,
            BigDecimal outstandingBalance,
            String preferredReminderLanguage,
            String currency,
            String status,
            List<PaymentEntry> payments,
            List<ItemEntry> items) {
        static OrderHistoryResponse from(OrderPaymentService.OrderSummary summary, boolean includeItems) {
            Order order = summary.order();
            List<ItemEntry> items = includeItems
                    ? order.getItems().stream().map(i -> new ItemEntry(i.getDescription(), i.getQuantity(),
                            i.getUnit(), i.getUnitPrice(), i.getLineTotal())).toList()
                    : List.of();
            return new OrderHistoryResponse(order.getId(), order.getCustomer().getId(),
                    order.getOrderDate(), order.getDueDate(),
                    order.getCustomer().getName(), order.getCustomer().getPhone(),
                    order.getTotal(), summary.totalPaid(),
                    summary.outstandingBalance(), order.getCustomer().getPreferredReminderLanguage(),
                    order.getCurrency(), order.getStatus().name(),
                    summary.payments().stream().map(p -> new PaymentEntry(p.getId(), p.getAmount(),
                            p.getPaidAt(), p.getMethod(), p.getNote())).toList(),
                    items);
        }
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
            String customerPhone,
            LocalDate orderDate,
            BigDecimal total,
            BigDecimal totalPaid,
            BigDecimal outstandingBalance,
            String currency,
            String status,
            long daysPending,
            String dueStatus,
            LocalDate lastRemindedAt) {

        static OutstandingOrderResponse from(OrderPaymentService.OutstandingOrder summary) {
            return new OutstandingOrderResponse(summary.orderId(), summary.customerId(),
                    summary.customerName(), summary.customerPhone(), summary.orderDate(),
                    summary.total(), summary.totalPaid(), summary.outstandingBalance(),
                    summary.currency(), summary.status(), summary.daysPending(),
                    summary.dueStatus(), summary.lastRemindedAt());
        }
    }
}
