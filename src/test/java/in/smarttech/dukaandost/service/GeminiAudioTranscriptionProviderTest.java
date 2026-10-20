package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.smarttech.dukaandost.domain.NoteLanguage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiAudioTranscriptionProviderTest {

    @Test
    void sendsAudioToConfiguredGeminiEndpointAndParsesStructuredTranscript() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(content().string(containsString("audio/wav")))
                .andRespond(withSuccess(
                        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"supported\\\":true,"
                                + "\\\"detectedLanguage\\\":\\\"Hindi\\\",\\\"transcript\\\":\\\"नमस्ते\\\"}\"}]}}]}",
                        MediaType.APPLICATION_JSON));

        GeminiAudioTranscriptionProvider provider = new GeminiAudioTranscriptionProvider(
                builder, new ObjectMapper(), "test-key", "https://generativelanguage.googleapis.com", "gemini-test");
        AudioTranscriptionProvider.TranscriptionResult result =
                provider.transcribe(new byte[] {1, 2}, "audio/wav", NoteLanguage.HINDI);

        assertThat(result.supported()).isTrue();
        assertThat(result.detectedLanguage()).isEqualTo("Hindi");
        assertThat(result.transcript()).isEqualTo("नमस्ते");
        server.verify();
    }
}
