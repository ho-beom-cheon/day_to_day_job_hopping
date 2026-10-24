package dev.dailycareer.integration;

import dev.dailycareer.common.api.*;
import dev.dailycareer.common.concurrency.Revisions;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class IntegrationController {
    private final IntegrationService service; public IntegrationController(IntegrationService service){this.service=service;}
    @GetMapping("/notifications/settings") ResponseEntity<?> settings(@AuthenticationPrincipal AppPrincipal p,HttpServletRequest r){var v=service.settings(user(p));return ApiResponses.success(r,200,v,Revisions.etag(v.revision()),null);}
    @PatchMapping("/notifications/settings") ResponseEntity<?> updateSettings(@AuthenticationPrincipal AppPrincipal p,@RequestBody IntegrationViews.NotificationSettingsPatch body,HttpServletRequest r){var v=service.update(user(p),body,Revisions.required(r));return ApiResponses.success(r,200,v,Revisions.etag(v.revision()),null);}
    @GetMapping("/push/config") ResponseEntity<?> push(HttpServletRequest r){return ApiResponses.ok(r,service.pushConfig());}
    @GetMapping("/integrations/slack") ResponseEntity<?> slack(@AuthenticationPrincipal AppPrincipal p,HttpServletRequest r){var v=service.slack(user(p));return ApiResponses.success(r,200,v,Revisions.etag(v.revision()),null);}
    @PatchMapping("/integrations/slack") ResponseEntity<?> updateSlack(@AuthenticationPrincipal AppPrincipal p,@RequestBody IntegrationViews.SlackPatch body,HttpServletRequest r){var v=service.updateSlack(user(p),body,Revisions.required(r));return ApiResponses.success(r,200,v,Revisions.etag(v.revision()),null);}
    private static long user(AppPrincipal p){if(p==null)throw new ApiException(ApiErrorCode.AUTH_REQUIRED);return p.userId();}
}
