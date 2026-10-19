package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.smarttech.dukaandost.domain.OrderEntryType;
import in.smarttech.dukaandost.validation.ValidationCode;
import in.smarttech.dukaandost.validation.ExtractedOrder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.openai.OpenAiChatOptions;

class ExtractionServiceTest {

    private static final String BASE_RESPONSE = """
            {
              "customerName": "Verma ji",
              "customerPhone": null,
              "sourceNoteLanguage": "hi",
              "preferredReminderLanguage": null,
              "entryType": "ORDER",
              "orderDate": "2026-10-15",
              "dueDate": null,
              "total": 400,
              "currency": "INR",
              "items": [
                {"description": "cheeni", "quantity": 3, "unit": "packet",
                 "unitPrice": 150, "lineTotal": null}
              ],
              "advanceAmount": null,
              "advancePaidAt": null,
              "advanceNote": null,
              "multipleCustomers": false,
              "reviewRequired": false,
              "reviewNotes": []
            }
            """;

    private ValidationService validationService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-15T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
        validationService = new ValidationService(clock);
    }

    @Test
    void computesAcceptanceMismatchIndependentlyOfModelReviewFlagAndReturnsRawJson() {
        ExtractionService service = serviceWithResponse(BASE_RESPONSE);

        ExtractionResult result = service.extract("Verma ji ke 3 packet cheeni 150 each, total 400 likh do.");

        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.validationResult().hasIssue(ValidationCode.ITEM_TOTAL_MISMATCH)).isTrue();
        assertThat(result.validationResult().normalizedOrder().items().getFirst().lineTotal())
                .isEqualByComparingTo("450");
        assertThat(result.reviewNotes()).anySatisfy(note -> assertThat(note).contains("₹450", "₹400"));
        assertThat(result.rawModelResponse().path("reviewRequired").asBoolean()).isFalse();
    }

    @Test
    void resolvesFridayAsTheNextFridayAndRequiresReview() {
        String response = BASE_RESPONSE.replace("\"dueDate\": null", "\"dueDate\": \"2026-10-16\"");
        ExtractionResult result = serviceWithResponse(response)
                .extract("Sharma ji ko 2 carton tel dena, total 2400 rupaye. 500 advance de diya, baaki Friday ko.");

        assertThat(result.validationResult().normalizedOrder().dueDate().toString()).isEqualTo("2026-10-16");
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.reviewNotes()).anySatisfy(note -> assertThat(note).contains("Confirm"));
    }

    @Test
    void marksMultipleCustomersAndClearsMergedOrderFields() {
        String response = BASE_RESPONSE.replace("\"multipleCustomers\": false", "\"multipleCustomers\": true");
        ExtractionResult result = serviceWithResponse(response).extract(
                "Meena ko 5 kilo atta 250 ka aur Salim bhai ko 2 dabbe chai 320 ke; Meena ne 100 de diye.");

        assertThat(result.multipleCustomers()).isTrue();
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.validationResult().normalizedOrder().customerName()).isNull();
        assertThat(result.validationResult().normalizedOrder().items()).isEmpty();
        assertThat(result.reviewNotes()).anySatisfy(note -> assertThat(note).contains("one customer", "rerun"));
    }

    @Test
    void preservesPreviousBalanceAmountAndLetsValidationPrepareItsLineItem() {
        String response = BASE_RESPONSE
                .replace("\"entryType\": \"ORDER\"", "\"entryType\": \"PREVIOUS_BALANCE\"")
                .replace("\"dueDate\": null", "\"dueDate\": \"2026-10-16\"")
                .replace("\"total\": 400", "\"total\": 800");
        ExtractionResult result = serviceWithResponse(response)
                .extract("Gupta store ka 800 rupaye baaki hai, kal tak de denge.");

        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.validationResult().normalizedOrder().entryType()).isEqualTo(OrderEntryType.PREVIOUS_BALANCE);
        assertThat(result.validationResult().normalizedOrder().total()).isEqualByComparingTo("800");
        assertThat(result.validationResult().normalizedOrder().dueDate().toString()).isEqualTo("2026-10-16");
        assertThat(validationService.prepareForConfirmation(result.validationResult().normalizedOrder())
                .normalizedOrder().items().getFirst().description()).isEqualTo("Previous balance");
    }

    @Test
    void acceptsPlainIsoDatesForDueDateAndAdvancePaidAt() {
        String response = BASE_RESPONSE
                .replace("\"dueDate\": null", "\"dueDate\": \"2026-10-20\"")
                .replace("\"advanceAmount\": null", "\"advanceAmount\": 100")
                .replace("\"advancePaidAt\": null", "\"advancePaidAt\": \"2026-10-15\"");

        ExtractedOrder order = serviceWithResponse(response)
                .extract("Verma ji ke 3 packet cheeni 150 each, total 400 likh do.")
                .validationResult().normalizedOrder();

        assertThat(order.dueDate().toString()).isEqualTo("2026-10-20");
        assertThat(order.advancePaidAt().toString()).isEqualTo("2026-10-15");
    }

    private ExtractionService serviceWithResponse(String json) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClientRequestSpec prompt = mock(ChatClientRequestSpec.class);
        CallResponseSpec callResponse = mock(CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(prompt);
        when(prompt.system(anyString())).thenReturn(prompt);
        when(prompt.user(anyString())).thenReturn(prompt);
        when(prompt.options(any(OpenAiChatOptions.class))).thenReturn(prompt);
        when(prompt.call()).thenReturn(callResponse);
        when(callResponse.content()).thenReturn(json);

        return new ExtractionService(builder, new ObjectMapper().findAndRegisterModules(), validationService,
                Clock.fixed(Instant.parse("2026-10-15T06:30:00Z"), ZoneId.of("Asia/Kolkata")));
    }
}
