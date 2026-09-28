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
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import com.clinic.schedule.security.presentation.http.ApiSecurityErrorHandler;
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
 * reaches nothing else (D-68); reception does everything else.
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
                        .anyRequest().hasRole(RECEPTION))
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
                        .anyRequest().hasRole(RECEPTION))
                .formLogin(login -> login.loginPage("/login").successHandler(landing()).permitAll())
                // Every session is registered, with no limit to how many a user has, so the sessions
                // of a deactivated user can be ended at once (D-68).
                .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessions)
                        .expiredUrl("/login?expired"))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .build();
    }

    /** Each role lands on its own screen: the admin on the users, everyone else on the agenda. */
    private static AuthenticationSuccessHandler landing() {
        String admin = "ROLE_" + ADMIN; // the prefix hasRole adds
        return (request, response, authentication) -> response.sendRedirect(request.getContextPath()
                + (authentication.getAuthorities().stream().anyMatch(a -> admin.equals(a.getAuthority()))
                        ? "/users" : "/agenda"));
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
