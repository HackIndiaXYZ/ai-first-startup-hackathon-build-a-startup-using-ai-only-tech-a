package in.smarttech.dukaandost.security;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentOwner {

    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OwnerPrincipal principal) {
            return principal.id();
        }
        if (authentication != null && authentication.getPrincipal() instanceof OwnerOidcPrincipal principal) {
            return principal.ownerId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
    }
}
