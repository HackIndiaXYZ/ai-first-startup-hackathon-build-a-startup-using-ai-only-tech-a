package in.smarttech.dukaandost.service;

import in.smarttech.dukaandost.domain.NoteLanguage;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VoiceTranscriptionService {

    public static final int MAX_AUDIO_BYTES = 10 * 1024 * 1024;
    private static final Set<String> AUDIO_TYPES = Set.of(
            "audio/aac", "audio/aiff", "audio/flac", "audio/mp4", "audio/mpeg",
            "audio/mp3", "audio/ogg", "audio/wav", "audio/webm");
    private static final Set<NoteLanguage> SUPPORTED_LANGUAGES = Set.of(
            NoteLanguage.AUTO, NoteLanguage.HINDI, NoteLanguage.HINGLISH,
            NoteLanguage.ENGLISH, NoteLanguage.MARATHI, NoteLanguage.BENGALI, NoteLanguage.TAMIL);
    private static final Set<String> SUPPORTED_DETECTED_LANGUAGES = Set.of(
            "hindi", "hinglish", "english", "marathi", "bengali", "tamil");
    private static final String UNRELIABLE_MESSAGE =
            "This recording could not be transcribed reliably or its language is unsupported. "
                    + "Try a clearer recording in a supported language, or type the order instead.";
    private static final String FAILURE_MESSAGE =
            "Transcription failed. Your recording was not saved. Try again or type the order instead.";

    private final AudioTranscriptionProvider provider;

    public VoiceTranscriptionService(AudioTranscriptionProvider provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    public Transcription transcribe(byte[] audio, String contentType, NoteLanguage language) {
        if (audio == null || audio.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose or record a voice note first.");
        }
        if (audio.length > MAX_AUDIO_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Voice notes must be 10 MB or smaller.");
        }
        String normalizedType = normalizeContentType(contentType);
        if (!AUDIO_TYPES.contains(normalizedType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported audio format. Use WAV, MP3, MP4/M4A, WebM, OGG, AAC, AIFF or FLAC.");
        }
        NoteLanguage hint = language == null ? NoteLanguage.AUTO : language;
        if (!SUPPORTED_LANGUAGES.contains(hint)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a supported transcription language.");
        }

        AudioTranscriptionProvider.TranscriptionResult result;
        try {
            result = provider.transcribe(audio, normalizedType, hint);
        } catch (RuntimeException exception) {
            if (exception instanceof ResponseStatusException statusException) {
                throw statusException;
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, FAILURE_MESSAGE);
        }
        if (result == null || !result.supported() || result.transcript() == null
                || result.transcript().isBlank() || result.detectedLanguage() == null
                || !SUPPORTED_DETECTED_LANGUAGES.contains(result.detectedLanguage().strip().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, UNRELIABLE_MESSAGE);
        }
        return new Transcription(result.transcript().strip(), result.detectedLanguage());
    }

    static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String type = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "audio/x-wav", "audio/wave" -> "audio/wav";
            case "audio/x-m4a", "audio/m4a" -> "audio/mp4";
            case "audio/x-aiff" -> "audio/aiff";
            default -> type;
        };
    }

    public record Transcription(String transcript, String detectedLanguage) {
    }
}
