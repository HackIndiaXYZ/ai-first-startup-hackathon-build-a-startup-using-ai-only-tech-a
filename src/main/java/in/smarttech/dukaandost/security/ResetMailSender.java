package in.smarttech.dukaandost.security;

import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends reset links through the configured SMTP server. Configured only when spring.mail.host
 * (SPRING_MAIL_HOST), app.mail.from and app.base-url are all set; the link is built from the
 * configured base URL, never from the request's Host header.
 */
@Component
public class ResetMailSender {

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String from;
    private final String baseUrl;

    public ResetMailSender(ObjectProvider<JavaMailSender> senderProvider,
                           @Value("${app.mail.from:}") String from,
                           @Value("${app.base-url:}") String baseUrl) {
        this.senderProvider = senderProvider;
        this.from = from;
        this.baseUrl = baseUrl;
    }

    public boolean isConfigured() {
        return senderProvider.getIfAvailable() != null && !from.isBlank() && !baseUrl.isBlank();
    }

    public void sendResetLink(String to, String name, String token, Duration ttl) {
        JavaMailSender sender = senderProvider.getObject();
        String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Reset your Dukaan Dost password");
        message.setText("Hello " + name + ",\n\nUse this link to choose a new password. It works once and expires in "
                + ttl.toMinutes() + " minutes:\n\n" + root + "/reset-password.html?token=" + token
                + "\n\nIf you did not ask for this, you can ignore this email.\n");
        sender.send(message);
    }

    public void sendVerificationLink(String to, String name, String token, Duration ttl) {
        JavaMailSender sender = senderProvider.getObject();
        String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Verify your Dukaan Dost email");
        message.setText("Hello " + name + ",\n\nVerify your email using this link. It works once and expires in "
                + ttl.toMinutes() + " minutes:\n\n" + root + "/verify-email.html?token=" + token
                + "\n\nIf you did not create this account, you can ignore this email.\n");
        sender.send(message);
    }
}
