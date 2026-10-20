package in.smarttech.dukaandost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.smarttech.dukaandost.domain.NoteLanguage;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class VoiceTranscriptionServiceTest {

    private final AudioTranscriptionProvider provider = mock(AudioTranscriptionProvider.class);
    private final VoiceTranscriptionService service = new VoiceTranscriptionService(provider);

    static Stream<String> acceptedAudioTypes() {
        return Stream.of(
                "audio/aac", "audio/aiff", "audio/flac", "audio/mp4", "audio/mpeg",
                "audio/mp3", "audio/ogg", "audio/wav", "audio/webm");
    }

    @ParameterizedTest
    @MethodSource("acceptedAudioTypes")
    void acceptsDocumentedAudioTypesAndPassesLanguageHint(String contentType) {
        byte[] bytes = {1, 2, 3};
        when(provider.transcribe(bytes, contentType, NoteLanguage.HINDI))
                .thenReturn(new AudioTranscriptionProvider.TranscriptionResult(
                        " Sharma ji ko 500 rupaye dene hain. ", "Hinglish", true));

        VoiceTranscriptionService.Transcription result = service.transcribe(bytes, contentType, NoteLanguage.HINDI);

        assertThat(result.transcript()).isEqualTo("Sharma ji ko 500 rupaye dene hain.");
        assertThat(result.detectedLanguage()).isEqualTo("Hinglish");
        verify(provider).transcribe(bytes, contentType, NoteLanguage.HINDI);
    }

    @ParameterizedTest
    @EnumSource(value = NoteLanguage.class, names = {
            "AUTO", "HINDI", "HINGLISH", "ENGLISH", "MARATHI", "BENGALI", "TAMIL"
    })
    void forwardsEachDocumentedLanguageHint(NoteLanguage language) {
        byte[] bytes = {1};
        when(provider.transcribe(bytes, "audio/wav", language))
                .thenReturn(new AudioTranscriptionProvider.TranscriptionResult("note", "English", true));

        assertThat(service.transcribe(bytes, "audio/wav", language).transcript()).isEqualTo("note");
        verify(provider).transcribe(bytes, "audio/wav", language);
    }

    @Test
    void normalizesBrowserAudioMimeParametersAndAliases() {
        byte[] bytes = {1};
        when(provider.transcribe(bytes, "audio/webm", NoteLanguage.AUTO))
                .thenReturn(new AudioTranscriptionProvider.TranscriptionResult("note", "English", true));

        assertThat(service.transcribe(bytes, "audio/webm;codecs=opus", null).transcript()).isEqualTo("note");
    }

    @Test
    void rejectsUnsupportedAudioFormatsAndOversizedRecordingsBeforeProviderCall() {
        assertThatThrownBy(() -> service.transcribe(new byte[] {1}, "application/pdf", NoteLanguage.AUTO))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported audio format");
        assertThatThrownBy(() -> service.transcribe(new byte[VoiceTranscriptionService.MAX_AUDIO_BYTES + 1],
                "audio/wav", NoteLanguage.AUTO))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("10 MB");
    }

    @Test
    void givesClearMessageWhenLanguageIsUnsupportedOrAudioCannotBeRead() {
        when(provider.transcribe(new byte[] {1}, "audio/wav", NoteLanguage.AUTO))
                .thenReturn(new AudioTranscriptionProvider.TranscriptionResult("", "unsupported", false));

        assertThatThrownBy(() -> service.transcribe(new byte[] {1}, "audio/wav", NoteLanguage.AUTO))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("language is unsupported");
    }

    @Test
    void givesFriendlyErrorWhenProviderFails() {
        when(provider.transcribe(new byte[] {1}, "audio/wav", NoteLanguage.AUTO))
                .thenThrow(new IllegalStateException("provider internal detail"));

        assertThatThrownBy(() -> service.transcribe(new byte[] {1}, "audio/wav", NoteLanguage.AUTO))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException status = (ResponseStatusException) exception;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(status.getReason()).contains("Transcription failed");
                    assertThat(status.getReason()).doesNotContain("provider internal detail");
                });
    }
}
