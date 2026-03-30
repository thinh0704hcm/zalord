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

        // TODO Step 1 & 2: Check if authHeader is null or doesn't start with "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // If so, call filterChain.doFilter() and return.
            filterChain.doFilter(request, response);
            return;
        }

        // TODO Step 3: Extract the exact token string
        String token = authHeader.substring(7);

        // TODO Step 4b: Check if ID is not null AND
        // SecurityContextHolder.getContext().getAuthentication() is null
        if (SecurityContextHolder.getContext().getAuthentication() == null) {

            // TODO Step 5: Load the user from the database using the ID

            // TODO Step 6: Validate token with jwtService.isValidToken()
            if (jwtService.isValidToken(token)) {

                UUID userId = jwtService.extractUserId(token);
                AuthenticatedUser principal = new AuthenticatedUser(userId);

                // TODO Step 6b: If valid, create UsernamePasswordAuthenticationToken
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        Collections.emptyList());

                // TODO Step 6c: Set details on the auth token using
                // WebAuthenticationDetailsSource
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // TODO Step 6d: Set the auth token into
                // SecurityContextHolder.getContext().setAuthentication(...)
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        // TODO Step 7: Call filterChain.doFilter(request, response) to continue the
        // journey
        filterChain.doFilter(request, response);

    }
}