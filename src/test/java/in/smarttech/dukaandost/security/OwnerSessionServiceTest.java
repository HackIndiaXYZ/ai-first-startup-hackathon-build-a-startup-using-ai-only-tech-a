package in.smarttech.dukaandost.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.smarttech.dukaandost.domain.Owner;
import in.smarttech.dukaandost.repository.OwnerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnerSessionServiceTest {

    @Test
    void welcomeIsClaimedOnlyOnce() {
        OwnerRepository owners = mock(OwnerRepository.class);
        Owner owner = new Owner("Ravi", "ravi@example.com", "hash", null);
        OwnerSessionService service = new OwnerSessionService(owners);
        UUID ownerId = UUID.randomUUID();
        when(owners.findById(ownerId)).thenReturn(Optional.of(owner));
        when(owners.save(owner)).thenReturn(owner);

        var first = service.claimWelcome(ownerId);
        var second = service.claimWelcome(ownerId);

        assertThat(first.showWelcome()).isTrue();
        assertThat(first.name()).isEqualTo("Ravi");
        assertThat(second.showWelcome()).isFalse();
        verify(owners).save(owner);
    }
}
