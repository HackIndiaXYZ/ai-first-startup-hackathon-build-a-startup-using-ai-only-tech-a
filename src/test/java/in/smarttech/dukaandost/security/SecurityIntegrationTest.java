package in.smarttech.dukaandost.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.smarttech.dukaandost.domain.DraftStatus;
import in.smarttech.dukaandost.domain.Owner;
import in.smarttech.dukaandost.domain.OrderDraft;
import in.smarttech.dukaandost.domain.SourceType;
import in.smarttech.dukaandost.repository.OwnerRepository;
import in.smarttech.dukaandost.repository.EmailVerificationTokenRepository;
import in.smarttech.dukaandost.repository.PasswordResetTokenRepository;
import in.smarttech.dukaandost.service.DraftWorkflowService;
import in.smarttech.dukaandost.service.OrderPaymentService;
import in.smarttech.dukaandost.service.PaymentReminderService;
import in.smarttech.dukaandost.service.TodaySummaryService;
import in.smarttech.dukaandost.service.VoiceTranscriptionService;
import in.smarttech.dukaandost.web.ApiExceptionHandler;
import in.smarttech.dukaandost.web.DraftController;
import in.smarttech.dukaandost.web.OrderController;
import in.smarttech.dukaandost.web.TranscriptionController;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = {DraftController.class, OrderController.class, TranscriptionController.class,
        AuthController.class, AccountController.class},
        properties = {
                "app.rate-limit.drafts.max-requests=2",
                "app.rate-limit.drafts.window=60s",
                "app.rate-limit.login.max-failures=3",
                "app.rate-limit.login.window=15m",
                "app.rate-limit.account.max-requests=3",
                "app.rate-limit.account.window=15m"
        })
@Import({SecurityConfig.class, OwnerUserDetailsService.class, AccountService.class, LoginPageConfig.class,
        OwnerSessionService.class, ApiExceptionHandler.class, SecurityIntegrationTest.ClockConfig.class})
class SecurityIntegrationTest {

