package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dailycareer.common.api.*;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/learning-topics")
class AiLessonController {
    private final AiService ai;private final AiLessonCatalog catalog;
    AiLessonController(AiService ai,AiLessonCatalog catalog){this.ai=ai;this.catalog=catalog;}
    @PostMapping ResponseEntity<?> create(@AuthenticationPrincipal AppPrincipal p,@RequestBody JsonNode input,HttpServletRequest r){
        return ai.create(user(p),"AI_LESSON-001",AiLessonContent.PURPOSE,input,IdempotencyService.requiredKey(r)).toResponse(r);
    }
    @GetMapping ResponseEntity<?> list(@AuthenticationPrincipal AppPrincipal p,HttpServletRequest r){return ApiResponses.ok(r,catalog.list(user(p)));}
    private static long user(AppPrincipal p){if(p==null)throw new ApiException(ApiErrorCode.AUTH_REQUIRED);return p.userId();}
}
