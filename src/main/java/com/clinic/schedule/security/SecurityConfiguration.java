package com.clinic.schedule.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import com.clinic.schedule.security.presentation.TemporaryPasswordFilter;
import com.clinic.schedule.security.presentation.http.ApiSecurityErrorHandler;
import com.clinic.schedule.security.service.AppUserDetails;
import com.clinic.schedule.user.api.Role;

/**
 * Two filter chains, one per kind of client (D-02, D-34):
 *
 * <ul>
 *   <li>{@code /api/**}: basic authentication on every request, no session, no CSRF — the
 *       contract the future single-page applications will consume.</li>
 *   <li>everything else: a login form, a session, and CSRF protection for the screens.</li>
 * </ul>
 *
 * <p>What each role may reach is decided here, by route: a practitioner reads the agenda and
 * works with clinical records, which are theirs alone (D-51); an admin administers the users, and
 * reaches nothing else (D-68); reception does everything else. Every user changes their own
 * password, and one whose password is temporary does nothing else until they do (D-69).
 * <em>Whose</em> agenda or record a practitioner reads is not a route question, so it is
 * decided by the use case, which asks the security module who is asking (D-04, D-49).
 */
@Configuration
class SecurityConfiguration {

    private static final String RECEPTION = Role.RECEPTION.name();
    private static final String PRACTITIONER = Role.PRACTITIONER.name();
    private static final String ADMIN = Role.ADMIN.name();

    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http, ApiSecurityErrorHandler errors) throws Exception {
        return http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(HttpMethod.GET, "/api/appointments").hasAnyRole(RECEPTION, PRACTITIONER)
                        .requestMatchers("/api/consultations/**", "/api/checks/**", "/api/orders/**",
                                "/api/attachments/**", "/api/clinical-records/**", "/api/my-queue/**").hasRole(PRACTITIONER)
                        .requestMatchers("/api/users/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/password").authenticated()
                        .anyRequest().hasRole(RECEPTION))
                .addFilterAfter(TemporaryPasswordFilter.forApi(errors), AuthorizationFilter.class)
                .httpBasic(basic -> basic.authenticationEntryPoint(errors))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain screenChain(HttpSecurity http, SessionRegistry sessions) throws Exception {
        return http
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers("/login", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/agenda", "/queue-events").hasAnyRole(RECEPTION, PRACTITIONER)
                        .requestMatchers("/consultations/**", "/checks/**", "/orders/**", "/attachments/**",
                                "/clinical-records/**", "/my-queue/**").hasRole(PRACTITIONER)
                        .requestMatchers("/users/**").hasRole(ADMIN)
                        .requestMatchers("/password").authenticated()
                        .anyRequest().hasRole(RECEPTION))
                .addFilterAfter(TemporaryPasswordFilter.forScreens(), AuthorizationFilter.class)
                .formLogin(login -> login.loginPage("/login").successHandler(landing()).permitAll())
                // Every session is registered, with no limit to how many a user has, so the sessions
                // of a deactivated user can be ended at once (D-68).
                .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessions)
                        .expiredUrl("/login?expired"))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .build();
    }

    /**
     * Each role lands on its own screen: the admin on the users, everyone else on the agenda. A user
     * whose password is temporary lands on changing it.
     */
    private static AuthenticationSuccessHandler landing() {
        String admin = "ROLE_" + ADMIN; // the prefix hasRole adds
        return (request, response, authentication) -> {
            var authorities = authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
            String screen = authorities.contains(AppUserDetails.TEMPORARY_PASSWORD) ? "/password?required"
                    : authorities.contains(admin) ? "/users" : "/agenda";
            response.sendRedirect(request.getContextPath() + screen);
        };
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Tells the registry when a session ends, by logging out or running out, so it forgets it. */
    @Bean
    static HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
