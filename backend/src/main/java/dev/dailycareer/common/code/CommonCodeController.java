package dev.dailycareer.common.code;

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

@RestController @RequestMapping("/api/v1/common-codes")
public class CommonCodeController {
    private final CommonCodeService service; public CommonCodeController(CommonCodeService service){this.service=service;}
    @GetMapping ResponseEntity<?> codes(@AuthenticationPrincipal AppPrincipal principal,HttpServletRequest request){if(principal==null)throw new ApiException(ApiErrorCode.AUTH_REQUIRED);return ApiResponses.ok(request,service.codes(principal.userId()));}
}
