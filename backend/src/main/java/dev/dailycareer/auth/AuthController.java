package dev.dailycareer.auth;

import dev.dailycareer.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    public record Token(String headerName, String token) {}
    public record Logout(boolean loggedOut) {}

    @GetMapping("/csrf")
    public ResponseEntity<ApiEnvelope<Token>> csrf(CsrfToken token, HttpServletRequest request) {
        return ApiResponses.ok(request, new Token(token.getHeaderName(), token.getToken()));
    }
    @PostMapping("/logout")
    public ResponseEntity<ApiEnvelope<Logout>> logout(HttpServletRequest request, HttpServletResponse response,
                                                     Authentication authentication) throws IOException {
        if (request.getInputStream().read() != -1) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ApiResponses.ok(request, new Logout(true));
    }
}
