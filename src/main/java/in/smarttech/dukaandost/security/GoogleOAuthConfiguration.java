package in.smarttech.dukaandost.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

@Configuration
public class GoogleOAuthConfiguration {

    @Bean
    @Conditional(GoogleCredentialsConfigured.class)
    ClientRegistrationRepository googleClientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:}") String clientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret) {
        ClientRegistration registration = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    GoogleOidcUserService googleOidcUserService(AccountService accounts) {
        return new GoogleOidcUserService(accounts);
    }

    static final class GoogleCredentialsConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String clientId = context.getEnvironment().getProperty("GOOGLE_CLIENT_ID", "");
            String clientSecret = context.getEnvironment().getProperty("GOOGLE_CLIENT_SECRET", "");
            return !clientId.isBlank() && !clientSecret.isBlank();
        }
    }

    static final class GoogleOidcUserService
            implements OAuth2UserService<OidcUserRequest, OidcUser> {
        private final AccountService accounts;
        private final OidcUserService delegate = new OidcUserService();

        private GoogleOidcUserService(AccountService accounts) {
            this.accounts = accounts;
        }

        @Override
        public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
            OidcUser googleUser = delegate.loadUser(userRequest);
            String email = googleUser.getEmail();
            String subject = googleUser.getSubject();
            if (email == null || subject == null || !Boolean.TRUE.equals(googleUser.getClaim("email_verified"))) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_user_info"), "Google must provide a verified email address.");
            }
            try {
                var owner = accounts.signInWithGoogle(subject, email, googleUser.getFullName(),
                        java.util.UUID.randomUUID() + "-Google-only");
                return new OwnerOidcPrincipal(googleUser, owner.getId());
            } catch (IllegalArgumentException exception) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("account_link_conflict"), "This Google account cannot be linked.");
            }
        }
    }
}
