package in.smarttech.dukaandost.security;

import in.smarttech.dukaandost.domain.Owner;
import in.smarttech.dukaandost.repository.OwnerRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerSessionService {

    private final OwnerRepository owners;

    public OwnerSessionService(OwnerRepository owners) {
        this.owners = owners;
    }

    @Transactional
    public SessionProfile claimWelcome(UUID ownerId) {
        Owner owner = owners.findById(ownerId).orElseThrow();
        boolean showWelcome = owner.isWelcomePending();
        if (showWelcome) {
            owner.setWelcomePending(false);
            owners.save(owner);
        }
        return new SessionProfile(owner.getName(), owner.getEmail(), showWelcome);
    }

    public record SessionProfile(String name, String email, boolean showWelcome) {
    }
}
