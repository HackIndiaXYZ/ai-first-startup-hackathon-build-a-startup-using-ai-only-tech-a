package in.smarttech.dukaandost.security;

import org.springframework.beans.factory.ObjectProvider;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Qualifier("accountRateLimiter")
    SlidingWindowLimiter accountRateLimiter(
            Clock clock,
            @Value("${app.rate-limit.account.max-requests:10}") int maxRequests,
            @Value("${app.rate-limit.account.window:15m}") Duration window) {
        return new SlidingWindowLimiter(maxRequests, window, clock);
    }

    @Bean
    @Qualifier("draftRateLimiter")
    SlidingWindowLimiter draftRateLimiter(
            Clock clock,
            @Value("${app.rate-limit.drafts.max-requests:10}") int maxRequests,
            @Value("${app.rate-limit.drafts.window:60s}") Duration window) {
        return new SlidingWindowLimiter(maxRequests, window, clock);
    }

    @Bean
    @Qualifier("failedLoginLimiter")
    SlidingWindowLimiter failedLoginLimiter(
            Clock clock,
            @Value("${app.rate-limit.login.max-failures:5}") int maxFailures,
            @Value("${app.rate-limit.login.window:15m}") Duration window) {
        return new SlidingWindowLimiter(maxFailures, window, clock);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> oauthRegistrations,
            ObjectProvider<GoogleOAuthConfiguration.GoogleOidcUserService> googleUserService,
            @Qualifier("draftRateLimiter") SlidingWindowLimiter draftRateLimiter,
            @Qualifier("failedLoginLimiter") SlidingWindowLimiter failedLoginLimiter,
            @Qualifier("accountRateLimiter") SlidingWindowLimiter accountRateLimiter) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/login.html", "/signup.html", "/forgot-password.html",
                                "/reset-password.html", "/verify-email.html", "/verify-email.js",
                                "/login.js", "/auth.js", "/password-toggle.js", "/styles.css", "/logo.svg",
                                "/api/auth/session", "/api/auth/google-enabled")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/login", "/api/auth/verify",
                                "/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/login", "/api/auth/signup",
                                "/api/auth/forgot-password", "/api/auth/reset-password")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .successHandler((request, response, authentication) -> {
                            failedLoginLimiter.reset(request.getRemoteAddr());
                            response.setStatus(HttpStatus.OK.value());
                        })
                        .failureHandler((request, response, exception) -> {
                            failedLoginLimiter.record(request.getRemoteAddr());
                            JsonErrors.write(response, 401, "Incorrect email or password.", null);
                        }))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("JSESSIONID"))
                .csrf(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler((request, response, exception) -> JsonErrors.write(response, 403,
                                "Your session security check failed. Refresh the page and try again.", null)))
                .addFilterBefore(new LoginThrottleFilter(failedLoginLimiter),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new RateLimitFilter(draftRateLimiter), AuthorizationFilter.class)
                .addFilterAfter(RateLimitFilter.forAccountEndpoints(accountRateLimiter), AuthorizationFilter.class);
        ClientRegistrationRepository registrations = oauthRegistrations.getIfAvailable();
        GoogleOAuthConfiguration.GoogleOidcUserService oidcUserService = googleUserService.getIfAvailable();
        if (registrations != null && oidcUserService != null) {
            http.oauth2Login(oauth -> oauth
                    .clientRegistrationRepository(registrations)
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                    .defaultSuccessUrl("/", true));
        }
        return http.build();
    }

    private static AuthenticationEntryPoint authenticationEntryPoint() {
        RequestMatcher api = request -> request.getRequestURI().startsWith("/api/");
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
        entryPoints.put(api, (request, response, exception) ->
                JsonErrors.write(response, 401, "Please sign in to continue.", null));
        DelegatingAuthenticationEntryPoint delegating = new DelegatingAuthenticationEntryPoint(entryPoints);
        delegating.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login.html"));
        return delegating;
    }
}
