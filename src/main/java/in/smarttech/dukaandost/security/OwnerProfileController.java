package in.smarttech.dukaandost.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profile")
public class OwnerProfileController {

    private final OwnerProfileService profiles;

    public OwnerProfileController(OwnerProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public OwnerProfileService.ProfileResponse getProfile() {
        return profiles.getProfile();
    }

    @PutMapping
    public OwnerProfileService.ProfileResponse updateProfile(
            @Valid @RequestBody ProfileUpdateRequest request) {
        return profiles.updateProfile(new OwnerProfileService.ProfileUpdate(
                request.name(), request.phone(), request.shopName(), request.street(),
                request.city(), request.state(), request.postalCode()));
    }

    @PostMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OwnerProfileService.ProfileResponse uploadAvatar(@RequestPart("avatar") MultipartFile avatar) {
        return profiles.updateAvatar(avatar);
    }

    @GetMapping("/avatar")
    public ResponseEntity<byte[]> getAvatar() {
        byte[] bytes = profiles.avatar().bytes();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<Void> deleteAvatar() {
        profiles.deleteAvatar();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public java.util.Map<String, String> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
        }
        profiles.changePassword(request.currentPassword(), request.newPassword());
        return java.util.Map.of("message", "Password changed.");
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request, HttpServletRequest httpRequest) {
        profiles.deleteAccount(request.password());
        if (httpRequest.getSession(false) != null) {
            httpRequest.getSession(false).invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    public record ProfileUpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @Pattern(regexp = "^(\\+?[0-9 ()-]{7,20})?$") String phone,
            @Size(max = 200) String shopName,
            @Size(max = 200) String street,
            @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Pattern(regexp = "^(|[0-9]{6})$") String postalCode) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            String newPassword,
            @NotBlank String confirmPassword) implements OwnerPasswordHolder {
        @Override
        public String password() {
            return newPassword;
        }
    }

    public record DeleteAccountRequest(@NotBlank String password) {
    }

    interface OwnerPasswordHolder {
        String password();

        @AssertTrue(message = "password must be 8 to 72 characters and include uppercase, lowercase, numeric and symbol characters")
        default boolean isPasswordStrongEnough() {
            String value = password();
            return value != null && value.length() >= 8
                    && value.getBytes(StandardCharsets.UTF_8).length <= 72
                    && value.matches("(?s).*[A-Z].*") && value.matches("(?s).*[a-z].*")
                    && value.matches("(?s).*\\d.*") && value.matches("(?s).*[^A-Za-z0-9].*");
        }
    }
}
