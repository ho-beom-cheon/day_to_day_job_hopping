package dev.dailycareer.dashboard;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.api.ApiResponses;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardService service; public DashboardController(DashboardService service){this.service=service;}
    @GetMapping ResponseEntity<?> dashboard(@AuthenticationPrincipal AppPrincipal principal,HttpServletRequest request){if(principal==null)throw new ApiException(ApiErrorCode.AUTH_REQUIRED);return ApiResponses.ok(request,service.dashboard(principal.userId()));}
}
