package in.smarttech.dukaandost.security;

import in.smarttech.dukaandost.domain.Owner;
import in.smarttech.dukaandost.repository.OwnerRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OwnerProfileService {

    private final OwnerRepository owners;
    private final CurrentOwner currentOwner;
    private final PasswordEncoder passwords;
    private final AvatarStorageService avatars;

    public OwnerProfileService(OwnerRepository owners, CurrentOwner currentOwner,
                               PasswordEncoder passwords, AvatarStorageService avatars) {
        this.owners = owners;
        this.currentOwner = currentOwner;
        this.passwords = passwords;
        this.avatars = avatars;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile() {
        return response(requireOwner());
    }

    @Transactional
    public ProfileResponse updateProfile(ProfileUpdate request) {
        Owner owner = requireOwner();
        String phone = normalizePhone(request.phone());
        if (!Objects.equals(owner.getPhone(), phone)) {
            owner.setPhone(phone);
            owner.setPhoneVerified(false);
        }
        owner.setName(request.name().strip());
        owner.setShopName(clean(request.shopName()));
        owner.setStreet(clean(request.street()));
        owner.setCity(clean(request.city()));
        owner.setState(clean(request.state()));
        owner.setPostalCode(clean(request.postalCode()));
        return response(owners.save(owner));
    }

    @Transactional
    public ProfileResponse updateAvatar(MultipartFile upload) {
        Owner owner = requireOwner();
        String oldKey = owner.getAvatarKey();
        String newKey = avatars.store(upload);
        owner.setAvatarKey(newKey);
        owners.save(owner);
        avatars.delete(oldKey);
        return response(owner);
    }

    @Transactional
    public void deleteAvatar() {
        Owner owner = requireOwner();
        String oldKey = owner.getAvatarKey();
        owner.setAvatarKey(null);
        owners.save(owner);
        avatars.delete(oldKey);
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        Owner owner = requireOwner();
        if (!owner.isPasswordLoginEnabled()
                || !passwords.matches(currentPassword, owner.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }
        if (!isStrongPassword(newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use at least 8 characters with uppercase, lowercase, number and symbol characters.");
        }
        owner.setPasswordHash(passwords.encode(newPassword));
        owners.save(owner);
    }

    @Transactional
    public void deleteAccount(String currentPassword) {
        Owner owner = requireOwner();
        boolean credentialValid = owner.isPasswordLoginEnabled()
                ? passwords.matches(currentPassword, owner.getPasswordHash())
                : owner.getEmail().equalsIgnoreCase(currentPassword.strip());
        if (!credentialValid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is incorrect.");
        }
        avatars.delete(owner.getAvatarKey());
        owners.delete(owner);
    }

    @Transactional(readOnly = true)
    public AvatarResponse avatar() {
        Owner owner = requireOwner();
        if (owner.getAvatarKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile picture not found.");
        }
        return new AvatarResponse(avatars.read(owner.getAvatarKey()));
    }

    private Owner requireOwner() {
        UUID ownerId = currentOwner.id();
        return owners.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
    }

    private static ProfileResponse response(Owner owner) {
        return new ProfileResponse(owner.getName(), owner.getEmail(), owner.getPhone(), owner.isPhoneVerified(),
                owner.getShopName(), owner.getStreet(), owner.getCity(), owner.getState(), owner.getPostalCode(),
                owner.getAvatarKey() == null ? null : "/api/profile/avatar", owner.isPasswordLoginEnabled());
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String normalized = phone.strip().replaceAll("[ ()-]", "");
        if (!normalized.matches("^\\+[1-9][0-9]{7,14}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Phone must include a country code, for example +919876543210.");
        }
        return normalized;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static boolean isStrongPassword(String value) {
        return value != null && value.length() >= 8 && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72
                && value.matches("(?s).*[A-Z].*") && value.matches("(?s).*[a-z].*")
                && value.matches("(?s).*\\d.*") && value.matches("(?s).*[^A-Za-z0-9].*");
    }

    public record ProfileUpdate(String name, String phone, String shopName, String street,
                                String city, String state, String postalCode) {
    }

    public record ProfileResponse(String name, String email, String phone, boolean phoneVerified,
                                  String shopName, String street, String city, String state,
                                  String postalCode, String avatarUrl, boolean passwordLoginEnabled) {
    }

    public record AvatarResponse(byte[] bytes) {
    }
}
