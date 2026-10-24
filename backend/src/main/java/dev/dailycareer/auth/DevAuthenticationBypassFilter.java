package dev.dailycareer.auth;

import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Local-only screen testing without an external Google OAuth round trip. */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "DEV_AUTH_BYPASS", havingValue = "true")
public final class DevAuthenticationBypassFilter extends OncePerRequestFilter {
    private final long userId;

    public DevAuthenticationBypassFilter(@Value("${DEV_AUTH_USER_ID}") long userId) {
        if (userId <= 0) throw new IllegalArgumentException("DEV_AUTH_USER_ID must be a positive existing user ID");
        this.userId = userId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("GET".equals(request.getMethod()) && "/oauth2/authorization/google".equals(request.getServletPath())) {
            response.setHeader("Cache-Control", "no-store");
            response.sendRedirect("/");
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    new AppPrincipal(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }
}
