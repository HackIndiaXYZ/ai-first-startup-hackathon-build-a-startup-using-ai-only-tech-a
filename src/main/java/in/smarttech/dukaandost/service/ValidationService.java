package in.smarttech.dukaandost.service;

import in.smarttech.dukaandost.domain.OrderEntryType;
import in.smarttech.dukaandost.validation.ExtractedOrder;
import in.smarttech.dukaandost.validation.ExtractedOrderItem;
import in.smarttech.dukaandost.validation.ValidationCode;
import in.smarttech.dukaandost.validation.ValidationIssue;
import in.smarttech.dukaandost.validation.ValidationResult;
import in.smarttech.dukaandost.validation.ValidationSeverity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {

    private final Clock clock;

    public ValidationService(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
        if (!"Asia/Kolkata".equals(clock.getZone().getId())) {
            throw new IllegalArgumentException("Validation clock must use Asia/Kolkata.");
        }
    }

    public ValidationResult validate(ExtractedOrder order) {
        Objects.requireNonNull(order, "order must not be null");

        List<ValidationIssue> issues = new ArrayList<>();
        String normalizedPhone = normalizePhone(order.customerPhone());
        if (order.customerPhone() != null && !order.customerPhone().isBlank() && normalizedPhone == null) {
            add(issues, ValidationCode.INVALID_PHONE, ValidationSeverity.WARNING, "customerPhone",
                    "Enter a valid Indian mobile number; reminders may not reach this customer.");
        }

        if (order.total() == null) {
            add(issues, ValidationCode.MISSING_TOTAL, ValidationSeverity.ERROR, "total",
                    "Enter an order total before confirming.");
        } else if (order.total().signum() < 0) {
            add(issues, ValidationCode.NEGATIVE_TOTAL, ValidationSeverity.ERROR, "total",
                    "Order total cannot be negative.");
        }

        List<ExtractedOrderItem> itemsWithLineTotals = order.items().stream()
                .map(ValidationService::withCalculatedLineTotal)
                .toList();

        for (int index = 0; index < itemsWithLineTotals.size(); index++) {
            ExtractedOrderItem item = itemsWithLineTotals.get(index);
            String field = "items[" + index + "]";
            if (item.quantity() != null && item.quantity().signum() < 0) {
                add(issues, ValidationCode.NEGATIVE_QUANTITY, ValidationSeverity.ERROR, field + ".quantity",
                        "Item quantity cannot be negative.");
            }
            if (item.unitPrice() != null && item.unitPrice().signum() < 0) {
                add(issues, ValidationCode.NEGATIVE_UNIT_PRICE, ValidationSeverity.ERROR, field + ".unitPrice",
                        "Item unit price cannot be negative.");
            }
            if (item.lineTotal() != null && item.lineTotal().signum() < 0) {
                add(issues, ValidationCode.NEGATIVE_LINE_TOTAL, ValidationSeverity.ERROR, field + ".lineTotal",
                        "Item line total cannot be negative.");
            }
        }

        if (order.dueDate() != null && order.dueDate().isBefore(LocalDate.now(clock))) {
            add(issues, ValidationCode.DUE_DATE_IN_PAST, ValidationSeverity.WARNING, "dueDate",
                    "The due date is in the past.");
        }

        if (!itemsWithLineTotals.isEmpty()
                && itemsWithLineTotals.stream().allMatch(item -> item.lineTotal() != null)
                && order.total() != null) {
            BigDecimal itemsTotal = itemsWithLineTotals.stream()
                    .map(ExtractedOrderItem::lineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (itemsTotal.compareTo(order.total()) != 0) {
                String currency = order.currency();
                add(issues, ValidationCode.ITEM_TOTAL_MISMATCH, ValidationSeverity.WARNING, "total",
                        "Items total " + formatMoney(itemsTotal, currency)
                                + " differs from stated total " + formatMoney(order.total(), currency) + ".");
            }
        }

        if (order.advanceAmount() != null) {
            if (order.advanceAmount().signum() <= 0) {
                add(issues, ValidationCode.NON_POSITIVE_ADVANCE, ValidationSeverity.ERROR, "advanceAmount",
                        "Payment amount must be greater than zero.");
            } else if (order.total() != null && order.advanceAmount().compareTo(order.total()) > 0) {
                add(issues, ValidationCode.ADVANCE_EXCEEDS_TOTAL, ValidationSeverity.WARNING, "advanceAmount",
                        "Advance " + formatMoney(order.advanceAmount(), order.currency())
                                + " exceeds order total " + formatMoney(order.total(), order.currency()) + ".");
            }
        }

        ExtractedOrder normalized = copyWithItems(copyWithPhone(order, normalizedPhone), itemsWithLineTotals);
        return new ValidationResult(normalized, issues);
    }

    public ValidationResult prepareForConfirmation(ExtractedOrder order) {
        ValidationResult result = validate(order);
        if (!result.canConfirm()
                || result.normalizedOrder().entryType() != OrderEntryType.PREVIOUS_BALANCE
                || !result.normalizedOrder().items().isEmpty()) {
            return result;
        }

        ExtractedOrder normalized = result.normalizedOrder();
        List<ExtractedOrderItem> items = List.of(
                new ExtractedOrderItem("Previous balance", null, null, null, normalized.total()));
        return new ValidationResult(copyWithItems(normalized, items), result.issues());
    }

    private static void add(
            List<ValidationIssue> issues,
            ValidationCode code,
            ValidationSeverity severity,
            String field,
            String message) {
        issues.add(new ValidationIssue(code, severity, field, message));
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }

        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }

        if (digits.length() != 10 || digits.charAt(0) < '6' || digits.charAt(0) > '9') {
            return null;
        }
        return "+91" + digits;
    }

    private static ExtractedOrderItem withCalculatedLineTotal(ExtractedOrderItem item) {
        if (item.lineTotal() != null
                || item.quantity() == null
                || item.unitPrice() == null
                || item.quantity().signum() < 0
                || item.unitPrice().signum() < 0) {
            return item;
        }
        return new ExtractedOrderItem(
                item.description(), item.quantity(), item.unit(), item.unitPrice(),
                item.quantity().multiply(item.unitPrice()));
    }

    private static String formatMoney(BigDecimal amount, String currency) {
        String number = amount.stripTrailingZeros().toPlainString();
        return currency == null || currency.isBlank() || "INR".equalsIgnoreCase(currency)
                ? "₹" + number
                : currency.toUpperCase() + " " + number;
    }

    private static ExtractedOrder copyWithPhone(ExtractedOrder order, String phone) {
        return new ExtractedOrder(
                order.customerName(),
                phone,
                order.sourceNoteLanguage(),
                order.preferredReminderLanguage(),
                order.entryType(),
                order.orderDate(),
                order.dueDate(),
                order.total(),
                order.currency(),
                order.items(),
                order.advanceAmount(),
                order.advancePaidAt(),
                order.advanceNote(),
                order.transcript());
    }

    private static ExtractedOrder copyWithItems(ExtractedOrder order, List<ExtractedOrderItem> items) {
        return new ExtractedOrder(
                order.customerName(),
                order.customerPhone(),
                order.sourceNoteLanguage(),
                order.preferredReminderLanguage(),
                order.entryType(),
                order.orderDate(),
                order.dueDate(),
                order.total(),
                order.currency(),
                items,
                order.advanceAmount(),
                order.advancePaidAt(),
                order.advanceNote(),
                order.transcript());
    }
}
