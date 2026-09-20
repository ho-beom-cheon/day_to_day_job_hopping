package dev.dailycareer.learning;

import dev.dailycareer.common.api.*;
import dev.dailycareer.common.concurrency.Revisions;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.json.Ids;
import dev.dailycareer.user.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static dev.dailycareer.learning.LearningCommandService.parseDate;

@RestController
@RequestMapping("/api/v1")
public class LearningController {
    private final LearningCatalog catalog; private final LearningQueryService queries; private final LearningCommandService commands; private final ScheduleCommandService schedules; private final ProblemService problems; private final UserAccountService users;
    public LearningController(LearningCatalog catalog,LearningQueryService queries,LearningCommandService commands,ScheduleCommandService schedules,ProblemService problems,UserAccountService users) {
        this.catalog=catalog;this.queries=queries;this.commands=commands;this.schedules=schedules;this.problems=problems;this.users=users;
    }
    @GetMapping("/curriculum-templates")
    ResponseEntity<?> templates(@AuthenticationPrincipal AppPrincipal p,HttpServletRequest r) {users.current(user(p));return ApiResponses.ok(r,catalog.templates());}
    @PostMapping("/curriculums")
    ResponseEntity<?> create(@AuthenticationPrincipal AppPrincipal p,@Valid @RequestBody LearningCommandService.Create body,HttpServletRequest r) {
        return commands.create(user(p),body,IdempotencyService.requiredKey(r)).toResponse(r);
    }
    @GetMapping("/curriculums/current")
    ResponseEntity<?> current(@AuthenticationPrincipal AppPrincipal p,HttpServletRequest r) {
        var c=queries.current(user(p));return ApiResponses.success(r,200,c,c==null?null:Revisions.etag(c.scheduleRevision()),null);
    }
    @GetMapping("/curriculums/{curriculumId}")
    ResponseEntity<?> course(@AuthenticationPrincipal AppPrincipal p,@PathVariable String curriculumId,HttpServletRequest r) {
        var c=queries.curriculum(user(p),Ids.parse(curriculumId));return ApiResponses.success(r,200,c,Revisions.etag(c.scheduleRevision()),null);
    }
    @GetMapping("/curriculums/{curriculumId}/months")
    ResponseEntity<?> months(@AuthenticationPrincipal AppPrincipal p,@PathVariable String curriculumId,HttpServletRequest r) {
        return ApiResponses.ok(r,queries.months(user(p),Ids.parse(curriculumId)));
    }
    @GetMapping("/curriculums/{curriculumId}/months/{monthNo}")
    ResponseEntity<?> month(@AuthenticationPrincipal AppPrincipal p,@PathVariable String curriculumId,@PathVariable int monthNo,HttpServletRequest r) {
        bounded(monthNo,6);return ApiResponses.ok(r,queries.month(user(p),Ids.parse(curriculumId),monthNo));
    }
    @GetMapping("/curriculums/{curriculumId}/weeks")
    ResponseEntity<?> weeks(@AuthenticationPrincipal AppPrincipal p,@PathVariable String curriculumId,HttpServletRequest r) {
        return ApiResponses.ok(r,queries.weeks(user(p),Ids.parse(curriculumId)));
    }
    @GetMapping("/curriculums/{curriculumId}/weeks/{weekNo}")
    ResponseEntity<?> week(@AuthenticationPrincipal AppPrincipal p,@PathVariable String curriculumId,@PathVariable int weekNo,HttpServletRequest r) {
        bounded(weekNo,60);return ApiResponses.ok(r,queries.week(user(p),Ids.parse(curriculumId),weekNo));
    }
    @PostMapping("/curriculums/{curriculumId}/reschedule-preview")
    ResponseEntity<?> preview(@AuthenticationPrincipal AppPrincipal p,@PathVariable String curriculumId,
                              @Valid @RequestBody ScheduleCommandService.PreviewRequest body,HttpServletRequest r) {
        return schedules.preview(user(p),Ids.parse(curriculumId),body,Revisions.required(r),IdempotencyService.requiredKey(r)).toResponse(r);
    }
    @PostMapping("/curriculums/{curriculumId}/reschedule")
    ResponseEntity<?> reschedule(@AuthenticationPrincipal AppPrincipal p,@PathVariable String curriculumId,
                                 @Valid @RequestBody ScheduleCommandService.ApplyPreview body,HttpServletRequest r) {
        return schedules.apply(user(p),Ids.parse(curriculumId),body.previewId(),Revisions.required(r),IdempotencyService.requiredKey(r)).toResponse(r);
    }
    @PostMapping("/rest-days")
    ResponseEntity<?> addRest(@AuthenticationPrincipal AppPrincipal p,@Valid @RequestBody ScheduleCommandService.RestDayRequest body,HttpServletRequest r) {
        return schedules.addRest(user(p),body,Revisions.required(r),IdempotencyService.requiredKey(r)).toResponse(r);
    }
    @DeleteMapping("/rest-days/{date}")
    ResponseEntity<?> removeRest(@AuthenticationPrincipal AppPrincipal p,@PathVariable String date,@RequestParam String previewId,HttpServletRequest r) throws IOException {
        noBody(r);return schedules.removeRest(user(p),parseDate(date),Ids.parse(previewId),Revisions.required(r),IdempotencyService.requiredKey(r)).toResponse(r);
    }
    @GetMapping("/learning-days/today")
    ResponseEntity<?> today(@AuthenticationPrincipal AppPrincipal p,HttpServletRequest r) {return ApiResponses.ok(r,queries.day(user(p),null,queries.today()));}
    @GetMapping("/learning-days/{date}")
    ResponseEntity<?> day(@AuthenticationPrincipal AppPrincipal p,@PathVariable String date,@RequestParam(required=false) String curriculumId,HttpServletRequest r) {
        return ApiResponses.ok(r,queries.day(user(p),optionalId(curriculumId),parseDate(date)));
    }
    @GetMapping("/learning-days")
    ResponseEntity<?> days(@AuthenticationPrincipal AppPrincipal p,@RequestParam(required=false) String curriculumId,@RequestParam String from,@RequestParam String to,HttpServletRequest r) {
        return ApiResponses.ok(r,queries.days(user(p),optionalId(curriculumId),parseDate(from),parseDate(to)));
    }
    @GetMapping("/learning-days/{date}/sessions")
    ResponseEntity<?> daySessions(@AuthenticationPrincipal AppPrincipal p,@PathVariable String date,@RequestParam(required=false) String curriculumId,HttpServletRequest r) {
        return ApiResponses.ok(r,queries.daySessions(user(p),optionalId(curriculumId),parseDate(date)));
    }
    @GetMapping("/learning-sessions/{sessionId}")
    ResponseEntity<?> session(@AuthenticationPrincipal AppPrincipal p,@PathVariable String sessionId,HttpServletRequest r) {return ApiResponses.ok(r,queries.session(user(p),Ids.parse(sessionId)));}
    @PostMapping("/learning-sessions/{sessionId}/start")
    ResponseEntity<?> start(@AuthenticationPrincipal AppPrincipal p,@PathVariable String sessionId,HttpServletRequest r) throws IOException {
        noBody(r);return ApiResponses.ok(r,commands.start(user(p),Ids.parse(sessionId)));
    }
    @PostMapping("/learning-sessions/{sessionId}/complete")
    ResponseEntity<?> complete(@AuthenticationPrincipal AppPrincipal p,@PathVariable String sessionId,@Valid @RequestBody LearningCommandService.Complete body,HttpServletRequest r) {
        return commands.complete(user(p),Ids.parse(sessionId),body,IdempotencyService.requiredKey(r)).toResponse(r);
    }
    @GetMapping("/learning-sessions/{sessionId}/contents")
    ResponseEntity<?> contents(@AuthenticationPrincipal AppPrincipal p,@PathVariable String sessionId,HttpServletRequest r) {return ApiResponses.ok(r,queries.contents(user(p),Ids.parse(sessionId)));}
    @GetMapping("/learning-contents/{contentId}")
    ResponseEntity<?> content(@AuthenticationPrincipal AppPrincipal p,@PathVariable String contentId,HttpServletRequest r) {return ApiResponses.ok(r,queries.content(user(p),Ids.parse(contentId)));}
    @PostMapping("/learning-contents/{contentId}/complete")
    ResponseEntity<?> completeContent(@AuthenticationPrincipal AppPrincipal p,@PathVariable String contentId,HttpServletRequest r) throws IOException {
        noBody(r);return ApiResponses.ok(r,commands.completeContent(user(p),Ids.parse(contentId)));
    }
    @GetMapping("/problems/{problemId}")
    ResponseEntity<?> problem(@AuthenticationPrincipal AppPrincipal p,@PathVariable String problemId,HttpServletRequest r) {
        return ApiResponses.ok(r,problems.problem(user(p),Ids.parse(problemId)));
    }
    @PostMapping("/problem-attempts")
    ResponseEntity<?> submitProblem(@AuthenticationPrincipal AppPrincipal p,@Valid @RequestBody ProblemService.AttemptRequest body,HttpServletRequest r) {
        return problems.submit(user(p),body,IdempotencyService.requiredKey(r)).toResponse(r);
    }
    @GetMapping("/problem-attempts/{attemptId}")
    ResponseEntity<?> problemAttempt(@AuthenticationPrincipal AppPrincipal p,@PathVariable String attemptId,HttpServletRequest r) {
        return ApiResponses.ok(r,problems.attempt(user(p),Ids.parse(attemptId)));
    }
    @PostMapping("/problems/{problemId}/reports")
    ResponseEntity<?> reportProblem(@AuthenticationPrincipal AppPrincipal p,@PathVariable String problemId,@Valid @RequestBody ProblemService.ReportRequest body,HttpServletRequest r) {
        return problems.report(user(p),Ids.parse(problemId),body,IdempotencyService.requiredKey(r)).toResponse(r);
    }
    @GetMapping("/wrong-answers")
    ResponseEntity<?> wrongAnswers(@AuthenticationPrincipal AppPrincipal p,@RequestParam(required=false) String category,@RequestParam(required=false) String status,
                                   @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
                                   @RequestParam(defaultValue="lastWrongAt,desc") String sort,HttpServletRequest r) {
        return ApiResponses.ok(r,problems.wrongs(user(p),category,status,page,size,sort));
    }
    @GetMapping("/wrong-answers/{wrongAnswerId}")
    ResponseEntity<?> wrongAnswer(@AuthenticationPrincipal AppPrincipal p,@PathVariable String wrongAnswerId,HttpServletRequest r) {
        var result=problems.wrong(user(p),Ids.parse(wrongAnswerId));return ApiResponses.success(r,200,result,Revisions.etag(result.revision()),null);
    }
    @PatchMapping("/wrong-answers/{wrongAnswerId}")
    ResponseEntity<?> patchWrong(@AuthenticationPrincipal AppPrincipal p,@PathVariable String wrongAnswerId,@Valid @RequestBody ProblemService.NoteRequest body,HttpServletRequest r) {
        var result=problems.note(user(p),Ids.parse(wrongAnswerId),body,Revisions.required(r));return ApiResponses.success(r,200,result,Revisions.etag(result.revision()),null);
    }
    @PostMapping("/wrong-answers/{wrongAnswerId}/resolve")
    ResponseEntity<?> resolveWrong(@AuthenticationPrincipal AppPrincipal p,@PathVariable String wrongAnswerId,@Valid @RequestBody ProblemService.ResolveRequest body,HttpServletRequest r) {
        return problems.resolve(user(p),Ids.parse(wrongAnswerId),body,IdempotencyService.requiredKey(r)).toResponse(r);
    }
    @PostMapping("/wrong-answers/{wrongAnswerId}/reopen")
    ResponseEntity<?> reopenWrong(@AuthenticationPrincipal AppPrincipal p,@PathVariable String wrongAnswerId,HttpServletRequest r) throws IOException {
        noBody(r);return ApiResponses.ok(r,problems.reopen(user(p),Ids.parse(wrongAnswerId)));
    }
    private static long user(AppPrincipal p) {if(p==null)throw new ApiException(ApiErrorCode.AUTH_REQUIRED);return p.userId();}
    private static Long optionalId(String value) {return value==null?null:Ids.parse(value);}
    private static void bounded(int value,int max) {if(value<1 || value>max)throw new ApiException(ApiErrorCode.VALIDATION_FAILED);}
    private static void noBody(HttpServletRequest r) throws IOException {if(r.getInputStream().read()!=-1)throw new ApiException(ApiErrorCode.INVALID_REQUEST);}
}
