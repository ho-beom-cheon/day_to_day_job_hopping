package dev.dailycareer.user;

import dev.dailycareer.common.api.ApiEnvelope;
import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.api.ApiResponses;
import dev.dailycareer.common.concurrency.Revisions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {
    private final UserAccountService users;
    public UserController(UserAccountService users) { this.users = users; }
    public record Patch(String nickname) {}

    @GetMapping
    public ResponseEntity<ApiEnvelope<UserView>> get(@AuthenticationPrincipal AppPrincipal principal, HttpServletRequest request) {
        return response(request, users.current(id(principal)));
    }
    @PatchMapping
    public ResponseEntity<ApiEnvelope<UserView>> patch(@AuthenticationPrincipal AppPrincipal principal,
            @RequestBody Patch patch, HttpServletRequest request) {
        return response(request, users.rename(id(principal), Revisions.required(request), patch.nickname()));
    }
    private static long id(AppPrincipal principal) {
        if (principal == null) throw new ApiException(ApiErrorCode.AUTH_REQUIRED);
        return principal.userId();
    }
    private static ResponseEntity<ApiEnvelope<UserView>> response(HttpServletRequest request, UserView user) {
        return ApiResponses.success(request, 200, user, Revisions.etag(user.revision()), null);
    }
}
