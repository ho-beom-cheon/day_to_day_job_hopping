package dev.dailycareer.exam;

import dev.dailycareer.common.api.*;
import dev.dailycareer.common.concurrency.Revisions;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.json.Ids;
import dev.dailycareer.user.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ExamController {
    private final ExamService exams;
    public ExamController(ExamService exams){this.exams=exams;}

    @GetMapping("/tests")
    ResponseEntity<?> tests(@AuthenticationPrincipal AppPrincipal p,@RequestParam(required=false) String type,@RequestParam(required=false) String status,
                            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest r){return ApiResponses.ok(r,exams.exams(user(p),type,status,page,size));}
    @GetMapping("/tests/{testId}")
    ResponseEntity<?> test(@AuthenticationPrincipal AppPrincipal p,@PathVariable String testId,HttpServletRequest r){return ApiResponses.ok(r,exams.exam(user(p),Ids.parse(testId)));}
    @PostMapping("/tests/{testId}/attempts")
    ResponseEntity<?> start(@AuthenticationPrincipal AppPrincipal p,@PathVariable String testId,HttpServletRequest r){return exams.start(user(p),Ids.parse(testId),IdempotencyService.requiredKey(r)).toResponse(r);}
    @GetMapping("/test-attempts/{attemptId}")
    ResponseEntity<?> attempt(@AuthenticationPrincipal AppPrincipal p,@PathVariable String attemptId,HttpServletRequest r){return ApiResponses.ok(r,exams.attempt(user(p),Ids.parse(attemptId)));}
    @PutMapping("/test-attempts/{attemptId}/answers/{problemId}")
    ResponseEntity<?> answer(@AuthenticationPrincipal AppPrincipal p,@PathVariable String attemptId,@PathVariable String problemId,@Valid @RequestBody ExamService.AnswerRequest body,HttpServletRequest r){var saved=exams.save(user(p),Ids.parse(attemptId),Ids.parse(problemId),body,Revisions.required(r));return ApiResponses.success(r,200,saved,Revisions.etag(saved.answerRevision()),null);}
    @PostMapping("/test-attempts/{attemptId}/submit")
    ResponseEntity<?> submit(@AuthenticationPrincipal AppPrincipal p,@PathVariable String attemptId,HttpServletRequest r){return exams.submit(user(p),Ids.parse(attemptId),IdempotencyService.requiredKey(r)).toResponse(r);}
    @GetMapping("/test-attempts/{attemptId}/result")
    ResponseEntity<?> result(@AuthenticationPrincipal AppPrincipal p,@PathVariable String attemptId,HttpServletRequest r){return ApiResponses.ok(r,exams.result(user(p),Ids.parse(attemptId)));}
    @GetMapping("/test-attempts")
    ResponseEntity<?> attempts(@AuthenticationPrincipal AppPrincipal p,@RequestParam(required=false) String testId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest r){return ApiResponses.ok(r,exams.attempts(user(p),testId==null?0:Ids.parse(testId),page,size));}
    private static long user(AppPrincipal p){if(p==null)throw new ApiException(ApiErrorCode.AUTH_REQUIRED);return p.userId();}
}
