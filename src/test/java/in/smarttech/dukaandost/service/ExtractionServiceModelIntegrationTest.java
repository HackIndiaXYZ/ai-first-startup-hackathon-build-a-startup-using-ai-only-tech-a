package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;

import in.smarttech.dukaandost.domain.OrderEntryType;
import in.smarttech.dukaandost.validation.ValidationCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = ExtractionServiceModelIntegrationTest.ExtractionTestConfiguration.class)
@EnabledIf("isApiKeyConfigured")
class ExtractionServiceModelIntegrationTest {

    @Autowired
    private ExtractionService extractionService;

    @Autowired
    private ValidationService validationService;

    static boolean isApiKeyConfigured() {
        return present("GEMINI_API_KEY");
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', textBlock = """
            HINDI    | शर्मा जी को 2 कार्टन तेल देना, कुल 2400 रुपये
            HINGLISH | Sharma ji ko 2 carton tel dena, total 2400 rupaye
            ENGLISH  | Send 2 cartons of oil to Sharma ji, total 2400 rupees
            MARATHI  | शर्मा काकांना 2 कार्टन तेल द्या, एकूण 2400 रुपये
            BENGALI  | শর্মা জিকে 2 কার্টন তেল দিন, মোট 2400 টাকা
            TAMIL    | சர்மா ஜிக்கு 2 அட்டை எண்ணெய் கொடுக்கவும், மொத்தம் 2400 ரூபாய்
            """)
    void extractsTotalInEachLanguageWithoutGuessing(
            in.smarttech.dukaandost.domain.NoteLanguage language, String note) {
        ExtractionResult result = extractionService.extract(note, language);

        assertThat(result.validationResult().normalizedOrder().total()).isEqualByComparingTo("2400");
        assertThat(result.validationResult().normalizedOrder().customerName()).isNotNull();
        assertThat(result.validationResult().normalizedOrder().preferredReminderLanguage()).isNull();
    }

    @Test
    void extractsHashtagItemWithoutGuessingCustomer() {
        ExtractionResult result = extractionService.extract("#chini - 150");

        assertThat(result.validationResult().normalizedOrder().customerName()).isNull();
        assertThat(result.validationResult().normalizedOrder().total()).isEqualByComparingTo("150");
        assertThat(result.validationResult().normalizedOrder().items()).anySatisfy(item ->
                assertThat(item.description().toLowerCase()).contains("chini"));
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.reviewNotes()).isNotEmpty();
    }

    @Test
    void extractsFridayAdvanceAcceptanceTranscript() {
        ExtractionResult result = extractionService.extract(
                "Sharma ji ko 2 carton tel dena, total 2400 rupaye. 500 advance de diya, baaki Friday ko.");

        assertThat(result.validationResult().normalizedOrder().total()).isEqualByComparingTo("2400");
        assertThat(result.validationResult().normalizedOrder().advanceAmount()).isEqualByComparingTo("500");
        assertThat(result.validationResult().normalizedOrder().dueDate().toString()).isEqualTo("2026-10-16");
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.reviewNotes()).isNotEmpty();
    }

    @Test
    void doesNotInventTotalWhenAcceptanceTranscriptOmitsIt() {
        ExtractionResult result = extractionService.extract(
                "Ramesh ke liye chawal aur dal bhejna, hisaab baad mein kar lenge.");

        assertThat(result.validationResult().normalizedOrder().total()).isNull();
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.validationResult().canConfirm()).isFalse();
    }

    @Test
    void classifiesExistingBalanceAndResolvesKalAsTomorrow() {
        ExtractionResult result = extractionService.extract(
                "Gupta store ka 800 rupaye baaki hai, kal tak de denge.");

        assertThat(result.validationResult().normalizedOrder().entryType()).isEqualTo(OrderEntryType.PREVIOUS_BALANCE);
        assertThat(result.validationResult().normalizedOrder().total()).isEqualByComparingTo("800");
        assertThat(result.validationResult().normalizedOrder().dueDate().toString()).isEqualTo("2026-10-16");
        assertThat(result.reviewRequired()).isTrue();
        assertThat(validationService.prepareForConfirmation(result.validationResult().normalizedOrder())
                .normalizedOrder().items().getFirst().description()).isEqualTo("Previous balance");
    }

    @Test
    void flagsRatherThanMergesMultipleCustomers() {
        ExtractionResult result = extractionService.extract(
                "Meena ko 5 kilo atta 250 ka aur Salim bhai ko 2 dabbe chai 320 ke; Meena ne 100 de diye.");

        assertThat(result.multipleCustomers()).isTrue();
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.validationResult().normalizedOrder().customerName()).isNull();
        assertThat(result.validationResult().normalizedOrder().items()).isEmpty();
        assertThat(result.reviewNotes()).anySatisfy(note -> assertThat(note).contains("one customer", "rerun"));
    }

    @Test
    void flagsArithmeticMismatchEvenIfTheModelDoesNotRequestReview() {
        ExtractionResult result = extractionService.extract(
                "Verma ji ke 3 packet cheeni 150 each, total 400 likh do.");

        assertThat(result.validationResult().normalizedOrder().total()).isEqualByComparingTo("400");
        assertThat(result.validationResult().hasIssue(ValidationCode.ITEM_TOTAL_MISMATCH)).isTrue();
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.reviewNotes()).anySatisfy(note -> assertThat(note).contains("₹450", "₹400"));
    }

    private static boolean present(String environmentVariable) {
        String value = System.getenv(environmentVariable);
        return value != null && !value.isBlank();
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ExtractionService.class, ValidationService.class})
    @ImportAutoConfiguration({
            JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            RestClientAutoConfiguration.class,
            WebClientAutoConfiguration.class,
            SpringAiRetryAutoConfiguration.class,
            ToolCallingAutoConfiguration.class,
            ChatClientAutoConfiguration.class,
            OpenAiChatAutoConfiguration.class})
    static class ExtractionTestConfiguration {

        @Bean
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-15T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
        }
    }
}
