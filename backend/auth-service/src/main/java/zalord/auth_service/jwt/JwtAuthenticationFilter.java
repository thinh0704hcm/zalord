package zalord.auth_service.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import zalord.auth_service.model.CustomUserDetails;
import zalord.auth_service.service.impl.UserDetailsServiceImpl;

import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        String id = null;
        String jwt = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
            try {
                id = jwtUtil.extractUserId(jwt);
            }
            catch (Exception e) {
                log.warn("Invalid JWT on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            }
        }

        if (id != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            CustomUserDetails userDetails = userDetailsService.loadUserById(UUID.fromString(id));

            if (jwtUtil.validateToken(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("Authenticated userId={} for {} {}", id, request.getMethod(), request.getRequestURI());
            } else {
                log.warn("Token validation failed for userId={} on {} {}", id, request.getMethod(), request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }
}
