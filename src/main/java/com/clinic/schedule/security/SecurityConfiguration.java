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
import com.clinic.schedule.security.presentation.screen.ScreenAccessDeniedHandler;
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
 * works with clinical records, which are theirs alone (D-51); an admin administers the users and
 * reads who read which record and who was refused one, and reaches nothing else (D-68, D-71, D-72);
 * reception does everything else. Every user changes their own password, and one whose password is
 * temporary does nothing else until they do (D-69). The waiting room's user sees the waiting-room
 * screen and its stream, and nothing else (D-70). A clinical route refused to a role is said, to be
 * recorded (D-72).
 * <em>Whose</em> agenda or record a practitioner reads is not a route question, so it is
 * decided by the use case, which asks the security module who is asking (D-04, D-49).
 */
@Configuration
class SecurityConfiguration {

    private static final String RECEPTION = Role.RECEPTION.name();
    private static final String PRACTITIONER = Role.PRACTITIONER.name();
    private static final String ADMIN = Role.ADMIN.name();
    private static final String WAITING_ROOM = Role.WAITING_ROOM.name();

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
                        .requestMatchers("/api/users/**", "/api/record-readings", "/api/record-refusals").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/password").authenticated()
                        .anyRequest().hasRole(RECEPTION))
                .addFilterAfter(TemporaryPasswordFilter.forApi(errors), AuthorizationFilter.class)
                .httpBasic(basic -> basic.authenticationEntryPoint(errors))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain screenChain(HttpSecurity http, SessionRegistry sessions, ScreenAccessDeniedHandler denied)
            throws Exception {
        return http
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers("/login", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/queue-events").hasAnyRole(RECEPTION, PRACTITIONER, WAITING_ROOM)
                        .requestMatchers(HttpMethod.GET, "/waiting-room").hasAnyRole(RECEPTION, WAITING_ROOM)
                        .requestMatchers(HttpMethod.GET, "/", "/agenda").hasAnyRole(RECEPTION, PRACTITIONER)
                        .requestMatchers("/consultations/**", "/checks/**", "/orders/**", "/attachments/**",
                                "/clinical-records/**", "/my-queue/**").hasRole(PRACTITIONER)
                        .requestMatchers("/users/**", "/record-readings", "/record-refusals").hasRole(ADMIN)
                        .requestMatchers("/password").authenticated()
                        .anyRequest().hasRole(RECEPTION))
                .addFilterAfter(TemporaryPasswordFilter.forScreens(), AuthorizationFilter.class)
                .formLogin(login -> login.loginPage("/login").successHandler(landing()).permitAll())
                // Every session is registered, with no limit to how many a user has, so the sessions
                // of a deactivated user can be ended at once (D-68).
                .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessions)
                        .expiredUrl("/login?expired"))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .exceptionHandling(handling -> handling.accessDeniedHandler(denied))
                .build();
    }

    /**
     * Each role lands on its own screen: the admin on the users, the waiting room on its screen,
     * everyone else on the agenda. A user whose password is temporary lands on changing it.
     *
     * <p>The waiting room's session does not run out while nobody touches it: nobody does, it is a
     * screen on the wall, and an open stream is not activity for the session (D-70).
     */
    private static AuthenticationSuccessHandler landing() {
        String admin = "ROLE_" + ADMIN; // the prefix hasRole adds
        String waitingRoom = "ROLE_" + WAITING_ROOM;
        return (request, response, authentication) -> {
            var authorities = authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
            if (authorities.contains(waitingRoom)) {
                request.getSession().setMaxInactiveInterval(-1); // never, for the servlet container
            }
            String screen = authorities.contains(AppUserDetails.TEMPORARY_PASSWORD) ? "/password?required"
                    : authorities.contains(admin) ? "/users"
                    : authorities.contains(waitingRoom) ? "/waiting-room" : "/agenda";
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
