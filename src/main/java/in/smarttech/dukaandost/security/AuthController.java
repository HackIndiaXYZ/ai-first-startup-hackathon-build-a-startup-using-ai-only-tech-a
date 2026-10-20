package in.smarttech.dukaandost.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final boolean googleConfigured;
    private final OwnerSessionService ownerSessions;

    public AuthController(@Value("${GOOGLE_CLIENT_ID:}") String googleClientId,
                          @Value("${GOOGLE_CLIENT_SECRET:}") String googleClientSecret,
                          OwnerSessionService ownerSessions) {
        this.googleConfigured = !googleClientId.isBlank() && !googleClientSecret.isBlank();
        this.ownerSessions = ownerSessions;
    }

    /** Lets the UI learn whether it is signed in and fetch the CSRF token for state-changing calls. */
    @GetMapping("/session")
    public SessionResponse session(Authentication authentication, CsrfToken csrfToken) {
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        if (!authenticated) {
            return new SessionResponse(false, csrfToken.getHeaderName(), csrfToken.getToken(), null, null, false);
        }
        UUID ownerId = authentication.getPrincipal() instanceof OwnerPrincipal principal
                ? principal.id()
                : ((OwnerOidcPrincipal) authentication.getPrincipal()).ownerId();
        OwnerSessionService.SessionProfile profile = ownerSessions.claimWelcome(ownerId);
        return new SessionResponse(true, csrfToken.getHeaderName(), csrfToken.getToken(),
                profile.name(), profile.email(), profile.showWelcome());
    }

    @GetMapping("/google-enabled")
    public java.util.Map<String, Boolean> googleEnabled() {
        return java.util.Map.of("enabled", googleConfigured);
    }

    public record SessionResponse(boolean authenticated, String csrfHeader, String csrfToken,
                                  String ownerName, String ownerEmail, boolean showWelcome) {
    }
}
