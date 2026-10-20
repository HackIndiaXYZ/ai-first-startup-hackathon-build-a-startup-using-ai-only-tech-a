package in.smarttech.dukaandost.service;

import in.smarttech.dukaandost.domain.Customer;
import in.smarttech.dukaandost.domain.NoteLanguage;
import in.smarttech.dukaandost.repository.CustomerRepository;
import in.smarttech.dukaandost.security.CurrentOwner;
import in.smarttech.dukaandost.validation.ExtractedOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentCaptureService {

    private final ExtractionService extraction;
    private final CustomerRepository customers;
    private final OrderPaymentService payments;
    private final CurrentOwner currentOwner;

    public PaymentCaptureService(ExtractionService extraction, CustomerRepository customers,
                                 OrderPaymentService payments, CurrentOwner currentOwner) {
        this.extraction = extraction;
        this.customers = customers;
        this.payments = payments;
        this.currentOwner = currentOwner;
    }

    public PaymentCaptureReview extract(String transcript, NoteLanguage language) {
        ExtractionResult result = extraction.extract(transcript,
                language == null ? NoteLanguage.AUTO : language);
        ExtractedOrder order = result.validationResult().normalizedOrder();
        if (result.multipleCustomers() || order.customerName() == null || order.customerName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not identify one customer. Edit the payment note to include one clear customer name.");
        }
        BigDecimal amount = order.advanceAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not read a positive payment amount. Edit the note and try again.");
        }
        UUID ownerId = currentOwner.id();
        List<CustomerPaymentMatch> matches = customers
                .findByOwnerIdAndNameContainingIgnoreCase(ownerId, order.customerName().strip())
                .stream()
                .map(customer -> new CustomerPaymentMatch(customer.getId(), customer.getName(),
                        customer.getPhone(), payments.outstandingForCustomer(customer.getId())))
                .toList();
        return new PaymentCaptureReview(transcript, order.customerName(), amount,
                order.advanceNote(), matches);
    }

    public record CustomerPaymentMatch(UUID customerId, String customerName, String phone,
                                       List<OrderPaymentService.PaymentTarget> outstandingOrders) {
        public CustomerPaymentMatch {
            outstandingOrders = List.copyOf(outstandingOrders);
        }
    }

    public record PaymentCaptureReview(String transcript, String extractedCustomerName,
                                        BigDecimal amount, String note,
                                        List<CustomerPaymentMatch> customerMatches) {
        public PaymentCaptureReview {
            customerMatches = List.copyOf(customerMatches);
        }
    }
}
