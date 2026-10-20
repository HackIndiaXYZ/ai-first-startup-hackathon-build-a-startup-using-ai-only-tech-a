package in.smarttech.dukaandost.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.smarttech.dukaandost.domain.Owner;
import in.smarttech.dukaandost.repository.OwnerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class OwnerProfileServiceTest {

    private final OwnerRepository owners = mock(OwnerRepository.class);
    private final CurrentOwner currentOwner = new CurrentOwner();
    private final OwnerProfileService service = new OwnerProfileService(
            owners, currentOwner, new BCryptPasswordEncoder(), mock(AvatarStorageService.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void profileReadsAndUpdatesOnlyTheSignedInOwnerAndResetsPhoneVerification() {
        UUID ownerId = UUID.randomUUID();
        Owner owner = new Owner("Asha", "asha@example.com", "hash", "+919876543210");
        owner.setPhoneVerified(true);
        ReflectionTestUtils.setField(owner, "id", ownerId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new OwnerPrincipal(ownerId, owner.getEmail(), owner.getPasswordHash()), "session",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
        when(owners.findById(ownerId)).thenReturn(Optional.of(owner));
        when(owners.save(owner)).thenReturn(owner);

        var response = service.updateProfile(new OwnerProfileService.ProfileUpdate(
                " Asha ", "+91 98765 43211", "Asha Stores", null, "Pune", "Maharashtra", "411001"));

        assertThat(response.email()).isEqualTo("asha@example.com");
        assertThat(response.name()).isEqualTo("Asha");
        assertThat(response.phone()).isEqualTo("+919876543211");
        assertThat(response.phoneVerified()).isFalse();
        verify(owners).findById(ownerId);
        verify(owners).save(owner);
    }
}
