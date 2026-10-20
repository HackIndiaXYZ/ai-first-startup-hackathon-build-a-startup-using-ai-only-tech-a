package in.smarttech.dukaandost.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.smarttech.dukaandost.domain.DraftStatus;
import in.smarttech.dukaandost.domain.OrderDraft;
import in.smarttech.dukaandost.domain.SourceType;
import in.smarttech.dukaandost.service.DraftWorkflowService;
import in.smarttech.dukaandost.service.ExtractionFailedException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DraftControllerTest {

    @Mock
    private DraftWorkflowService workflowService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        mockMvc = MockMvcBuilders.standaloneSetup(new DraftController(workflowService, objectMapper))
                .build();
    }

    @Test
    void createsDraftThroughPostEndpoint() throws Exception {
        OrderDraft draft = new OrderDraft("Asha ka order", SourceType.TEXT, DraftStatus.READY);
        when(workflowService.create("Asha ka order", null)).thenReturn(draft);

        mockMvc.perform(post("/api/drafts")
                        .contentType("application/json")
                        .content("""
                                {"transcript":"Asha ka order"}
                                """))
                .andExpect(status().isCreated());

        verify(workflowService).create("Asha ka order", null);
    }

    @Test
    void rejectsBlankTranscriptAtTheHttpBoundary() throws Exception {
        mockMvc.perform(post("/api/drafts")
                        .contentType("application/json")
                        .content("""
                                {"transcript":" "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void viewsDraftThroughGetEndpoint() throws Exception {
        UUID id = UUID.randomUUID();
        OrderDraft draft = new OrderDraft("saved", SourceType.TEXT, DraftStatus.READY);
        when(workflowService.get(id)).thenReturn(draft);

        mockMvc.perform(get("/api/drafts/{id}", id))
                .andExpect(status().isOk());

        verify(workflowService).get(id);
    }

    @Test
    void returnsSavedDraftIdAndFriendlyErrorWhenExtractionFails() throws Exception {
        UUID draftId = UUID.randomUUID();
        when(workflowService.create("bad provider input", null))
                .thenThrow(new ExtractionFailedException(draftId));

        mockMvc = MockMvcBuilders.standaloneSetup(new DraftController(workflowService, objectMapper))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        mockMvc.perform(post("/api/drafts")
                        .contentType("application/json")
                        .content("""
                                {"transcript":"bad provider input"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(
                        "Couldn't read this note. Your text is saved. You can retry "
                                + "or enter the details manually."))
                .andExpect(jsonPath("$.draftId").value(draftId.toString()));
    }

    @Test
    void retryEndpointReturns502WithDraftIdWhenExtractionFailsAgain() throws Exception {
        UUID draftId = UUID.randomUUID();
        when(workflowService.retryExtraction(draftId, null, null))
                .thenThrow(new ExtractionFailedException(draftId));

        mockMvc = MockMvcBuilders.standaloneSetup(new DraftController(workflowService, objectMapper))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        mockMvc.perform(post("/api/drafts/" + draftId + "/extract"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.draftId").value(draftId.toString()));
    }

    @Test
    void passesLanguageChoiceAndReturnsOriginalTranscript() throws Exception {
        OrderDraft draft = new OrderDraft("शर्मा जी 500", SourceType.TEXT, DraftStatus.READY);
        draft.setLanguageHint(in.smarttech.dukaandost.domain.NoteLanguage.HINDI);
        when(workflowService.create("शर्मा जी 500", in.smarttech.dukaandost.domain.NoteLanguage.HINDI))
                .thenReturn(draft);

        mockMvc.perform(post("/api/drafts")
                        .contentType("application/json; charset=UTF-8")
                        .content("{\"transcript\":\"शर्मा जी 500\",\"language\":\"HINDI\"}"))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.language").value("HINDI"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.originalTranscript").value("शर्मा जी 500"));
    }

    @Test
    void rejectsUnsupportedLanguageChoice() throws Exception {
        mockMvc.perform(post("/api/drafts")
                        .contentType("application/json")
                        .content("{\"transcript\":\"x\",\"language\":\"KLINGON\"}"))
                .andExpect(status().isBadRequest());
    }
}
