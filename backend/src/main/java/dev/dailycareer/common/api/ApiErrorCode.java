package dev.dailycareer.common.api;

/** Shared codes already declared by the canonical OpenAPI; domain codes belong to their features. */
public enum ApiErrorCode {
    INVALID_REQUEST(400, "요청 형식이 올바르지 않습니다."),
    VALIDATION_FAILED(400, "입력값을 확인해 주세요."),
    INVALID_SORT_FIELD(400, "지원하지 않는 정렬 기준입니다."),
    IDEMPOTENCY_KEY_REQUIRED(400, "Idempotency-Key 헤더가 필요합니다."),
    AUTH_REQUIRED(401, "로그인이 필요합니다."),
    CSRF_INVALID(403, "요청 검증 토큰을 다시 발급받아 주세요."),
    TEST_CONTENT_RESTRICTED(403, "시험 전용 문제는 일반 문제풀이에서 이용할 수 없습니다."),
    RESOURCE_NOT_FOUND(404, "요청한 리소스를 찾을 수 없습니다."),
    IDEMPOTENCY_KEY_REUSED(409, "같은 요청 키를 다른 작업 내용에 사용할 수 없습니다."),
    REQUEST_IN_PROGRESS(409, "같은 요청을 처리 중입니다.", true, 1),
    CURRICULUM_ALREADY_ACTIVE(409, "이미 진행 중인 과정이 있습니다."),
    TEMPLATE_NOT_PUBLISHED(409, "배포되지 않은 템플릿입니다."),
    CONTENT_NOT_READY(409, "학습 콘텐츠가 준비되지 않았습니다."),
    LEARNING_NOT_AVAILABLE(409, "예정된 날짜부터 학습을 시작할 수 있습니다."),
    SESSION_ALREADY_COMPLETED(409, "이미 완료된 세션입니다."),
    SESSION_NOT_STARTED(409, "먼저 학습 세션을 시작해 주세요."),
    RESCHEDULE_PREVIEW_EXPIRED(409, "일정 변경 미리보기가 만료되었습니다."),
    RESCHEDULE_PREVIEW_MISMATCH(409, "미리보기와 적용 요청이 일치하지 않습니다."),
    RESCHEDULE_PREVIEW_CONSUMED(409, "이미 적용한 일정 변경 미리보기입니다."),
    RESCHEDULE_LOCKED_DAY(409, "시작했거나 완료한 학습일은 이동할 수 없습니다."),
    SESSION_CONTENT_INCOMPLETE(422, "필수 콘텐츠를 모두 완료해 주세요."),
    CONTENT_PROBLEMS_INCOMPLETE(422, "확인 문제를 모두 제출해 주세요."),
    PROBLEM_INVALID_ANSWER(422, "문제 유형에 맞는 답안을 입력해 주세요."),
    WRONG_RESOLUTION_EVIDENCE_INVALID(422, "오답 해결 근거를 확인해 주세요."),
    RESCHEDULE_OUT_OF_RANGE(422, "일정이 과정 시작일부터 366일을 초과합니다."),
    PRECONDITION_FAILED(412, "다른 변경이 반영되었습니다. 최신 내용을 다시 조회해 주세요."),
    PRECONDITION_REQUIRED(428, "최신 ETag를 If-Match 헤더에 전달해 주세요."),
    INTERNAL_SERVER_ERROR(500, "요청을 처리하지 못했습니다."),
    SERVICE_UNAVAILABLE(503, "현재 서비스를 이용할 수 없습니다.", true, null);

    private final int status;
    private final String message;
    private final boolean retryable;
    private final Integer retryAfterSeconds;

    ApiErrorCode(int status, String message) { this(status, message, false, null); }
    ApiErrorCode(int status, String message, boolean retryable, Integer retryAfterSeconds) {
        this.status = status;
        this.message = message;
        this.retryable = retryable;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public int status() { return status; }
    public String message() { return message; }
    public boolean retryable() { return retryable; }
    public Integer retryAfterSeconds() { return retryAfterSeconds; }
}
