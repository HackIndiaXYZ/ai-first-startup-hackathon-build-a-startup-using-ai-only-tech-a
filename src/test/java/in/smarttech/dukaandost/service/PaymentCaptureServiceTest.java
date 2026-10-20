package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import in.smarttech.dukaandost.domain.Customer;
import in.smarttech.dukaandost.domain.NoteLanguage;
import in.smarttech.dukaandost.repository.CustomerRepository;
import in.smarttech.dukaandost.security.CurrentOwner;
import in.smarttech.dukaandost.validation.ExtractedOrder;
import in.smarttech.dukaandost.validation.ValidationResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class PaymentCaptureServiceTest {

    private final ExtractionService extraction = mock(ExtractionService.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final OrderPaymentService payments = mock(OrderPaymentService.class);
    private final CurrentOwner currentOwner = mock(CurrentOwner.class);
    private final PaymentCaptureService service =
            new PaymentCaptureService(extraction, customers, payments, currentOwner);

    @Test
    void extractionReturnsReviewCandidatesWithoutRecordingAnything() {
        UUID ownerId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Customer customer = new Customer("Ramesh Kumar", "+919876543210", null);
        ReflectionTestUtils.setField(customer, "id", customerId);
        when(currentOwner.id()).thenReturn(ownerId);
        when(extraction.extract("Ramesh ne 100 rupaye diye", NoteLanguage.HINDI))
                .thenReturn(result("Ramesh", new BigDecimal("100")));
        when(customers.findByOwnerIdAndNameContainingIgnoreCase(ownerId, "Ramesh"))
                .thenReturn(List.of(customer));
        when(payments.outstandingForCustomer(customerId)).thenReturn(List.of(
                new OrderPaymentService.PaymentTarget(orderId, LocalDate.of(2026, 10, 21),
                        new BigDecimal("500"), new BigDecimal("300"), "INR")));

        var review = service.extract("Ramesh ne 100 rupaye diye", NoteLanguage.HINDI);

        assertThat(review.amount()).isEqualByComparingTo("100");
        assertThat(review.customerMatches()).hasSize(1);
        assertThat(review.customerMatches().getFirst().outstandingOrders()).hasSize(1);
        assertThat(review.customerMatches().getFirst().outstandingOrders().getFirst().outstandingBalance())
                .isEqualByComparingTo("300");
    }

    @Test
    void missingOrNonPositiveExtractedAmountIsRejectedForReview() {
        when(extraction.extract(any(String.class), any(NoteLanguage.class)))
                .thenReturn(result("Ramesh", BigDecimal.ZERO));

        assertThatThrownBy(() -> service.extract("Ramesh paid zero", NoteLanguage.AUTO))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(422));
    }

    private static ExtractionResult result(String customerName, BigDecimal advanceAmount) {
        ExtractedOrder order = new ExtractedOrder(customerName, null, null, null, null,
                null, null, null, "INR", List.of(), advanceAmount, null, "cash", "");
        return new ExtractionResult(new ValidationResult(order, List.of()),
                JsonNodeFactory.instance.objectNode(), false, true, List.of());
    }
}
