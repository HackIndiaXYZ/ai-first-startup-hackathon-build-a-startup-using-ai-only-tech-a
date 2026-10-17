package in.smarttech.dukaandost.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.smarttech.dukaandost.domain.DraftStatus;
import in.smarttech.dukaandost.domain.OrderDraft;
import in.smarttech.dukaandost.domain.SourceType;
import in.smarttech.dukaandost.service.DraftWorkflowService;
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
        when(workflowService.create("Asha ka order")).thenReturn(draft);

        mockMvc.perform(post("/api/drafts")
                        .contentType("application/json")
                        .content("""
                                {"transcript":"Asha ka order"}
                                """))
                .andExpect(status().isCreated());

        verify(workflowService).create("Asha ka order");
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
}