    private static final String EMAIL = "shop@example.com";
    private static final String PASSWORD = "correct-horse-battery";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.system(ZoneId.of("Asia/Kolkata"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private DraftWorkflowService workflowService;

    @MockitoBean
    private OrderPaymentService paymentService;

    @MockitoBean
    private PaymentReminderService reminderService;

    @MockitoBean
    private TodaySummaryService todaySummaryService;

    @MockitoBean
    private VoiceTranscriptionService voiceTranscriptionService;

    @MockitoBean
    private OwnerRepository owners;

    @MockitoBean
    private EmailVerificationTokenRepository verificationTokens;

    @MockitoBean
    private PasswordResetTokenRepository resetTokens;

    @MockitoBean
    private ResetMailSender mailSender;

    @BeforeEach
    void stubOwner() {
        Owner owner = new Owner("Asha", EMAIL, passwordEncoder.encode(PASSWORD), null);
        owner.setEmailVerified(true);
        when(owners.findByEmail(EMAIL)).thenReturn(Optional.of(owner));
    }

    private static RequestPostProcessor ip(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private MockHttpSession login(String address) throws Exception {
        var result = mockMvc.perform(post("/login").with(csrf()).with(ip(address))
                        .param("email", EMAIL).param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String json(String body) {
        return body;
    }

    @Test
    void apiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/orders/outstanding"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
        mockMvc.perform(post("/api/drafts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"transcript\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders/{id}/payment-reminder", java.util.UUID.randomUUID()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"ENGLISH\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/transcription").with(csrf())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uiRedirectsToLoginAndLoginUrlNoLongerReturns404() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login.html"));
        mockMvc.perform(get("/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html"));
    }

    @Test
    void publicPagesAreServedWithoutSignIn() throws Exception {
        for (String page : List.of("/login.html", "/signup.html", "/forgot-password.html",
                "/reset-password.html", "/verify-email.html", "/verify-email.js",
                "/auth.js", "/login.js", "/password-toggle.js", "/styles.css", "/logo.svg")) {
            mockMvc.perform(get(page)).andExpect(status().isOk());
        }
    }

    @Test
    void logoAndPasswordToggleLoadAsPublicStaticAssets() throws Exception {
        var logo = mockMvc.perform(get("/logo.svg").queryParam("v", "2"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(logo.getContentType()).startsWith("image/svg+xml");

        var toggle = mockMvc.perform(get("/password-toggle.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(toggle.getContentType()).contains("javascript");
        assertThat(toggle.getContentAsString()).contains("data-toggle-password");
    }

    @Test
    void authPagesContainBrandedWelcomeAndInlineValidationHooks() throws Exception {
        String signup = mockMvc.perform(get("/signup.html"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(signup).contains("auth-logo", "signup-welcome", "name-error",
                "password-error", "data-toggle-password");

        String login = mockMvc.perform(get("/login.html"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(login).contains("auth-logo", "login-email-error",
                "login-password-error", "data-toggle-password");
    }

    @Test
    void sessionEndpointIsPublicAndReportsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.csrfToken").isNotEmpty());
    }

    @Test
    void loginNormalizesEmailAndGrantsAccess() throws Exception {
        var result = mockMvc.perform(post("/login").with(csrf()).with(ip("10.0.0.1"))
                        .param("email", "  Shop@Example.COM ").param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        when(paymentService.outstanding()).thenReturn(List.of());
        mockMvc.perform(get("/api/orders/outstanding").session((MockHttpSession) result.getRequest().getSession(false)))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameRejection() throws Exception {
        mockMvc.perform(post("/login").with(csrf()).with(ip("10.0.0.2"))
                        .param("email", EMAIL).param("password", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect email or password."));
        when(owners.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        mockMvc.perform(post("/login").with(csrf()).with(ip("10.0.0.2"))
                        .param("email", "nobody@example.com").param("password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect email or password."));
    }

    @Test
    void loginWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/login").with(ip("10.0.0.3")).param("email", EMAIL).param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedPostWithoutCsrfIsForbiddenAndWithCsrfWorks() throws Exception {
        MockHttpSession session = login("10.0.0.4");
        when(workflowService.create(any(), any())).thenReturn(new OrderDraft("x", SourceType.TEXT, DraftStatus.READY));

        mockMvc.perform(post("/api/drafts").session(session).with(ip("10.0.0.4"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"transcript\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/drafts").session(session).with(csrf()).with(ip("10.0.0.4"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"transcript\":\"x\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = login("10.0.0.6");
        mockMvc.perform(post("/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/orders/outstanding").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void draftCreationIsRateLimitedPerClientIp() throws Exception {
        MockHttpSession session = login("10.0.0.5");
        when(workflowService.create(any(), any())).thenReturn(new OrderDraft("x", SourceType.TEXT, DraftStatus.READY));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/drafts").session(session).with(csrf()).with(ip("203.0.113.7"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"transcript\":\"x\"}"))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/drafts").session(session).with(csrf()).with(ip("203.0.113.7"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"transcript\":\"x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        mockMvc.perform(post("/api/drafts").session(session).with(csrf()).with(ip("203.0.113.8"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"transcript\":\"x\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void repeatedFailedLoginsAreThrottled() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/login").with(csrf()).with(ip("198.51.100.9"))
                            .param("email", EMAIL).param("password", "bad"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/login").with(csrf()).with(ip("198.51.100.9"))
                        .param("email", EMAIL).param("password", PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void signUpNormalizesEmailHashesPasswordWithBcryptAndNeverEchoesIt() throws Exception {
        when(mailSender.isConfigured()).thenReturn(true);
        when(owners.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(owners.saveAndFlush(any(Owner.class))).thenAnswer(call -> call.getArgument(0));

        mockMvc.perform(post("/api/auth/signup").with(csrf()).with(ip("192.0.2.10"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"name\":\" Ravi \",\"email\":\"New@Example.com\","
                                + "\"password\":\"Strong-pass1!\",\"confirmPassword\":\"Strong-pass1!\","
                                + "\"phone\":\"+91 98765 43210\"}")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        ArgumentCaptor<Owner> saved = ArgumentCaptor.forClass(Owner.class);
        verify(owners).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getValue().getName()).isEqualTo("Ravi");
        assertThat(saved.getValue().getPasswordHash()).startsWith("$2").isNotEqualTo("Strong-pass1!");
        assertThat(passwordEncoder.matches("Strong-pass1!", saved.getValue().getPasswordHash())).isTrue();
        assertThat(saved.getValue().isEmailVerified()).isFalse();
    }

    @Test
    void signUpRejectsDuplicateEmailInvalidInputAndMissingCsrf() throws Exception {
        when(mailSender.isConfigured()).thenReturn(true);
        mockMvc.perform(post("/api/auth/signup").with(csrf()).with(ip("192.0.2.11"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"email\":\"SHOP@example.com\","
                                + "\"password\":\"Strong-pass1!\",\"confirmPassword\":\"Strong-pass1!\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "If this address can be registered, email verification instructions will be sent."));
        mockMvc.perform(post("/api/auth/signup").with(csrf()).with(ip("192.0.2.11"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"email\":\"a@example.com\",\"password\":\"short\","
                                + "\"confirmPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/signup").with(csrf()).with(ip("192.0.2.11"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"email\":\"not-an-email\",\"password\":\"Strong-pass1!\","
                                + "\"confirmPassword\":\"Strong-pass1!\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/signup").with(ip("192.0.2.12"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"email\":\"a@example.com\",\"password\":\"Strong-pass1!\","
                                + "\"confirmPassword\":\"Strong-pass1!\"}"))
                .andExpect(status().isForbidden());
        verify(owners, never()).saveAndFlush(any());
    }

    @Test
    void signUpIsRateLimitedPerClientIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/signup").with(csrf()).with(ip("192.0.2.20"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/auth/signup").with(csrf()).with(ip("192.0.2.20"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void forgotPasswordReportsMissingEmailSetupInsteadOfFakingSuccess() throws Exception {
        when(mailSender.isConfigured()).thenReturn(false);
        mockMvc.perform(post("/api/auth/forgot-password").with(csrf()).with(ip("192.0.2.30"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"shop@example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").exists());
        verify(resetTokens, never()).save(any());
    }

    @Test
    void forgotPasswordGivesSameAnswerForKnownAndUnknownEmail() throws Exception {
        when(mailSender.isConfigured()).thenReturn(true);
        when(owners.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        for (String email : List.of(EMAIL, "nobody@example.com")) {
            mockMvc.perform(post("/api/auth/forgot-password").with(csrf()).with(ip("192.0.2.31"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").value(
                            "If an account exists for that email, a reset link has been sent."))
                    .andExpect(jsonPath("$.token").doesNotExist());
        }
    }

    @Test
    void resetWithUnknownOrUsedTokenIsRejected() throws Exception {
        when(resetTokens.claim(any(), any())).thenReturn(0);
        mockMvc.perform(post("/api/auth/reset-password").with(csrf()).with(ip("192.0.2.32"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"nope\",\"password\":\"Strong-pass1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        verify(owners, never()).save(any());
    }

    @Test
    void unverifiedOwnersCannotSignIn() throws Exception {
        Owner unverified = new Owner("Ravi", "pending@example.com", passwordEncoder.encode(PASSWORD), null);
        when(owners.findByEmail("pending@example.com")).thenReturn(Optional.of(unverified));
        mockMvc.perform(post("/login").with(csrf()).with(ip("192.0.2.41"))
                        .param("email", "pending@example.com").param("password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect email or password."));
    }
}
