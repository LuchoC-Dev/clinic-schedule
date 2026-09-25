package com.clinic.schedule.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
 * works with clinical records, which are theirs alone (D-51); reception does everything else.
 * <em>Whose</em> agenda or record a practitioner reads is not a route question, so it is
 * decided by the use case, which asks the security module who is asking (D-04, D-49).
 */
@Configuration
class SecurityConfiguration {

    private static final String RECEPTION = Role.RECEPTION.name();
    private static final String PRACTITIONER = Role.PRACTITIONER.name();

    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http, ApiSecurityErrorHandler errors) throws Exception {
        return http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(HttpMethod.GET, "/api/appointments").hasAnyRole(RECEPTION, PRACTITIONER)
                        .requestMatchers("/api/consultations/**", "/api/checks/**", "/api/clinical-records/**").hasRole(PRACTITIONER)
                        .anyRequest().hasRole(RECEPTION))
                .httpBasic(basic -> basic.authenticationEntryPoint(errors))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain screenChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers("/login", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/agenda").hasAnyRole(RECEPTION, PRACTITIONER)
                        .requestMatchers("/consultations/**", "/checks/**", "/clinical-records/**").hasRole(PRACTITIONER)
                        .anyRequest().hasRole(RECEPTION))
                .formLogin(login -> login.loginPage("/login").defaultSuccessUrl("/agenda", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
