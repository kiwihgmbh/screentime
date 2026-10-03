package com.kiwih.screentime.config;

import com.kiwih.screentime.security.JwtAuthenticationFilter;
import com.kiwih.screentime.web.ApiErrorWriter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless, token only. There is no session, no form login and no
 * registration: accounts are created by a parent inside the app.
 *
 * The role rules are stated twice on purpose, once here by path and once on the
 * service methods with {@code @PreAuthorize}. A URL rule is easy to read and a
 * method rule cannot be bypassed by a route somebody adds later.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           ApiErrorWriter apiErrorWriter,
                                           // Spring MVC contributes a CorsConfigurationSource of its
                                           // own, so this one is asked for by name
                                           @Qualifier("screentimeCorsConfigurationSource")
                                           CorsConfigurationSource corsSource) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // the container dispatches to /error after a refusal in this
                        // chain. Without this the second pass has no authentication
                        // left, falls through to denyAll below, and every 403 comes
                        // back as a 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // the child has to be able to read the rules the account
                        // runs on, or the rules page drifts away from the balance
                        .requestMatchers(HttpMethod.GET, "/api/settings").authenticated()
                        // everything a parent alone may do
                        .requestMatchers("/api/checks/**").hasRole("PARENT")
                        .requestMatchers("/api/adjustments/**").hasRole("PARENT")
                        .requestMatchers("/api/settings/**").hasRole("PARENT")
                        .requestMatchers("/api/users/**").hasRole("PARENT")
                        .requestMatchers("/api/weeks/**").hasRole("PARENT")
                        .requestMatchers(HttpMethod.PUT, "/api/sessions/**").hasRole("PARENT")
                        .requestMatchers(HttpMethod.DELETE, "/api/sessions/**").hasRole("PARENT")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(apiErrorWriter)
                        .accessDeniedHandler(apiErrorWriter))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS for the one case that needs it: the Angular dev server on another
     * port. In a real deployment nginx serves the app and proxies /api under
     * one host name, so nothing is cross origin and this never applies.
     */
    @Bean("screentimeCorsConfigurationSource")
    public CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins(properties));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource byPath = new UrlBasedCorsConfigurationSource();
        byPath.registerCorsConfiguration("/api/**", config);

        // a same origin request carries an Origin header too, and must not be
        // measured against the list above
        return new SameOriginAwareCorsSource(byPath);
    }

    /** The configured origins, plus the public URL when it is a different host. */
    private static List<String> allowedOrigins(AppProperties properties) {
        List<String> origins = new ArrayList<>(properties.getCors().getAllowedOrigins());
        String publicUrl = properties.getPublicUrl();
        if (publicUrl != null && !publicUrl.isBlank()) {
            try {
                URI uri = URI.create(publicUrl.trim());
                if (uri.getScheme() != null && uri.getHost() != null) {
                    String origin = uri.getPort() == -1
                            ? uri.getScheme() + "://" + uri.getHost()
                            : uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort();
                    if (!origins.contains(origin)) {
                        origins.add(origin);
                    }
                }
            } catch (IllegalArgumentException e) {
                // APP_PUBLIC_URL is only used for links; a bad value must not
                // stop the application from starting
            }
        }
        return origins;
    }
}
