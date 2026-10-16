package com.padmakar.trustflow.config;

import com.padmakar.trustflow.repository.UserAccountRepository;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(UserAccountRepository repository) {
        return username ->
            repository
                .findByUsername(username)
                .map(user ->
                    User.withUsername(user.getUsername())
                        .password(user.getPassword())
                        .roles(user.getRole().name())
                        .build()
                )
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers("/login", "/app/**", "/api/csrf", "/style.css", "/error")
                    .permitAll()
                    .requestMatchers("/admin/**", "/api/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/cases/*/review")
                    .hasRole("REVIEWER")
                    .requestMatchers("/cases/new")
                    .hasRole("SUBMITTER")
                    .anyRequest()
                    .authenticated()
            )
            .exceptionHandling(errors ->
                errors.authenticationEntryPoint((request, response, exception) -> {
                    if (request.getRequestURI().startsWith("/api/")) response.sendError(401);
                    else response.sendRedirect("/login");
                })
            )
            .formLogin(form ->
                form
                    .loginPage("/login")
                    .successHandler((request, response, authentication) -> {
                        if (
                            "application/json".equals(request.getHeader("Accept"))
                        ) response.setStatus(204);
                        else response.sendRedirect("/app/index.html");
                    })
                    .failureHandler((request, response, exception) -> {
                        if (
                            "application/json".equals(request.getHeader("Accept"))
                        ) response.sendError(401);
                        else response.sendRedirect("/login?error");
                    })
                    .permitAll()
            )
            .logout(logout ->
                logout.logoutSuccessHandler((request, response, authentication) -> {
                    if ("application/json".equals(request.getHeader("Accept"))) response.setStatus(
                        204
                    );
                    else response.sendRedirect("/app/index.html");
                })
            )
            .build();
    }
}
