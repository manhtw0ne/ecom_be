package com.manh.ecom_be.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manh.ecom_be.components.JwtTokenUtils;
import com.manh.ecom_be.models.User;
import com.manh.ecom_be.responses.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {
    private final UserDetailsService userDetailsService;
    private final JwtTokenUtils jwtTokenUtil;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (SecurityContextHolder.getContext().getAuthentication() == null
                && header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                String subject = jwtTokenUtil.getSubject(token);
                User user = (User) userDetailsService.loadUserByUsername(subject);
                if (!jwtTokenUtil.validateToken(token, user)) {
                    unauthorized(response);
                    return;
                }
                var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
                unauthorized(response);
                return;
            }
        }
        // Authorization rules live in SecurityFilterChain. Never turn downstream errors into 401.
        filterChain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(HttpStatus.UNAUTHORIZED, "Invalid or expired token"));
    }
}
