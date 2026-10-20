package in.smarttech.dukaandost.security;

import in.smarttech.dukaandost.repository.OwnerRepository;
import java.util.Locale;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerUserDetailsService implements UserDetailsService {

    private final OwnerRepository owners;

    public OwnerUserDetailsService(OwnerRepository owners) {
        this.owners = owners;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        String email = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        return owners.findByEmail(email)
                .map(owner -> {
                    if (!owner.isEmailVerified() || !owner.isPasswordLoginEnabled()) {
                        throw new DisabledException("Account email has not been verified.");
                    }
                    return new OwnerPrincipal(owner.getId(), owner.getEmail(), owner.getPasswordHash());
                })
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
    }
}
