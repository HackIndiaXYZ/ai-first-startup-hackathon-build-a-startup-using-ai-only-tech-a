package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.smarttech.dukaandost.domain.NoteLanguage;
import in.smarttech.dukaandost.validation.ExtractedOrder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Mocked-model tests: they prove language handling in our code (transcript passthrough, prompt hint,
 * mapping, unreliable-language handling). They do not prove the model understands a language; see
 * ExtractionServiceModelIntegrationTest for the live-model checks.
 */
class MultilingualExtractionTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-15T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
    private final List<String> systemPrompts = new ArrayList<>();

    private static String response(String language, String name, int total, boolean reliable) {
        return """
                {"customerName": %s, "customerPhone": null, "sourceNoteLanguage": "%s",
                 "preferredReminderLanguage": null, "entryType": "ORDER", "orderDate": null,
                 "dueDate": null, "total": %s, "currency": "INR", "items": [],
                 "advanceAmount": null, "advancePaidAt": null, "advanceNote": null,
                 "multipleCustomers": false, "reviewRequired": false, "reviewNotes": [],
                 "languageReliable": %s}
                """.formatted(name == null ? "null" : "\"" + name + "\"", language,
                reliable ? String.valueOf(total) : "null", reliable);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            HINDI    | शर्मा जी को 2 कार्टन तेल देना, कुल 2400 रुपये
            HINGLISH | Sharma ji ko 2 carton tel dena, total 2400 rupaye
            ENGLISH  | Send 2 cartons of oil to Sharma ji, total 2400 rupees
            MARATHI  | शर्मा काकांना 2 कार्टन तेल द्या, एकूण 2400 रुपये
            BENGALI  | শর্মা জিকে 2 কার্টন তেল দিন, মোট 2400 টাকা
            TAMIL    | சர்மா ஜிக்கு 2 அட்டை எண்ணெய் கொடுக்கவும், மொத்தம் 2400 ரூபாய்
            """)
    void passesTranscriptAndLanguageHintThroughWithoutAlteringText(NoteLanguage language, String note) {
        ExtractionService service = service(response(language.name(), "Sharma ji", 2400, true));

        ExtractionResult result = service.extract(note, language);

        ExtractedOrder order = result.validationResult().normalizedOrder();
        assertThat(order.transcript()).isEqualTo(note);
        assertThat(order.total()).isEqualByComparingTo("2400");
        assertThat(order.sourceNoteLanguage()).isEqualTo(language.name());
        assertThat(order.preferredReminderLanguage()).isNull();
        assertThat(systemPrompts.getLast()).contains(language.promptName());
    }

    @Test
    void autoDetectAsksTheModelToDetectAndMixedLanguageStillMapsFields() {
        ExtractionService service = service(response("HINGLISH", "Ramesh", 300, true));

        ExtractionResult result = service.extract("Ramesh ke liye ५०० ka saman, total 300 likh do");

        assertThat(systemPrompts.getLast()).contains("Detect it");
        assertThat(result.validationResult().normalizedOrder().customerName()).isEqualTo("Ramesh");
    }

    @Test
    void unreadableLanguageReturnsClearNoteAndNoGuessedFields() {
        ExtractionService service = service(response("unknown", "Guess Name", 999, false));

        ExtractionResult result = service.extract("ਸ਼ਰਮਾ ਜੀ ਨੂੰ ਤੇਲ", NoteLanguage.AUTO);

        ExtractedOrder order = result.validationResult().normalizedOrder();
        assertThat(order.customerName()).isNull();
        assertThat(order.total()).isNull();
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.reviewNotes()).anySatisfy(note -> assertThat(note).contains("could not be read reliably"));
        assertThat(order.transcript()).isEqualTo("ਸ਼ਰਮਾ ਜੀ ਨੂੰ ਤੇਲ");
    }

    @Test
    void reminderLanguageIsNotCopiedFromNoteLanguage() {
        String json = response("TAMIL", "Kumar", 100, true)
                .replace("\"preferredReminderLanguage\": null", "\"preferredReminderLanguage\": \"Hindi\"");

        ExtractedOrder order = service(json).extract("குமார் 100", NoteLanguage.TAMIL)
                .validationResult().normalizedOrder();

        assertThat(order.sourceNoteLanguage()).isEqualTo("TAMIL");
        assertThat(order.preferredReminderLanguage()).isEqualTo("Hindi");
    }

    private ExtractionService service(String json) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClientRequestSpec prompt = mock(ChatClientRequestSpec.class);
        CallResponseSpec call = mock(CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(prompt);
        when(prompt.system(anyString())).thenAnswer(invocation -> {
            systemPrompts.add(invocation.getArgument(0));
            return prompt;
        });
        when(prompt.user(anyString())).thenReturn(prompt);
        when(prompt.options(any(OpenAiChatOptions.class))).thenReturn(prompt);
        when(prompt.call()).thenReturn(call);
        when(call.content()).thenReturn(json);
        return new ExtractionService(builder, new ObjectMapper().findAndRegisterModules(),
                new ValidationService(CLOCK), CLOCK);
    }
}
