package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dailycareer.common.api.*;
import dev.dailycareer.common.concurrency.Revisions;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.json.Ids;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/study-evaluations")
class StudyEvaluationController {
    private final StudyEvaluationService service;
    StudyEvaluationController(StudyEvaluationService service){this.service=service;}
    @GetMapping ResponseEntity<?> list(@AuthenticationPrincipal AppPrincipal p,HttpServletRequest r){return ApiResponses.ok(r,service.list(user(p)));}
    @GetMapping("/{id}") ResponseEntity<?> get(@AuthenticationPrincipal AppPrincipal p,@PathVariable String id,HttpServletRequest r){return ApiResponses.ok(r,service.get(user(p),Ids.parse(id)));}
    @PostMapping ResponseEntity<?> create(@AuthenticationPrincipal AppPrincipal p,@RequestBody JsonNode body,HttpServletRequest r) {
        return ApiResponses.ok(r,service.create(user(p),body.path("kind").asText(),body.path("key").asText(),body.hasNonNull("retakeOf")?Ids.parse(body.path("retakeOf").asText()):null,IdempotencyService.requiredKey(r)));
    }
    @PostMapping("/{id}/parts/{part}/{action}") ResponseEntity<?> action(@AuthenticationPrincipal AppPrincipal p,@PathVariable String id,@PathVariable int part,@PathVariable String action,@RequestBody(required=false) JsonNode body,HttpServletRequest r) {
        long user=user(p),evaluation=Ids.parse(id);
        return ApiResponses.ok(r,switch(action) {
            case "generate"->service.generate(user,evaluation,part);
            case "start"->service.start(user,evaluation,part);
            case "grade"->service.grade(user,evaluation,part);
            case "submit"->{if(body==null)throw new ApiException(ApiErrorCode.VALIDATION_FAILED);yield service.answer(user,evaluation,part,body.path("answers"),Revisions.required(r),true);}
            default->throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
        });
    }
    @PutMapping("/{id}/parts/{part}/answers") ResponseEntity<?> answers(@AuthenticationPrincipal AppPrincipal p,@PathVariable String id,@PathVariable int part,@RequestBody JsonNode body,HttpServletRequest r) {
        return ApiResponses.ok(r,service.answer(user(p),Ids.parse(id),part,body.path("answers"),Revisions.required(r),false));
    }
    private static long user(AppPrincipal p){if(p==null)throw new ApiException(ApiErrorCode.AUTH_REQUIRED);return p.userId();}
}
