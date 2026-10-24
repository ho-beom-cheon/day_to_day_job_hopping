package dev.dailycareer.notification;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping
    ResponseEntity<?> list(@AuthenticationPrincipal AppPrincipal principal,
                           @RequestParam(required = false) Boolean read,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size,
                           @RequestParam(defaultValue = "createdAt,desc") String sort,
                           HttpServletRequest request) {
        return ApiResponses.ok(request, service.list(user(principal), read, page, size, sort));
    }

    @PostMapping("/{notificationId}/read")
    ResponseEntity<?> read(@AuthenticationPrincipal AppPrincipal principal, @PathVariable String notificationId,
                           HttpServletRequest request) throws IOException {
        noBody(request);
        return ApiResponses.ok(request, service.markRead(user(principal), Ids.parse(notificationId)));
    }

    @PostMapping("/read-all")
    ResponseEntity<?> readAll(@AuthenticationPrincipal AppPrincipal principal,
                              @RequestBody NotificationViews.ReadAllRequest body, HttpServletRequest request) {
        return service.markAllRead(user(principal), body, IdempotencyService.requiredKey(request)).toResponse(request);
    }

    private static void noBody(HttpServletRequest request) throws IOException {
        if (request.getInputStream().read() != -1) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
    }
    private static long user(AppPrincipal principal) {
        if (principal == null) throw new ApiException(ApiErrorCode.AUTH_REQUIRED);
        return principal.userId();
    }
}
