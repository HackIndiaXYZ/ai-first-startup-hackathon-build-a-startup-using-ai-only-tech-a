package in.smarttech.dukaandost.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> signUp(@Valid @RequestBody SignUpRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Passwords do not match.");
        }
        accounts.signUp(request.name(), request.email(), request.password(), request.phone(),
                request.street(), request.city(), request.state(), request.postalCode());
        return Map.of("message", "If this address can be registered, email verification instructions will be sent.");
    }

    @GetMapping("/verify")
    public Map<String, String> verifyEmail(@RequestParam @NotBlank @Size(max = 200) String token) {
        accounts.verifyEmail(token);
        return Map.of("message", "Email verified. You can sign in now.");
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        accounts.requestPasswordReset(request.email());
        return Map.of("message", "If an account exists for that email, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accounts.resetPassword(request.token(), request.password());
        return Map.of("message", "Your password has been changed. You can sign in now.");
    }

    // BCrypt only reads the first 72 bytes, so longer passwords are rejected rather than silently truncated.
    interface PasswordHolder {
        String password();

        @AssertTrue(message = "password must be 8 to 72 characters")
        default boolean isPasswordLengthValid() {
            String value = password();
            return value != null && value.length() >= 8 && value.getBytes(StandardCharsets.UTF_8).length <= 72;
        }

        @AssertTrue(message = "password must include uppercase, lowercase, numeric and symbol characters")
        default boolean isPasswordStrongEnough() {
            String value = password();
            return value != null && value.matches("(?s).*[A-Z].*")
                    && value.matches("(?s).*[a-z].*")
                    && value.matches("(?s).*\\d.*")
                    && value.matches("(?s).*[^A-Za-z0-9].*");
        }
    }

    public record SignUpRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Email @Size(max = 254) String email,
            String password,
            @NotBlank String confirmPassword,
            @Pattern(regexp = "^(\\+?[0-9 ()-]{7,20})?$", message = "phone must be a valid phone number")
            String phone,
            @Size(max = 200) String street,
            @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Pattern(regexp = "^(|[0-9]{6})$", message = "postal code must be a 6-digit PIN")
            String postalCode) implements PasswordHolder {
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 200) String token, String password)
            implements PasswordHolder {
    }
}
