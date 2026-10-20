package in.smarttech.dukaandost.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects login attempts from a client address that has too many recent failures. */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private final SlidingWindowLimiter failedLogins;

    public LoginThrottleFilter(SlidingWindowLimiter failedLogins) {
        this.failedLogins = failedLogins;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod()) && "/login".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long retryAfter = failedLogins.retryAfterSeconds(request.getRemoteAddr());
        if (retryAfter > 0) {
            JsonErrors.write(response, 429, "Too many failed logins. Please wait and try again.", retryAfter);
            return;
        }
        chain.doFilter(request, response);
    }
}
