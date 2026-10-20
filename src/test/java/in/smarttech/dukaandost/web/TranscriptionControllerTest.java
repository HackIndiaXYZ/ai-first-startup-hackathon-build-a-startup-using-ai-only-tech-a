package in.smarttech.dukaandost.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.smarttech.dukaandost.domain.NoteLanguage;
import in.smarttech.dukaandost.service.VoiceTranscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TranscriptionControllerTest {

    @Mock
    private VoiceTranscriptionService transcriptionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TranscriptionController(transcriptionService)).build();
    }

    @Test
    void acceptsAudioUploadAndReturnsTranscriptForEditing() throws Exception {
        byte[] audio = {1, 2, 3};
        when(transcriptionService.transcribe(audio, "audio/wav", NoteLanguage.HINDI))
                .thenReturn(new VoiceTranscriptionService.Transcription("नमस्ते", "Hindi"));

        mockMvc.perform(multipart("/api/transcription")
                        .file(new MockMultipartFile("audio", "note.wav", "audio/wav", audio))
                        .param("language", "HINDI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript").value("नमस्ते"))
                .andExpect(jsonPath("$.detectedLanguage").value("Hindi"));

        verify(transcriptionService).transcribe(audio, "audio/wav", NoteLanguage.HINDI);
    }
}
