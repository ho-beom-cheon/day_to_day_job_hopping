package dev.dailycareer.integration.slack;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.api.ApiResponses;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/slack")
public class SlackController {
    private final SlackService service;
    public SlackController(SlackService service) { this.service = service; }

    @PostMapping("/test-messages")
    ResponseEntity<?> test(@AuthenticationPrincipal AppPrincipal principal, HttpServletRequest request) {
        return service.submitTest(user(principal), IdempotencyService.requiredKey(request)).toResponse(request);
    }

    @GetMapping("/deliveries")
    ResponseEntity<?> list(@AuthenticationPrincipal AppPrincipal principal,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size,
                           @RequestParam(defaultValue = "createdAt,desc") String sort,
                           HttpServletRequest request) {
        return ApiResponses.ok(request, service.list(user(principal), page, size, sort));
    }

    @GetMapping("/deliveries/{deliveryId}")
    ResponseEntity<?> get(@AuthenticationPrincipal AppPrincipal principal, @PathVariable long deliveryId,
                          HttpServletRequest request) {
        return ApiResponses.ok(request, service.get(user(principal), deliveryId));
    }

    private static long user(AppPrincipal principal) {
        if (principal == null) throw new ApiException(ApiErrorCode.AUTH_REQUIRED);
        return principal.userId();
    }
}
