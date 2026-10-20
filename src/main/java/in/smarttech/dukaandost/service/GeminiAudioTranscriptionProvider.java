package in.smarttech.dukaandost.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.smarttech.dukaandost.domain.NoteLanguage;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GeminiAudioTranscriptionProvider implements AudioTranscriptionProvider {

    private static final String TRANSCRIPTION_PROMPT = """
            Transcribe the spoken words exactly without translating or summarizing.
            Preserve the speaker's original language and any natural Hindi-English mixing.
            Do not infer order details or fill in unclear words. Return only JSON with:
            supported (boolean), detectedLanguage (one of Hindi, Hinglish, English, Marathi,
            Bengali, Tamil or unsupported), and transcript (string).
            If the audio is inaudible, empty, or primarily in a language outside that list,
            set supported=false and transcript to an empty string.
            """;

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiAudioTranscriptionProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.transcription.api-key:${GEMINI_API_KEY:}}") String apiKey,
            @Value("${app.transcription.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${app.transcription.model:${spring.ai.openai.chat.options.model:gemini-3.1-flash-lite}}") String model) {
        this.client = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public TranscriptionResult transcribe(byte[] audio, String contentType, NoteLanguage language) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Voice transcription is not configured. Set GEMINI_API_KEY or type the order instead.");
        }
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("text", prompt(language)),
                        Map.of("inline_data", Map.of(
                                "mime_type", contentType,
                                "data", Base64.getEncoder().encodeToString(audio)))))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "supported", Map.of("type", "BOOLEAN"),
                                        "detectedLanguage", Map.of("type", "STRING"),
                                        "transcript", Map.of("type", "STRING")),
                                "required", List.of("supported", "detectedLanguage", "transcript"))));
        JsonNode response = client.post()
                .uri("/v1beta/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        String content = response == null
                ? null : response.path("candidates").path(0).path("content").path("parts").path(0).path("text")
                        .asText(null);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Transcription provider returned no text.");
        }
        try {
            JsonNode result = objectMapper.readTree(content);
            return new TranscriptionResult(
                    result.path("transcript").asText(""),
                    result.path("detectedLanguage").asText("unsupported"),
                    result.path("supported").asBoolean(false));
        } catch (Exception exception) {
            throw new IllegalStateException("Transcription provider returned an invalid response.", exception);
        }
    }

    private static String prompt(NoteLanguage language) {
        String instruction = language == null || language == NoteLanguage.AUTO
                ? "Detect the spoken language."
                : "The owner's language hint is " + language.promptName() + ".";
        return TRANSCRIPTION_PROMPT + "\n" + instruction;
    }
}
