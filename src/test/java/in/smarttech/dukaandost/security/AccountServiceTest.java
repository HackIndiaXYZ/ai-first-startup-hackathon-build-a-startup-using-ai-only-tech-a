package in.smarttech.dukaandost.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.smarttech.dukaandost.domain.Owner;
import in.smarttech.dukaandost.domain.EmailVerificationToken;
import in.smarttech.dukaandost.domain.PasswordResetToken;
import in.smarttech.dukaandost.repository.EmailVerificationTokenRepository;
import in.smarttech.dukaandost.repository.OwnerRepository;
import in.smarttech.dukaandost.repository.PasswordResetTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");

    private final OwnerRepository owners = mock(OwnerRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final EmailVerificationTokenRepository verificationTokens =
            mock(EmailVerificationTokenRepository.class);
    private final ResetMailSender mail = mock(ResetMailSender.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(owners, tokens, verificationTokens, encoder, mail, Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(30));
    }

    @Test
    void emailsAreNormalized() {
        assertThat(AccountService.normalizeEmail("  Shop@Example.COM ")).isEqualTo("shop@example.com");
    }

    @Test
    void concurrentSignupRaceDoesNotRevealWhetherEmailWasRegistered() {
        when(mail.isConfigured()).thenReturn(true);
        when(owners.findByEmail("a@example.com")).thenReturn(Optional.empty());
        when(owners.existsByEmail("a@example.com")).thenReturn(true);
        when(owners.saveAndFlush(any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("uq_owners_email"));

        service.signUp("A", "A@example.com", "Strong-pass1!", null, null, null, null, null);

        verify(mail, never()).sendVerificationLink(anyString(), anyString(), anyString(), any());
    }

    @Test
    void signupSurfacesNonEmailDataIntegrityFailures() {
        when(mail.isConfigured()).thenReturn(true);
        when(owners.findByEmail("a@example.com")).thenReturn(Optional.empty());
        when(owners.existsByEmail("a@example.com")).thenReturn(false);
        when(owners.saveAndFlush(any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("other constraint"));

        assertThatThrownBy(() -> service.signUp(
                "A", "A@example.com", "Strong-pass1!", null, null, null, null, null))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void signUpStoresOnlyHashedVerificationTokenAndSendsTheLink() {
        when(mail.isConfigured()).thenReturn(true);
        when(owners.findByEmail("asha@example.com")).thenReturn(Optional.empty());
        when(owners.saveAndFlush(any())).thenAnswer(call -> {
            Owner owner = call.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(owner, "id", UUID.randomUUID());
            return owner;
        });

        service.signUp("Asha", " Asha@Example.com ", "Strong-pass1!", "+91 98765 43210",
                "Shop Road", "Pune", "Maharashtra", "411001");

        ArgumentCaptor<Owner> saved = ArgumentCaptor.forClass(Owner.class);
        verify(owners).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("asha@example.com");
        assertThat(saved.getValue().getPhone()).isEqualTo("+919876543210");
        assertThat(saved.getValue().isEmailVerified()).isFalse();
        ArgumentCaptor<EmailVerificationToken> stored =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(verificationTokens).save(stored.capture());
        ArgumentCaptor<String> emailedToken = ArgumentCaptor.forClass(String.class);
        verify(mail).sendVerificationLink(eq("asha@example.com"), eq("Asha"), emailedToken.capture(), any());
        assertThat(stored.getValue().getTokenHash()).isEqualTo(AccountService.hash(emailedToken.getValue()))
                .isNotEqualTo(emailedToken.getValue());
        assertThat(stored.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void verificationTokenIsClaimedOnceAndActivatesTheAccount() {
        UUID ownerId = UUID.randomUUID();
        Owner owner = mock(Owner.class);
        when(owner.getId()).thenReturn(ownerId);
        EmailVerificationToken token = new EmailVerificationToken(ownerId, AccountService.hash("verify-token"),
                NOW.plusSeconds(60), NOW);
        when(verificationTokens.claim(AccountService.hash("verify-token"), NOW)).thenReturn(1);
        when(verificationTokens.findByTokenHash(AccountService.hash("verify-token"))).thenReturn(Optional.of(token));
        when(owners.findById(ownerId)).thenReturn(Optional.of(owner));

        service.verifyEmail("verify-token");

        verify(owner).setEmailVerified(true);
        verify(owners).save(owner);
        verify(verificationTokens).invalidateAllForOwner(ownerId, NOW);
    }

    @Test
    void expiredVerificationTokenCannotActivateAnAccount() {
        when(verificationTokens.claim(anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.verifyEmail("expired"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        verify(owners, never()).save(any());
    }

    @Test
    void resetRequestStoresOnlyAHashAndEmailsTheRawTokenOnce() {
        Owner owner = new Owner("Asha", "asha@example.com", "hash", null);
        when(mail.isConfigured()).thenReturn(true);
        when(owners.findByEmail("asha@example.com")).thenReturn(Optional.of(owner));

        service.requestPasswordReset(" Asha@Example.com ");

        ArgumentCaptor<PasswordResetToken> stored = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(stored.capture());
        ArgumentCaptor<String> emailedToken = ArgumentCaptor.forClass(String.class);
        verify(mail).sendResetLink(eq("asha@example.com"), eq("Asha"), emailedToken.capture(), any());
        assertThat(stored.getValue().getTokenHash()).isEqualTo(AccountService.hash(emailedToken.getValue()))
                .isNotEqualTo(emailedToken.getValue());
        assertThat(stored.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void resetRequestFailsHonestlyWhenEmailIsNotConfigured() {
        when(mail.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.requestPasswordReset("asha@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        verify(tokens, never()).save(any());
        verify(mail, never()).sendResetLink(anyString(), anyString(), anyString(), any());
    }

    @Test
    void resetRequestReportsMailDeliveryFailure() {
        Owner owner = new Owner("Asha", "asha@example.com", "hash", null);
        when(mail.isConfigured()).thenReturn(true);
        when(owners.findByEmail("asha@example.com")).thenReturn(Optional.of(owner));
        doThrow(new IllegalStateException("smtp down")).when(mail).sendResetLink(anyString(), anyString(),
                anyString(), any());

        assertThatThrownBy(() -> service.requestPasswordReset("asha@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
    }

    @Test
    void unknownEmailSendsNothingButLooksIdentical() {
        when(mail.isConfigured()).thenReturn(true);
        when(owners.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.requestPasswordReset("ghost@example.com");

        verify(tokens, never()).save(any());
        verify(mail, never()).sendResetLink(anyString(), anyString(), anyString(), any());
    }

    @Test
    void validTokenChangesPasswordWithBcryptAndIsClaimedOnce() {
        UUID ownerId = UUID.randomUUID();
        Owner owner = mock(Owner.class);
        when(owner.getId()).thenReturn(ownerId);
        PasswordResetToken token = new PasswordResetToken(ownerId, AccountService.hash("raw"),
                NOW.plusSeconds(60), NOW);
        when(tokens.claim(AccountService.hash("raw"), NOW)).thenReturn(1);
        when(tokens.findByTokenHash(AccountService.hash("raw"))).thenReturn(Optional.of(token));
        when(owners.findById(ownerId)).thenReturn(Optional.of(owner));

        service.resetPassword("raw", "Brand-new-1!");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(owner).setPasswordHash(hash.capture());
        assertThat(hash.getValue()).startsWith("$2");
        assertThat(encoder.matches("Brand-new-1!", hash.getValue())).isTrue();
    }

    @Test
    void usedOrExpiredTokenIsRejectedWithoutChangingAnyPassword() {
        when(tokens.claim(anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.resetPassword("raw", "Brand-new-1!"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        verify(owners, never()).save(any());
    }
}
