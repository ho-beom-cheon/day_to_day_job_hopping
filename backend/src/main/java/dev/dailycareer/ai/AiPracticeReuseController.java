package dev.dailycareer.ai;

import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.api.ApiResponses;
import dev.dailycareer.common.json.Ids;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static dev.dailycareer.common.api.ApiErrorCode.AUTH_REQUIRED;

@RestController
@RequestMapping("/api/v1/ai")
public class AiPracticeReuseController {
    private final AiPracticeReuseService service;

    AiPracticeReuseController(AiPracticeReuseService service) { this.service = service; }

    @PostMapping("/practice-sets/{jobId}/runs")
    ResponseEntity<?> start(@AuthenticationPrincipal AppPrincipal principal, @PathVariable String jobId,
                            @RequestBody StartRequest body, HttpServletRequest request) {
        return ApiResponses.ok(request, service.start(user(principal), Ids.parse(jobId), body.mode()));
    }

    @GetMapping("/practice-runs/{runId}")
    ResponseEntity<?> run(@AuthenticationPrincipal AppPrincipal principal, @PathVariable String runId,
                          HttpServletRequest request) {
        return ApiResponses.ok(request, service.run(user(principal), Ids.parse(runId)));
    }

    @PostMapping("/practice-runs/{runId}/answers")
    ResponseEntity<?> answer(@AuthenticationPrincipal AppPrincipal principal, @PathVariable String runId,
                             @RequestBody AnswerRequest body, HttpServletRequest request) {
        return ApiResponses.ok(request, service.submit(user(principal), Ids.parse(runId),
                body.questionNo(), body.answerNo()));
    }

    @GetMapping("/practice-runs")
    ResponseEntity<?> exams(@AuthenticationPrincipal AppPrincipal principal, HttpServletRequest request) {
        return ApiResponses.ok(request, service.exams(user(principal)));
    }

    @GetMapping("/wrong-notes")
    ResponseEntity<?> wrongs(@AuthenticationPrincipal AppPrincipal principal,
                             @RequestParam(required = false) String status, HttpServletRequest request) {
        return ApiResponses.ok(request, service.wrongs(user(principal), status));
    }

    @GetMapping("/wrong-notes/{jobId}/{questionNo}")
    ResponseEntity<?> wrong(@AuthenticationPrincipal AppPrincipal principal, @PathVariable String jobId,
                            @PathVariable int questionNo, HttpServletRequest request) {
        return ApiResponses.ok(request, service.wrong(user(principal), Ids.parse(jobId), questionNo));
    }

    @PatchMapping("/wrong-notes/{jobId}/{questionNo}")
    ResponseEntity<?> updateWrong(@AuthenticationPrincipal AppPrincipal principal, @PathVariable String jobId,
                                  @PathVariable int questionNo, @RequestBody WrongRequest body,
                                  HttpServletRequest request) {
        return ApiResponses.ok(request, service.updateWrong(user(principal), Ids.parse(jobId), questionNo,
                body.note(), body.status()));
    }

    record StartRequest(String mode) {}
    record AnswerRequest(int questionNo, int answerNo) {}
    record WrongRequest(String note, String status) {}

    private static long user(AppPrincipal principal) {
        if (principal == null) throw new ApiException(AUTH_REQUIRED);
        return principal.userId();
    }
}
