package com.lemondrop.security;

import com.lemondrop.model.ApiToken;
import com.lemondrop.repository.ApiTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class ApiTokenFilter extends OncePerRequestFilter {

    private final ApiTokenRepository apiTokenRepository;
    private final UserDetailsService userDetailsService;

    public ApiTokenFilter(ApiTokenRepository apiTokenRepository, UserDetailsService userDetailsService) {
        this.apiTokenRepository = apiTokenRepository;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        // Only run token validation on /api/mobile/** routes, skipping authentication/public endpoints
        if (path.startsWith("/api/mobile/") && !path.equals("/api/mobile/auth/login")) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String tokenStr = authHeader.substring(7).trim();
                Optional<ApiToken> apiTokenOpt = apiTokenRepository.findByToken(tokenStr);

                if (apiTokenOpt.isPresent()) {
                    ApiToken apiToken = apiTokenOpt.get();
                    if (apiToken.getExpiresAt().isAfter(LocalDateTime.now())) {
                        try {
                            UserDetails userDetails = userDetailsService.loadUserByUsername(apiToken.getUsername());
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        } catch (Exception e) {
                            // User not found or inactive, keep unauthenticated
                            SecurityContextHolder.clearContext();
                        }
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
