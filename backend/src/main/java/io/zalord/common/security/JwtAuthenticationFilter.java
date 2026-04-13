package io.zalord.common.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.lang.Collections;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService /* , UserDetailsService */) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // If so, call filterChain.doFilter() and return.
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        // SecurityContextHolder.getContext().getAuthentication() is null
        if (SecurityContextHolder.getContext().getAuthentication() == null) {

            if (jwtService.isValidToken(token)) {

                UUID userId = jwtService.extractUserId(token);
                AuthenticatedUser principal = new AuthenticatedUser(userId);

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        Collections.emptyList());

                // WebAuthenticationDetailsSource
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // SecurityContextHolder.getContext().setAuthentication(...)
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        // journey
        filterChain.doFilter(request, response);

    }
}