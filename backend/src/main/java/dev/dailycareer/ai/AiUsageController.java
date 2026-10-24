package dev.dailycareer.ai;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.api.ApiResponses;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AiUsageController {
    private final AiCostService costs;
    AiUsageController(AiCostService costs) { this.costs = costs; }

    @GetMapping("/api/v1/ai/usage")
    ResponseEntity<?> usage(@AuthenticationPrincipal AppPrincipal principal, HttpServletRequest request) {
        if (principal == null) throw new ApiException(ApiErrorCode.AUTH_REQUIRED);
        return ApiResponses.ok(request, costs.summary(principal.userId()));
    }
}
