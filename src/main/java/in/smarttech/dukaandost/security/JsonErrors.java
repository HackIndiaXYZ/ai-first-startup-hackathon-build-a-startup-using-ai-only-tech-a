package in.smarttech.dukaandost.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class JsonErrors {

    private JsonErrors() {
    }

    static void write(HttpServletResponse response, int status, String message, Long retryAfterSeconds)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        if (retryAfterSeconds != null) {
            response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        }
        response.getWriter().write("{\"message\":\"" + message.replace("\"", "'") + "\"}");
    }
}
