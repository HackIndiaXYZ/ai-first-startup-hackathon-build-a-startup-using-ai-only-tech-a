package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;

import in.smarttech.dukaandost.DukaanDostApplication;
import in.smarttech.dukaandost.domain.OrderEntryType;
import in.smarttech.dukaandost.validation.ValidationCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@SpringBootTest(
        classes = DukaanDostApplication.class,
        properties = "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
@Import(ExtractionServiceModelIntegrationTest.FixedClockConfiguration.class)
@EnabledIf("isApiKeyConfigured")
class ExtractionServiceModelIntegrationTest {

    @Autowired
    private ExtractionService extractionService;

    @Autowired
    private ValidationService validationService;

    static boolean isApiKeyConfigured() {
        return present("OPENAI_API_KEY") || present("SPRING_AI_OPENAI_API_KEY");
    }

    @Test
    void extractsFridayAdvanceAcceptanceTranscript() {
        ExtractionResult result = extractionService.extract(
                "Sharma ji ko 2 carton tel dena, total 2400 rupaye. 500 advance de diya, baaki Friday ko.");

        assertThat(result.validationResult().normalizedOrder().total()).isEqualByComparingTo("2400");
        assertThat(result.validationResult().normalizedOrder().advanceAmount()).isEqualByComparingTo("500");
        assertThat(result.validationResult().normalizedOrder().dueDate().toString()).isEqualTo("2026-10-16");
        assertThat(result.reviewRequired()).isTrue();
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

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-15T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
        }
    }
}
