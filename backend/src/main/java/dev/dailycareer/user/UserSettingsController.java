package dev.dailycareer.user;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.api.ApiResponses;
import dev.dailycareer.common.concurrency.Revisions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/settings")
public class UserSettingsController {
    private final UserSettingsService service; public UserSettingsController(UserSettingsService service){this.service=service;}
    @GetMapping ResponseEntity<?> settings(@AuthenticationPrincipal AppPrincipal principal,HttpServletRequest request){var value=service.settings(user(principal));return ApiResponses.success(request,200,value,Revisions.etag(value.revision()),null);}
    @PatchMapping ResponseEntity<?> update(@AuthenticationPrincipal AppPrincipal principal,@RequestBody SettingsViews.SettingsPatch body,HttpServletRequest request){var value=service.update(user(principal),body,Revisions.required(request));return ApiResponses.success(request,200,value,Revisions.etag(value.revision()),null);}
    private static long user(AppPrincipal principal){if(principal==null)throw new ApiException(ApiErrorCode.AUTH_REQUIRED);return principal.userId();}
}
