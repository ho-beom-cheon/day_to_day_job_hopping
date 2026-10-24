package dev.dailycareer.notification.push;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.api.ApiResponses;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.json.Ids;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/push/subscriptions")
public class PushSubscriptionController {
    private final PushSubscriptionService service;
    public PushSubscriptionController(PushSubscriptionService service) { this.service = service; }

    @PostMapping
    ResponseEntity<?> subscribe(@AuthenticationPrincipal AppPrincipal principal,
                                @RequestBody PushViews.PushSubscribe body, HttpServletRequest request) {
        return service.subscribe(user(principal), body, IdempotencyService.requiredKey(request)).toResponse(request);
    }

    @GetMapping
    ResponseEntity<?> list(@AuthenticationPrincipal AppPrincipal principal, HttpServletRequest request) {
        return ApiResponses.ok(request, service.list(user(principal)));
    }

    @DeleteMapping("/{subscriptionId}")
    ResponseEntity<?> delete(@AuthenticationPrincipal AppPrincipal principal, @PathVariable String subscriptionId,
                             HttpServletRequest request) throws IOException {
        if (request.getInputStream().read() != -1) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        return ApiResponses.ok(request, service.delete(user(principal), Ids.parse(subscriptionId)));
    }

    private static long user(AppPrincipal principal) {
        if (principal == null) throw new ApiException(ApiErrorCode.AUTH_REQUIRED);
        return principal.userId();
    }
}
