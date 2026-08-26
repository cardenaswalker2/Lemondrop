package com.lemondrop.config;

import com.lemondrop.security.CustomUserDetailsService;
import com.lemondrop.security.ApiTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final ApiTokenFilter apiTokenFilter;

    public SecurityConfig(CustomUserDetailsService userDetailsService, ApiTokenFilter apiTokenFilter) {
        this.userDetailsService = userDetailsService;
        this.apiTokenFilter = apiTokenFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    "/api/mobile/**", 
                    "/api/public/**", 
                    "/api/ai/**",
                    "/admin/api/**",
                    "/asesor/api/**"
                )
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", 
                    "/catalogo",
                    "/catalogo/**", 
                    "/pedido/crear/**", 
                    "/pedido/exitoso/**",
                    "/pedido/seguimiento",
                    "/pedido/seguimiento/**", 
                    "/api/public/**",
                    "/api/ai/**",
                    "/api/mobile/auth/login",
                    "/api/health",
                    "/login", 
                    "/css/**", 
                    "/js/**", 
                    "/images/**", 
                    "/favicon.ico"
                ).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/asesor/**").hasAnyRole("ASESOR", "ADMIN")
                .requestMatchers("/api/mobile/**").hasAnyRole("ASESOR", "ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(apiTokenFilter, UsernamePasswordAuthenticationFilter.class)
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(customAuthenticationSuccessHandler())
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler customAuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            var roles = authentication.getAuthorities();
            boolean isAdmin = roles.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isAdvisor = roles.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ASESOR"));

            if (isAdmin) {
                response.sendRedirect("/admin/dashboard");
            } else if (isAdvisor) {
                response.sendRedirect("/asesor/dashboard");
            } else {
                response.sendRedirect("/");
            }
        };
    }
}
