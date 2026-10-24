package dev.dailycareer.integration.notion;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.api.ApiResponses;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/notion")
public class NotionController {
    private final NotionService service;
    public NotionController(NotionService service) { this.service = service; }

    @GetMapping("/setup")
    ResponseEntity<?> setup(HttpServletRequest request) { return ApiResponses.ok(request, java.util.Map.of("configured", service.configured())); }

    @GetMapping
    ResponseEntity<?> connection(@AuthenticationPrincipal AppPrincipal principal, HttpServletRequest request) {
        return ApiResponses.ok(request, service.connection(user(principal)));
    }

    @DeleteMapping
    ResponseEntity<?> disconnect(@AuthenticationPrincipal AppPrincipal principal, HttpServletRequest request) throws IOException {
        noBody(request);
        return ApiResponses.ok(request, service.disconnect(user(principal)));
    }

    @PostMapping("/authorization-requests")
    ResponseEntity<?> authorization(@AuthenticationPrincipal AppPrincipal principal, HttpServletRequest request) throws IOException {
        noBody(request);
        return service.authorization(user(principal), request.getSession().getId(),
                IdempotencyService.requiredKey(request)).toResponse(request);
    }

    @GetMapping("/callback")
    ResponseEntity<Void> callback(@AuthenticationPrincipal AppPrincipal principal,
                                  @RequestParam(required = false) String state,
                                  @RequestParam(required = false) String code,
                                  @RequestParam(required = false) String error,
                                  HttpServletRequest request) {
        if (principal == null || request.getSession(false) == null) return redirect("login_required");
        if (state == null || state.isBlank() || (code == null) == (error == null)) return redirect("failed");
        if (error != null) {
            service.rejectAuthorization(principal.userId(), request.getSession(false).getId(), state);
            return redirect("failed");
        }
        boolean connected = service.completeAuthorization(principal.userId(), request.getSession(false).getId(), state, code);
        return redirect(connected ? "connected" : "failed");
    }

    private static ResponseEntity<Void> redirect(String result) {
        return ResponseEntity.status(302).location(URI.create("/settings/integrations?notion=" + result)).build();
    }
    private static long user(AppPrincipal principal) {
        if (principal == null) throw new ApiException(ApiErrorCode.AUTH_REQUIRED);
        return principal.userId();
    }
    private static void noBody(HttpServletRequest request) throws IOException {
        if (request.getInputStream().read() != -1) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
    }
}
