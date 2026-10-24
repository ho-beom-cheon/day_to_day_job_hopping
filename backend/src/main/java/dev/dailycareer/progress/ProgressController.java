package dev.dailycareer.progress;

import dev.dailycareer.common.api.*;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.json.Ids;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class ProgressController {
    private final ProgressService service; public ProgressController(ProgressService service){this.service=service;}
    @GetMapping("/learning-progress") ResponseEntity<?> overall(@AuthenticationPrincipal AppPrincipal p,@RequestParam(required=false) String curriculumId,HttpServletRequest r){return ApiResponses.ok(r,service.overall(user(p),optional(curriculumId)));}
    @GetMapping("/learning-progress/daily") ResponseEntity<?> daily(@AuthenticationPrincipal AppPrincipal p,@RequestParam(required=false) String curriculumId,@RequestParam String date,HttpServletRequest r){return ApiResponses.ok(r,service.daily(user(p),optional(curriculumId),date(date)));}
    @GetMapping("/learning-progress/weekly") ResponseEntity<?> weekly(@AuthenticationPrincipal AppPrincipal p,@RequestParam(required=false) String curriculumId,@RequestParam String week,HttpServletRequest r){return ApiResponses.ok(r,service.weekly(user(p),optional(curriculumId),week));}
    @GetMapping("/learning-progress/monthly") ResponseEntity<?> monthly(@AuthenticationPrincipal AppPrincipal p,@RequestParam(required=false) String curriculumId,@RequestParam int year,@RequestParam int month,HttpServletRequest r){return ApiResponses.ok(r,service.monthly(user(p),optional(curriculumId),year,month));}
    @GetMapping("/learning-calendar") ResponseEntity<?> calendar(@AuthenticationPrincipal AppPrincipal p,@RequestParam(required=false) String curriculumId,@RequestParam int year,@RequestParam int month,HttpServletRequest r){var value=service.calendar(user(p),optional(curriculumId),year,month);return value==null?ApiResponses.ok(r,null):ApiResponses.success(r,200,value,dev.dailycareer.common.concurrency.Revisions.etag(value.scheduleRevision()),null);}
    @GetMapping("/learning-progress/categories") ResponseEntity<?> categories(@AuthenticationPrincipal AppPrincipal p,@RequestParam(required=false) String curriculumId,HttpServletRequest r){return ApiResponses.ok(r,service.categories(user(p),optional(curriculumId)));}
    @GetMapping("/evaluations/monthly") ResponseEntity<?> evaluations(@AuthenticationPrincipal AppPrincipal p,@RequestParam(required=false) String curriculumId,@RequestParam(required=false) Integer monthNo,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="createdAt,desc") String sort,HttpServletRequest r){return ApiResponses.ok(r,service.evaluations(user(p),optional(curriculumId),monthNo,page,size,sort));}
    @PostMapping("/evaluations/monthly") ResponseEntity<?> create(@AuthenticationPrincipal AppPrincipal p,@Valid @RequestBody ProgressViews.EvaluationCreate body,HttpServletRequest r){return service.create(user(p),body,IdempotencyService.requiredKey(r)).toResponse(r);}
    @GetMapping("/evaluations/{evaluationId}") ResponseEntity<?> evaluation(@AuthenticationPrincipal AppPrincipal p,@PathVariable String evaluationId,HttpServletRequest r){return ApiResponses.ok(r,service.evaluation(user(p),Ids.parse(evaluationId)));}
    private static long user(AppPrincipal p){if(p==null)throw new ApiException(ApiErrorCode.AUTH_REQUIRED);return p.userId();}private static Long optional(String v){return v==null?null:Ids.parse(v);}private static LocalDate date(String value){try{return LocalDate.parse(value);}catch(Exception ignored){throw new ApiException(ApiErrorCode.VALIDATION_FAILED);}}
}
