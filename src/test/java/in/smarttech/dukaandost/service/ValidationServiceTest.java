package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;

import in.smarttech.dukaandost.domain.OrderEntryType;
import in.smarttech.dukaandost.domain.Payment;
import in.smarttech.dukaandost.validation.ExtractedOrder;
import in.smarttech.dukaandost.validation.ExtractedOrderItem;
import in.smarttech.dukaandost.validation.ValidationCode;
import in.smarttech.dukaandost.validation.ValidationResult;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidationServiceTest {

    private static jakarta.validation.Validator beanValidator;
    private static jakarta.validation.ValidatorFactory validatorFactory;

    private ValidationService service;

    @BeforeAll
    static void createBeanValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        beanValidator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeBeanValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        ZoneId kolkata = ZoneId.of("Asia/Kolkata");
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-10-15T06:30:00Z"),
                kolkata);
        service = new ValidationService(fixedClock);
    }

    @Test
    void flagsItemSumMismatchAndShowsBothAmounts() {
        ExtractedOrder order = baseOrder(
                new BigDecimal("400"),
                List.of(item("250"), item("200")));

        ValidationResult result = service.validate(order);

        assertThat(result.hasIssue(ValidationCode.ITEM_TOTAL_MISMATCH)).isTrue();
        assertThat(result.issues())
                .anySatisfy(issue -> assertThat(issue.message())
                        .contains("Items total ₹450", "stated total ₹400"));
    }

    @Test
    void calculatesLineTotalsFromExplicitQuantityAndUnitPriceBeforeComparing() {
        ExtractedOrder order = baseOrder(
                new BigDecimal("400"),
                List.of(new ExtractedOrderItem("Sugar", new BigDecimal("3"), "packet",
                        new BigDecimal("150"), null)));

        ValidationResult result = service.validate(order);

        assertThat(result.normalizedOrder().items().getFirst().lineTotal()).isEqualByComparingTo("450");
        assertThat(result.hasIssue(ValidationCode.ITEM_TOTAL_MISMATCH)).isTrue();
    }

    @Test
    void flagsDueDatesBeforeTheFixedKolkataDate() {
        ExtractedOrder order = withDueDate(baseOrder(new BigDecimal("100"), List.of()), LocalDate.of(2026, 10, 14));

        assertThat(service.validate(order).hasIssue(ValidationCode.DUE_DATE_IN_PAST)).isTrue();
    }

    @Test
    void normalizesValidIndianPhoneNumbers() {
        ExtractedOrder order = withPhone(baseOrder(new BigDecimal("100"), List.of()), "09876543210");

        ValidationResult result = service.validate(order);

        assertThat(result.normalizedOrder().customerPhone()).isEqualTo("+919876543210");
        assertThat(result.hasIssue(ValidationCode.INVALID_PHONE)).isFalse();
    }

    @Test
    void flagsAdvanceGreaterThanOrderTotal() {
        ExtractedOrder order = withAdvance(baseOrder(new BigDecimal("400"), List.of()), new BigDecimal("500"));

        assertThat(service.validate(order).hasIssue(ValidationCode.ADVANCE_EXCEEDS_TOTAL)).isTrue();
    }

    @Test
    void requiresAnOrderTotalBeforeConfirmation() {
        ExtractedOrder order = baseOrder(null, List.of());

        ValidationResult result = service.validate(order);

        assertThat(result.hasIssue(ValidationCode.MISSING_TOTAL)).isTrue();
        assertThat(result.canConfirm()).isFalse();
        assertThat(result.issues())
                .anySatisfy(issue -> assertThat(issue.message()).contains("Enter an order total"));
    }

    @Test
    void rejectsNegativeMoneyAndItemQuantities() {
        ExtractedOrder order = baseOrder(
                new BigDecimal("-20"),
                List.of(new ExtractedOrderItem("Rice", new BigDecimal("-1"), "kg",
                        new BigDecimal("-5"), new BigDecimal("-20"))));

        ValidationResult result = service.validate(order);

        assertThat(result.hasIssue(ValidationCode.NEGATIVE_TOTAL)).isTrue();
        assertThat(result.hasIssue(ValidationCode.NEGATIVE_QUANTITY)).isTrue();
        assertThat(result.hasIssue(ValidationCode.NEGATIVE_UNIT_PRICE)).isTrue();
        assertThat(result.hasIssue(ValidationCode.NEGATIVE_LINE_TOTAL)).isTrue();
        assertThat(result.canConfirm()).isFalse();
    }

    @Test
    void paymentAmountsMustBePositiveInBeanAndOrderValidation() {
        Payment payment = new Payment(null, BigDecimal.ZERO, Instant.parse("2026-10-15T06:30:00Z"), null);
        ExtractedOrder order = withAdvance(baseOrder(new BigDecimal("100"), List.of()), BigDecimal.ZERO);

        assertThat(beanValidator.validate(payment))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("amount"));
        assertThat(service.validate(order).hasIssue(ValidationCode.NON_POSITIVE_ADVANCE)).isTrue();
    }

    @Test
    void convertsItemlessPreviousBalanceToAReviewLineOnConfirmation() {
        ExtractedOrder order = withEntryType(baseOrder(new BigDecimal("450"), List.of()),
                OrderEntryType.PREVIOUS_BALANCE);

        ValidationResult result = service.prepareForConfirmation(order);

        assertThat(result.canConfirm()).isTrue();
        assertThat(result.normalizedOrder().items()).containsExactly(
                new ExtractedOrderItem("Previous balance", null, null, null, new BigDecimal("450")));
    }

    @Test
    void sourceNoteAndReminderLanguagesRemainIndependent() {
        ExtractedOrder order = withLanguages(baseOrder(new BigDecimal("100"), List.of()), "hi", "bn");

        ExtractedOrder validated = service.validate(order).normalizedOrder();

        assertThat(validated.sourceNoteLanguage()).isEqualTo("hi");
        assertThat(validated.preferredReminderLanguage()).isEqualTo("bn");
    }

    private static ExtractedOrder baseOrder(BigDecimal total, List<ExtractedOrderItem> items) {
        return new ExtractedOrder(
                "Asha",
                null,
                "hi",
                "hi",
                OrderEntryType.ORDER,
                LocalDate.of(2026, 10, 15),
                null,
                total,
                "INR",
                items,
                null,
                null,
                null,
                "2 kg rice");
    }

    private static ExtractedOrderItem item(String amount) {
        return new ExtractedOrderItem("Item", null, null, null, new BigDecimal(amount));
    }

    private static ExtractedOrder withDueDate(ExtractedOrder order, LocalDate dueDate) {
        return new ExtractedOrder(order.customerName(), order.customerPhone(), order.sourceNoteLanguage(),
                order.preferredReminderLanguage(), order.entryType(), order.orderDate(), dueDate, order.total(),
                order.currency(), order.items(), order.advanceAmount(), order.advancePaidAt(), order.advanceNote(),
                order.transcript());
    }

    private static ExtractedOrder withPhone(ExtractedOrder order, String phone) {
        return new ExtractedOrder(order.customerName(), phone, order.sourceNoteLanguage(),
                order.preferredReminderLanguage(), order.entryType(), order.orderDate(), order.dueDate(), order.total(),
                order.currency(), order.items(), order.advanceAmount(), order.advancePaidAt(), order.advanceNote(),
                order.transcript());
    }

    private static ExtractedOrder withAdvance(ExtractedOrder order, BigDecimal advance) {
        return new ExtractedOrder(order.customerName(), order.customerPhone(), order.sourceNoteLanguage(),
                order.preferredReminderLanguage(), order.entryType(), order.orderDate(), order.dueDate(), order.total(),
                order.currency(), order.items(), advance, order.advancePaidAt(), order.advanceNote(), order.transcript());
    }

    private static ExtractedOrder withEntryType(ExtractedOrder order, OrderEntryType entryType) {
        return new ExtractedOrder(order.customerName(), order.customerPhone(), order.sourceNoteLanguage(),
                order.preferredReminderLanguage(), entryType, order.orderDate(), order.dueDate(), order.total(),
                order.currency(), order.items(), order.advanceAmount(), order.advancePaidAt(), order.advanceNote(),
                order.transcript());
    }

    private static ExtractedOrder withLanguages(ExtractedOrder order, String sourceLanguage, String reminderLanguage) {
        return new ExtractedOrder(order.customerName(), order.customerPhone(), sourceLanguage, reminderLanguage,
                order.entryType(), order.orderDate(), order.dueDate(), order.total(), order.currency(), order.items(),
                order.advanceAmount(), order.advancePaidAt(), order.advanceNote(), order.transcript());
    }
}
