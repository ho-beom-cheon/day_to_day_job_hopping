package dev.dailycareer.coding;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.api.ApiResponses;
import dev.dailycareer.common.concurrency.Revisions;
import dev.dailycareer.common.json.Ids;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coding")
class CodingController {
    private final CodingTrack coding;

    CodingController(CodingTrack coding) { this.coding = coding; }

    @GetMapping
    ResponseEntity<?> overview(@AuthenticationPrincipal AppPrincipal p, HttpServletRequest r) { return ApiResponses.ok(r, coding.overview(user(p))); }

    @GetMapping("/settings")
    ResponseEntity<?> settings(@AuthenticationPrincipal AppPrincipal p, HttpServletRequest r) { return ApiResponses.ok(r, coding.settings(user(p))); }

    @PutMapping("/settings")
    ResponseEntity<?> updateSettings(@AuthenticationPrincipal AppPrincipal p, @RequestBody CodingTrack.Settings body, HttpServletRequest r) {
        return ApiResponses.ok(r, coding.updateSettings(user(p), body));
    }

    @GetMapping("/links")
    ResponseEntity<?> links(@AuthenticationPrincipal AppPrincipal p, HttpServletRequest r) { return ApiResponses.ok(r, coding.links(user(p))); }

    @PostMapping("/links")
    ResponseEntity<?> createLink(@AuthenticationPrincipal AppPrincipal p, @RequestBody CodingTrack.LinkRequest body, HttpServletRequest r) {
        return ApiResponses.success(r, 201, coding.createLink(user(p), body), null, null);
    }

    @PutMapping("/links/{linkId}/hidden")
    ResponseEntity<?> hide(@AuthenticationPrincipal AppPrincipal p, @PathVariable String linkId, HttpServletRequest r) {
        return ApiResponses.ok(r, coding.hide(user(p), Ids.parse(linkId), true));
    }

    @DeleteMapping("/links/{linkId}/hidden")
    ResponseEntity<?> unhide(@AuthenticationPrincipal AppPrincipal p, @PathVariable String linkId, HttpServletRequest r) {
        return ApiResponses.ok(r, coding.hide(user(p), Ids.parse(linkId), false));
    }

    @GetMapping("/logs")
    ResponseEntity<?> logs(@AuthenticationPrincipal AppPrincipal p, HttpServletRequest r) { return ApiResponses.ok(r, coding.logs(user(p))); }

    @PostMapping("/logs")
    ResponseEntity<?> createLog(@AuthenticationPrincipal AppPrincipal p, @RequestBody CodingTrack.LogRequest body, HttpServletRequest r) {
        return ApiResponses.success(r, 201, coding.createLog(user(p), body), null, null);
    }

    @PatchMapping("/logs/{logId}")
    ResponseEntity<?> patchLog(@AuthenticationPrincipal AppPrincipal p, @PathVariable String logId, @RequestBody CodingTrack.LogPatch body, HttpServletRequest r) {
        return ApiResponses.ok(r, coding.patchLog(user(p), Ids.parse(logId), body, Revisions.required(r)));
    }

    @PostMapping("/logs/{logId}/retry")
    ResponseEntity<?> retry(@AuthenticationPrincipal AppPrincipal p, @PathVariable String logId, HttpServletRequest r) {
        return ApiResponses.ok(r, coding.addRetry(user(p), Ids.parse(logId), Revisions.required(r)));
    }

    private static long user(AppPrincipal principal) {
        if (principal == null) throw new ApiException(ApiErrorCode.AUTH_REQUIRED);
        return principal.userId();
    }
}
