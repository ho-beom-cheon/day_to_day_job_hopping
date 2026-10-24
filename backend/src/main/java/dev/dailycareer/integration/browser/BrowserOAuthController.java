package dev.dailycareer.integration.browser;

import dev.dailycareer.common.api.*;
import dev.dailycareer.common.concurrency.Revisions;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/integrations/browser/{provider:slack|openrouter}")
public class BrowserOAuthController {
    private final BrowserConnections connections;
    public BrowserOAuthController(BrowserConnections connections) { this.connections = connections; }
    @GetMapping ResponseEntity<?> status(@AuthenticationPrincipal AppPrincipal principal, @PathVariable String provider, HttpServletRequest request) {
        return ApiResponses.ok(request, connections.status(user(principal), provider));
    }
    @PostMapping("/authorization-requests") ResponseEntity<?> authorize(@AuthenticationPrincipal AppPrincipal principal,
            @PathVariable String provider, HttpServletRequest request) {
        return ApiResponses.ok(request, connections.authorize(user(principal), provider, request.getSession().getId()));
    }
    @DeleteMapping ResponseEntity<?> disconnect(@AuthenticationPrincipal AppPrincipal principal, @PathVariable String provider, HttpServletRequest request) {
        return ApiResponses.ok(request, connections.disconnect(user(principal), provider, Revisions.required(request)));
    }
    @GetMapping("/callback") ResponseEntity<Void> callback(@AuthenticationPrincipal AppPrincipal principal, @PathVariable String provider,
            @RequestParam(required=false) String state, @RequestParam(required=false) String code,
            @RequestParam(required=false) String error, HttpServletRequest request) {
        boolean loggedIn = principal != null && request.getSession(false) != null;
        boolean ok = loggedIn && connections.callback(principal.userId(), provider, request.getSession(false).getId(), state, code, error);
        return ResponseEntity.status(302).header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer")
                .location(URI.create("/settings/integrations?" + provider + "=" + (ok ? "connected" : loggedIn ? "failed" : "login_required"))).build();
    }
    private static long user(AppPrincipal principal) { if (principal == null) throw new ApiException(ApiErrorCode.AUTH_REQUIRED); return principal.userId(); }
}
