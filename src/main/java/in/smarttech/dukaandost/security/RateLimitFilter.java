package in.smarttech.dukaandost.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits draft creation, extraction retries and audio transcription, the endpoints that call AI providers.
 * The client address is {@code getRemoteAddr()}: with {@code server.forward-headers-strategy=native}
 * Tomcat already resolves Render's X-Forwarded-For from trusted proxies only, so reading the
 * header here as well would let clients spoof their way around the limit.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern CREATE_OR_RETRY = Pattern.compile("^/api/drafts(/[^/]+/extract)?$");
    private static final Pattern UPDATE = Pattern.compile("^/api/drafts/[^/]+$");
    private static final Pattern ACCOUNT_ACTIONS =
            Pattern.compile("^/api/auth/(signup|forgot-password|reset-password)$");
    private static final Pattern TRANSCRIPTION = Pattern.compile("^/api/transcription$");

    private final SlidingWindowLimiter limiter;
    private final boolean accountEndpoints;

    public RateLimitFilter(SlidingWindowLimiter limiter) {
        this(limiter, false);
    }

    private RateLimitFilter(SlidingWindowLimiter limiter, boolean accountEndpoints) {
        this.limiter = limiter;
        this.accountEndpoints = accountEndpoints;
    }

    /** Limits sign-up, forgot-password and reset-password per client IP. */
    public static RateLimitFilter forAccountEndpoints(SlidingWindowLimiter limiter) {
        return new RateLimitFilter(limiter, true);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (accountEndpoints) {
            return !(HttpMethod.POST.matches(request.getMethod()) && ACCOUNT_ACTIONS.matcher(path).matches());
        }
        if (HttpMethod.POST.matches(request.getMethod())) {
            return !CREATE_OR_RETRY.matcher(path).matches() && !TRANSCRIPTION.matcher(path).matches();
        }
        if (HttpMethod.PUT.matches(request.getMethod())) {
            return !UPDATE.matcher(path).matches();
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String clientIp = request.getRemoteAddr();
        if (limiter.tryAcquire(clientIp)) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfter = Math.max(1, limiter.retryAfterSeconds(clientIp));
        JsonErrors.write(response, 429, accountEndpoints
                ? "Too many attempts. Please wait a few minutes and try again."
                : "Too many extraction requests. Please wait a moment and try again.", retryAfter);
    }
}
