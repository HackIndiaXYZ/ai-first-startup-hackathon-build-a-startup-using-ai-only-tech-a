package in.smarttech.dukaandost.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Browsers that land on /login (typed by hand or from an old link) get the real sign-in page, not a 404. */
@Configuration
public class LoginPageConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/login", "/login.html");
    }
}
