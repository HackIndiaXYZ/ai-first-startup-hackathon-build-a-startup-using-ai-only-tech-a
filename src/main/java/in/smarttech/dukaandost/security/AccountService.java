package in.smarttech.dukaandost.security;

import in.smarttech.dukaandost.domain.Owner;
import in.smarttech.dukaandost.domain.EmailVerificationToken;
import in.smarttech.dukaandost.domain.PasswordResetToken;
import in.smarttech.dukaandost.repository.EmailVerificationTokenRepository;
import in.smarttech.dukaandost.repository.OwnerRepository;
import in.smarttech.dukaandost.repository.PasswordResetTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private final OwnerRepository owners;
    private final PasswordResetTokenRepository tokens;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordEncoder passwordEncoder;
    private final ResetMailSender mailSender;
    private final Clock clock;
    private final Duration tokenTtl;

    public AccountService(OwnerRepository owners, PasswordResetTokenRepository tokens,
                          EmailVerificationTokenRepository verificationTokens,
                          PasswordEncoder passwordEncoder, ResetMailSender mailSender, Clock clock,
                          @Value("${app.security.reset-token-ttl:30m}") Duration tokenTtl) {
        this.owners = owners;
        this.tokens = tokens;
        this.verificationTokens = verificationTokens;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    public void signUp(String name, String email, String password, String phone,
                       String street, String city, String state, String postalCode) {
        requireMailConfiguration("Email verification");
        String normalized = normalizeEmail(email);
        Owner existing = owners.findByEmail(normalized).orElse(null);
        if (existing != null) {
            if (!existing.isEmailVerified()) {
                sendVerificationEmail(existing);
            }
            return;
        }
        String cleanPhone = normalizePhone(phone);
        Owner owner = new Owner(name.strip(), normalized, passwordEncoder.encode(password), cleanPhone);
        owner.setStreet(cleanOptional(street));
        owner.setCity(cleanOptional(city));
        owner.setState(cleanOptional(state));
        owner.setPostalCode(cleanOptional(postalCode));
        try {
            owners.saveAndFlush(owner);
        } catch (DataIntegrityViolationException exception) {
            if (owners.existsByEmail(normalized)) {
                return;
            }
            throw exception;
        }
        sendVerificationEmail(owner);
    }

    /**
     * Emails a single-use reset link. Unknown addresses get the same outcome as known ones, but an
     * unconfigured mail setup is reported honestly instead of pretending a mail was sent.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        if (!mailSender.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Password reset email is not set up on this server. Please contact the administrator.");
        }
        Owner owner = owners.findByEmail(normalizeEmail(email)).orElse(null);
        if (owner == null) {
            return;
        }
        Instant now = clock.instant();
        tokens.invalidateAllForOwner(owner.getId(), now);
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokens.save(new PasswordResetToken(owner.getId(), hash(token), now.plus(tokenTtl), now));
        try {
            mailSender.sendResetLink(owner.getEmail(), owner.getName(), token, tokenTtl);
        } catch (RuntimeException exception) {
            log.error("Password reset email failed: {}", exception.getClass().getName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "We could not send the reset email right now. Please try again later.");
        }
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        String hash = hash(token);
        Instant now = clock.instant();
        // Claiming first makes the token single-use even for concurrent requests.
        if (tokens.claim(hash, now) != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This reset link is invalid or has expired. Please request a new one.");
        }
        PasswordResetToken stored = tokens.findByTokenHash(hash).orElseThrow();
        Owner owner = owners.findById(stored.getOwnerId()).orElseThrow();
        owner.setPasswordHash(passwordEncoder.encode(newPassword));
        owner.setPasswordLoginEnabled(true);
        owners.save(owner);
        tokens.invalidateAllForOwner(owner.getId(), now);
    }

    @Transactional
    public void verifyEmail(String token) {
        String tokenHash = hash(token);
        Instant now = clock.instant();
        if (verificationTokens.claim(tokenHash, now) != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This verification link is invalid or has expired. Request a new account verification email.");
        }
        EmailVerificationToken stored = verificationTokens.findByTokenHash(tokenHash).orElseThrow();
        Owner owner = owners.findById(stored.getOwnerId()).orElseThrow();
        owner.setEmailVerified(true);
        owners.save(owner);
        verificationTokens.invalidateAllForOwner(owner.getId(), now);
    }

    @Transactional
    public Owner signInWithGoogle(String subject, String email, String name, String generatedPassword) {
        String normalized = normalizeEmail(email);
        Owner owner = owners.findByEmail(normalized).orElse(null);
        if (owner == null) {
            owner = new Owner(name, normalized, passwordEncoder.encode(generatedPassword), null);
            owner.setPasswordLoginEnabled(false);
        } else if (owner.getGoogleSubject() != null && !Objects.equals(owner.getGoogleSubject(), subject)) {
            throw new IllegalArgumentException("Google account is already linked to a different identity.");
        }
        owner.setGoogleSubject(subject);
        owner.setEmailVerified(true);
        return owners.save(owner);
    }

    private void sendVerificationEmail(Owner owner) {
        Instant now = clock.instant();
        verificationTokens.invalidateAllForOwner(owner.getId(), now);
        String token = newRawToken();
        verificationTokens.save(new EmailVerificationToken(owner.getId(), hash(token), now.plus(tokenTtl), now));
        try {
            mailSender.sendVerificationLink(owner.getEmail(), owner.getName(), token, tokenTtl);
        } catch (RuntimeException exception) {
            log.error("Email verification message failed: {}", exception.getClass().getName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "We could not send the verification email. Check SMTP setup and try again.");
        }
    }

    private void requireMailConfiguration(String purpose) {
        if (!mailSender.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    purpose + " is unavailable until SMTP is configured (SPRING_MAIL_HOST, "
                            + "SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD, MAIL_FROM and APP_BASE_URL).");
        }
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String normalized = phone.strip().replaceAll("[ ()-]", "");
        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Phone must include a country code, for example +919876543210.");
        }
        return normalized;
    }

    private static String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String newRawToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

}
