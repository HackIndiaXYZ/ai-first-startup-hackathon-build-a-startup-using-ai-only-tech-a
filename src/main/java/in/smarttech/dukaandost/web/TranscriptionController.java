package in.smarttech.dukaandost.web;

import in.smarttech.dukaandost.domain.NoteLanguage;
import in.smarttech.dukaandost.service.VoiceTranscriptionService;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/transcription")
public class TranscriptionController {

    private final VoiceTranscriptionService transcriptionService;

    public TranscriptionController(VoiceTranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public TranscriptionResponse transcribe(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "language", required = false, defaultValue = "AUTO") NoteLanguage language) {
        try {
            VoiceTranscriptionService.Transcription transcription = transcriptionService.transcribe(
                    audio.getBytes(), audio.getContentType(), language);
            return new TranscriptionResponse(transcription.transcript(), transcription.detectedLanguage());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The uploaded voice note could not be read. Choose the file again or type the order.");
        }
    }

    public record TranscriptionResponse(String transcript, String detectedLanguage) {
    }
}
