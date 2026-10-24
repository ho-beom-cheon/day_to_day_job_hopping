package dev.dailycareer.competency;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.api.ApiResponses;
import dev.dailycareer.common.concurrency.Revisions;
import dev.dailycareer.common.json.Ids;
import dev.dailycareer.user.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static dev.dailycareer.competency.DailyTaskViews.*;

@RestController
@RequestMapping("/api/v1")
class DailyTaskController {
    private final DailyTaskService tasks;
    private final CareerFacts career;

    DailyTaskController(DailyTaskService tasks, CareerFacts career) {
        this.tasks = tasks;
        this.career = career;
    }

    @GetMapping("/today")
    ResponseEntity<?> today(@AuthenticationPrincipal AppPrincipal p, HttpServletRequest r) { return ApiResponses.ok(r, tasks.today(user(p))); }

    @PostMapping("/today")
    ResponseEntity<?> startToday(@AuthenticationPrincipal AppPrincipal p, HttpServletRequest r) { return ApiResponses.ok(r, tasks.startToday(user(p))); }

    @GetMapping("/tasks/{taskId}")
    ResponseEntity<?> task(@AuthenticationPrincipal AppPrincipal p, @PathVariable String taskId, HttpServletRequest r) {
        return ApiResponses.ok(r, tasks.view(user(p), Ids.parse(taskId)));
    }

    @PostMapping("/tasks/{taskId}/start")
    ResponseEntity<?> start(@AuthenticationPrincipal AppPrincipal p, @PathVariable String taskId, HttpServletRequest r) {
        return ApiResponses.ok(r, tasks.start(user(p), Ids.parse(taskId)));
    }

    @PutMapping("/tasks/{taskId}/turns/{turnNo}/answer")
    ResponseEntity<?> draft(@AuthenticationPrincipal AppPrincipal p, @PathVariable String taskId, @PathVariable int turnNo,
                            @RequestBody AnswerRequest body, HttpServletRequest r) {
        return ApiResponses.ok(r, tasks.saveDraft(user(p), Ids.parse(taskId), turnNo, body));
    }

    @PostMapping("/tasks/{taskId}/turns/{turnNo}/submit")
    ResponseEntity<?> submit(@AuthenticationPrincipal AppPrincipal p, @PathVariable String taskId, @PathVariable int turnNo,
                             @RequestBody AnswerRequest body, HttpServletRequest r) {
        return ApiResponses.ok(r, tasks.submit(user(p), Ids.parse(taskId), turnNo, body));
    }

    @PostMapping("/tasks/{taskId}/self-grade")
    ResponseEntity<?> selfGrade(@AuthenticationPrincipal AppPrincipal p, @PathVariable String taskId, @RequestBody SelfGradeRequest body, HttpServletRequest r) {
        return ApiResponses.ok(r, tasks.selfGrade(user(p), Ids.parse(taskId), body));
    }

    @PostMapping("/tasks/{taskId}/finish")
    ResponseEntity<?> finish(@AuthenticationPrincipal AppPrincipal p, @PathVariable String taskId, HttpServletRequest r) {
        return ApiResponses.ok(r, tasks.finish(user(p), Ids.parse(taskId)));
    }

    @PostMapping("/tasks/{taskId}/swap")
    ResponseEntity<?> swap(@AuthenticationPrincipal AppPrincipal p, @PathVariable String taskId, HttpServletRequest r) {
        return ApiResponses.ok(r, tasks.swap(user(p), Ids.parse(taskId)));
    }

    @PutMapping("/tasks/{taskId}/card")
    ResponseEntity<?> card(@AuthenticationPrincipal AppPrincipal p, @PathVariable String taskId, @RequestBody CardRequest body, HttpServletRequest r) {
        return ApiResponses.ok(r, tasks.saveCard(user(p), Ids.parse(taskId), body));
    }

    @PostMapping("/tasks/{taskId}/feedback")
    ResponseEntity<?> feedback(@AuthenticationPrincipal AppPrincipal p, @PathVariable String taskId, @RequestBody FeedbackRequest body, HttpServletRequest r) {
        return ApiResponses.ok(r, tasks.feedback(user(p), Ids.parse(taskId), body));
    }

    @PostMapping("/tasks/{taskId}/reports")
    ResponseEntity<?> report(@AuthenticationPrincipal AppPrincipal p, @PathVariable String taskId, @RequestBody ReportRequest body, HttpServletRequest r) {
        return ApiResponses.ok(r, tasks.report(user(p), Ids.parse(taskId), body));
    }

    @GetMapping("/career/facts")
    ResponseEntity<?> facts(@AuthenticationPrincipal AppPrincipal p, HttpServletRequest r) { return ApiResponses.ok(r, career.facts(user(p))); }

    @PostMapping("/career/facts")
    ResponseEntity<?> createFact(@AuthenticationPrincipal AppPrincipal p, @RequestBody CareerFacts.FactRequest body, HttpServletRequest r) {
        return ApiResponses.success(r, 201, career.createFact(user(p), body), null, null);
    }

    @PatchMapping("/career/facts/{factId}")
    ResponseEntity<?> updateFact(@AuthenticationPrincipal AppPrincipal p, @PathVariable String factId, @RequestBody CareerFacts.FactRequest body, HttpServletRequest r) {
        return ApiResponses.ok(r, career.updateFact(user(p), Ids.parse(factId), body, Revisions.required(r)));
    }

    @PostMapping("/career/facts/{factId}/confirm")
    ResponseEntity<?> confirmFact(@AuthenticationPrincipal AppPrincipal p, @PathVariable String factId, HttpServletRequest r) {
        return ApiResponses.ok(r, career.confirmFact(user(p), Ids.parse(factId), Revisions.required(r)));
    }

    @GetMapping("/career/claims")
    ResponseEntity<?> claims(@AuthenticationPrincipal AppPrincipal p, HttpServletRequest r) { return ApiResponses.ok(r, career.claims(user(p))); }

    @PostMapping("/career/claims")
    ResponseEntity<?> createClaim(@AuthenticationPrincipal AppPrincipal p, @RequestBody CareerFacts.ClaimRequest body, HttpServletRequest r) {
        return ApiResponses.success(r, 201, career.createClaim(user(p), body), null, null);
    }

    @PatchMapping("/career/claims/{claimId}")
    ResponseEntity<?> patchClaim(@AuthenticationPrincipal AppPrincipal p, @PathVariable String claimId, @RequestBody CareerFacts.ClaimPatch body, HttpServletRequest r) {
        return ApiResponses.ok(r, career.patchClaim(user(p), Ids.parse(claimId), body, Revisions.required(r)));
    }

    @PostMapping("/career/claims/{claimId}/review")
    ResponseEntity<?> review(@AuthenticationPrincipal AppPrincipal p, @PathVariable String claimId, HttpServletRequest r) {
        return ApiResponses.ok(r, career.review(user(p), Ids.parse(claimId)));
    }

    @PostMapping("/career/claims/{claimId}/interview")
    ResponseEntity<?> interview(@AuthenticationPrincipal AppPrincipal p, @PathVariable String claimId, HttpServletRequest r) {
        return ApiResponses.ok(r, career.interview(user(p), Ids.parse(claimId)));
    }

    private static long user(AppPrincipal principal) {
        if (principal == null) throw new ApiException(ApiErrorCode.AUTH_REQUIRED);
        return principal.userId();
    }
}
