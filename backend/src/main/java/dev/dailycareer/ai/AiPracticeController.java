package dev.dailycareer.ai;

import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.api.ApiResponses;
import dev.dailycareer.common.json.Ids;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static dev.dailycareer.common.api.ApiErrorCode.AUTH_REQUIRED;

@RestController
@RequestMapping("/api/v1/ai/practice-sets")
public class AiPracticeController {
    private final AiPracticeService service;
    private final AiPracticeQuestionReports reports;

    AiPracticeController(AiPracticeService service, AiPracticeQuestionReports reports) {
        this.service = service;
        this.reports = reports;
    }

    @GetMapping
    ResponseEntity<?> saved(@AuthenticationPrincipal AppPrincipal principal, HttpServletRequest request) {
        return ApiResponses.ok(request, service.saved(user(principal)));
    }

    @GetMapping("/by-content/{contentId}")
    ResponseEntity<?> latest(@AuthenticationPrincipal AppPrincipal principal, @PathVariable String contentId,
                             @RequestParam(required = false) Integer difficulty,
                             HttpServletRequest request) {
        return ApiResponses.ok(request, service.latest(user(principal), Ids.parse(contentId), difficulty));
    }

    @GetMapping("/{jobId}")
    ResponseEntity<?> set(@AuthenticationPrincipal AppPrincipal principal, @PathVariable String jobId,
                          HttpServletRequest request) {
        return ApiResponses.ok(request, service.set(user(principal), Ids.parse(jobId)));
    }

    @PostMapping("/{jobId}/answers")
    ResponseEntity<?> submit(@AuthenticationPrincipal AppPrincipal principal, @PathVariable String jobId,
                             @RequestBody AnswerRequest answer, HttpServletRequest request) {
        return ApiResponses.ok(request, service.submit(user(principal), Ids.parse(jobId),
                answer.questionNo(), answer.answerNo()));
    }

    @PostMapping("/{jobId}/reports")
    ResponseEntity<?> report(@AuthenticationPrincipal AppPrincipal principal, @PathVariable String jobId,
                             @RequestBody ReportRequest body, HttpServletRequest request) {
        return ApiResponses.ok(request, reports.report(user(principal), Ids.parse(jobId),
                body.questionNo(), body.reason()));
    }

    record AnswerRequest(int questionNo, int answerNo) {}
    record ReportRequest(int questionNo, String reason) {}

    private static long user(AppPrincipal principal) {
        if (principal == null) throw new ApiException(AUTH_REQUIRED);
        return principal.userId();
    }
}
