package dev.dailycareer.learning;

import dev.dailycareer.common.api.*;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.json.Ids;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/extra-learning")
public class ExtraLearningController {
    private final ExtraLearningService service;
    public ExtraLearningController(ExtraLearningService service){this.service=service;}
    @GetMapping("/topics") ResponseEntity<?> topics(@AuthenticationPrincipal AppPrincipal p,@RequestParam ExtraLearningService.Mode mode,HttpServletRequest r){return ApiResponses.ok(r,service.topics(user(p),mode));}
    @GetMapping ResponseEntity<?> history(@AuthenticationPrincipal AppPrincipal p,HttpServletRequest r){return ApiResponses.ok(r,service.history(user(p)));}
    @GetMapping("/{sessionId}") ResponseEntity<?> detail(@AuthenticationPrincipal AppPrincipal p,@PathVariable String sessionId,HttpServletRequest r){return ApiResponses.ok(r,service.detail(user(p),Ids.parse(sessionId)));}
    @PostMapping ResponseEntity<?> start(@AuthenticationPrincipal AppPrincipal p,@Valid @RequestBody ExtraLearningService.Start body,HttpServletRequest r){return service.start(user(p),body,IdempotencyService.requiredKey(r)).toResponse(r);}
    @PostMapping("/{sessionId}/time") ResponseEntity<?> time(@AuthenticationPrincipal AppPrincipal p,@PathVariable String sessionId,@Valid @RequestBody LearningCommandService.Complete body,HttpServletRequest r){return service.recordTime(user(p),Ids.parse(sessionId),body,IdempotencyService.requiredKey(r)).toResponse(r);}
    private long user(AppPrincipal p){if(p==null)throw new ApiException(ApiErrorCode.AUTH_REQUIRED);return p.userId();}
}
