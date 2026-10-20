package in.smarttech.dukaandost.service;

import in.smarttech.dukaandost.domain.NoteLanguage;

public interface AudioTranscriptionProvider {

    TranscriptionResult transcribe(byte[] audio, String contentType, NoteLanguage language);

    record TranscriptionResult(String transcript, String detectedLanguage, boolean supported) {
    }
}
