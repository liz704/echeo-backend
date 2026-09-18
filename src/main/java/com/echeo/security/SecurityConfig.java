package com.echeo.security;

import com.echeo.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuration centrale de Spring Security pour ÉCHÉO :
 * - API stateless (pas de session HTTP, tout repose sur le JWT).
 * - /api/v1/auth/** et /api/v1/public/** accessibles sans authentification.
 * - Toute autre route nécessite un JWT valide (filtre JwtAuthenticationFilter).
 * - CORS ouvert au frontend Next.js.
 *
 * IMPORTANT : sans point d'entrée personnalisé, Spring Security répond aux
 * requêtes non authentifiées par un 403 VIDE (Http403ForbiddenEntryPoint par
 * défaut) — le frontend, lui, ne sait réagir qu'à un 401 pour déconnecter et
 * renvoyer vers /login. authenticationEntryPoint() ci-dessous corrige ça en
 * renvoyant un vrai 401 avec un corps JSON cohérent avec le reste de l'API
 * (ErrorResponse). accessDeniedHandler() fait de même pour un 403
 * "authentifié mais non autorisé" (ex. accès à la ressource d'un autre
 * utilisateur), qui lui doit bien rester un 403.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    @Value("${echeo.frontend.base-url:https://echeo-one.vercel.app}")
    private String frontendBaseUrl;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CustomUserDetailsService userDetailsService,
                          ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Requête sans JWT, JWT malformé, expiré ou signature invalide : 401,
     * jamais 403 (voir note de classe ci-dessus).
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> writeJsonError(
                response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                "Authentification requise ou expirée. Merci de vous reconnecter.");
    }

    /**
     * Requête authentifiée mais interdite (ex. ressource d'un autre
     * utilisateur) : reste un vrai 403, avec corps JSON cette fois.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeJsonError(
                response, HttpStatus.FORBIDDEN, "Forbidden",
                "Vous n'avez pas accès à cette ressource.");
    }

    private void writeJsonError(HttpServletResponse response, HttpStatus status, String error, String message)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = new ErrorResponse(status.value(), error, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Autorise le frontend Next.js en dev (localhost:3000) et en prod (Vercel).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",
                frontendBaseUrl,
                "https://echeo-one.vercel.app"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}