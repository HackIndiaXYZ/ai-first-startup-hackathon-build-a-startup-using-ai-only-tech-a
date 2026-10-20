package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.smarttech.dukaandost.domain.Customer;
import in.smarttech.dukaandost.domain.NoteLanguage;
import in.smarttech.dukaandost.domain.Owner;
import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderStatus;
import in.smarttech.dukaandost.domain.ReminderAction;
import in.smarttech.dukaandost.repository.OwnerRepository;
import in.smarttech.dukaandost.security.CurrentOwner;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PaymentReminderServiceTest {

    private final OrderPaymentService orderPaymentService = mock(OrderPaymentService.class);
    private final ReminderLogService reminderLogService = mock(ReminderLogService.class);
    private final OwnerRepository owners = mock(OwnerRepository.class);
    private final CurrentOwner currentOwner = mock(CurrentOwner.class);
    private final PaymentReminderService service =
            new PaymentReminderService(orderPaymentService, reminderLogService, owners, currentOwner,
                    "Asha General Store", "asha@upi");
    private final UUID orderId = UUID.randomUUID();
    private final Order order = mock(Order.class);
    private final Customer customer = mock(Customer.class);

    @BeforeEach
    void setUp() {
        when(currentOwner.id()).thenReturn(UUID.randomUUID());
        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(order.getId()).thenReturn(orderId);
        when(order.getCustomer()).thenReturn(customer);
        when(order.getCurrency()).thenReturn("INR");
        when(order.getDueDate()).thenReturn(LocalDate.of(2026, 11, 5));
        when(customer.getName()).thenReturn("Asha");
        when(orderPaymentService.receipt(orderId)).thenReturn(
                new OrderPaymentService.OrderSummary(order, List.of(), new BigDecimal("200"),
                        new BigDecimal("300")));
    }

    @Test
    void usesCustomerPreferredLanguageAndIncludesKnownReminderDetails() {
        when(customer.getPreferredReminderLanguage()).thenReturn("hi");

        PaymentReminderService.ReminderDraft result = service.draft(orderId, "ENGLISH");

        assertThat(result.language()).isEqualTo(NoteLanguage.HINDI);
        assertThat(result.usedCustomerPreference()).isTrue();
        assertThat(result.message()).contains("Asha", "₹300", "05 Nov 2026",
                "Asha General Store", "asha%40upi");
        verify(orderPaymentService).receipt(orderId);
        verify(reminderLogService).record(orderId, NoteLanguage.HINDI, ReminderAction.DRAFT_CREATED);
    }

    @Test
    void usesOwnerChosenLanguageWhenNoSupportedPreferenceExists() {
        when(customer.getPreferredReminderLanguage()).thenReturn(null);

        PaymentReminderService.ReminderDraft result = service.draft(orderId, "HINGLISH");

        assertThat(result.language()).isEqualTo(NoteLanguage.HINGLISH);
        assertThat(result.usedCustomerPreference()).isFalse();
        assertThat(result.message()).contains("Asha", "₹300", "05 Nov 2026");
    }

    @Test
    void omitsUnknownCustomerNameAndDueDate() {
        when(customer.getPreferredReminderLanguage()).thenReturn(null);
        when(customer.getName()).thenReturn(null);
        when(order.getDueDate()).thenReturn(null);

        PaymentReminderService.ReminderDraft result = service.draft(orderId, "ENGLISH");

        assertThat(result.message()).contains("₹300");
        assertThat(result.message()).doesNotContain("null", "by ", "due date", "05 Nov");
    }

    @Test
    void requiresLanguageWhenCustomerHasNoSupportedPreference() {
        when(customer.getPreferredReminderLanguage()).thenReturn(null);

        assertThatThrownBy(() -> service.draft(orderId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Choose a language");
    }

    @Test
    void rejectsOrdersWithoutAnOutstandingBalance() {
        when(customer.getPreferredReminderLanguage()).thenReturn("English");
        when(orderPaymentService.receipt(orderId)).thenReturn(
                new OrderPaymentService.OrderSummary(order, List.of(), new BigDecimal("500"), BigDecimal.ZERO));

        assertThatThrownBy(() -> service.draft(orderId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("outstanding balance");
    }

    @Test
    void rejectsCancelledOrders() {
        when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);
        when(customer.getPreferredReminderLanguage()).thenReturn("English");

        assertThatThrownBy(() -> service.draft(orderId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("confirmed order");
    }

    @Test
    void rendersLocalTemplatesForAllSupportedLanguages() {
        when(customer.getPreferredReminderLanguage()).thenReturn(null);

        for (NoteLanguage language : List.of(NoteLanguage.HINDI, NoteLanguage.HINGLISH,
                NoteLanguage.ENGLISH, NoteLanguage.MARATHI, NoteLanguage.BENGALI, NoteLanguage.TAMIL)) {
            PaymentReminderService.ReminderDraft result = service.draft(orderId, language.name());
            assertThat(result.language()).isEqualTo(language);
            assertThat(result.message()).contains("Asha", "₹300", "asha%40upi");
        }
    }

    @Test
    void reminderUsesOutstandingAmountAfterMultiplePayments() {
        when(customer.getPreferredReminderLanguage()).thenReturn(null);
        when(order.getDueDate()).thenReturn(null);
        when(orderPaymentService.receipt(orderId)).thenReturn(
                new OrderPaymentService.OrderSummary(order, List.of(),
                        new BigDecimal("250"), new BigDecimal("250")));

        PaymentReminderService.ReminderDraft result = service.draft(orderId, "ENGLISH");

        assertThat(result.message()).contains("₹250").doesNotContain("₹500");
    }

    @Test
    void appendsEncodedUpiLinkForOutstandingAmount() {
        when(customer.getPreferredReminderLanguage()).thenReturn(null);

        PaymentReminderService.ReminderDraft result = service.draft(orderId, "ENGLISH");

        assertThat(result.message()).contains("upi://pay?pa=asha%40upi&am=300.00&cu=INR&pn=Asha%20General%20Store");
    }

    @Test
    void prefersShopNameFromSignedInOwnerProfile() {
        Owner owner = new Owner("Asha", "asha@example.test", "hash", null);
        owner.setShopName("My Profile Shop");
        when(owners.findById(currentOwner.id())).thenReturn(Optional.of(owner));
        when(customer.getPreferredReminderLanguage()).thenReturn(null);

        PaymentReminderService.ReminderDraft result = service.draft(orderId, "ENGLISH");

        assertThat(result.message()).contains("My Profile Shop").doesNotContain("Asha General Store");
    }

    @Test
    void upiLinkRequiresPositiveAmountAndInrCurrency() {
        assertThat(PaymentReminderService.buildUpiLink("shop@upi", "Shop", new BigDecimal("250"), "INR"))
                .isEqualTo("upi://pay?pa=shop%40upi&am=250.00&cu=INR&pn=Shop");
        assertThatThrownBy(() -> PaymentReminderService.buildUpiLink(
                "shop@upi", "Shop", BigDecimal.ZERO, "INR"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentReminderService.buildUpiLink(
                "shop@upi", "Shop", new BigDecimal("250"), "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownReminderLanguageWithHelpfulMessage() {
        when(customer.getPreferredReminderLanguage()).thenReturn(null);

        assertThatThrownBy(() -> service.draft(orderId, "unknown"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown reminder language");
    }

    @Test
    void recordsWhatsAppAndSmsOpensUsingSelectedLanguage() {
        service.recordOpened(orderId, "ENGLISH", "WHATSAPP");
        service.recordOpened(orderId, "TAMIL", "SMS");

        verify(reminderLogService).record(orderId, NoteLanguage.ENGLISH, ReminderAction.WHATSAPP_OPENED);
        verify(reminderLogService).record(orderId, NoteLanguage.TAMIL, ReminderAction.SMS_OPENED);
    }
}
