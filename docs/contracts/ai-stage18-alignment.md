# 작업 18 AI 기능·피드백 UI 계약 대조

- OpenAPI v1.2.1 AI-001~008의 5개 생성 요청, 작업 단건·목록, 사용량 경로를 유지했다.
- 내부 `QUEUED/RUNNING/SUCCEEDED/FAILED`는 공개 `READY/PROCESSING/SUCCESS/FAILED`로 매핑한다. 내부 `UNKNOWN/CANCELED`를 공개 성공이나 사용자 취소 기능으로 노출하지 않는다.
- `ai_job`은 논리 작업, `ai_usage`는 실제 공급자 호출로 구분한다. 같은 데이터 버전의 성공 결과는 재사용한다.
- 공급자 endpoint·key·model은 서버 환경변수로만 받는다. Browser가 provider, model, system prompt 또는 key를 전달하지 않는다.
- `/assistant`는 실제 enabled/사용량/작업 상태를 표시하고, 공급자 미설정 시 연결 대기 상태를 보여준다.

실제 공급자와 승인된 운영 템플릿이 없어 외부 성공 호출과 성공 피드백 실데이터 화면은 아직 검증하지 않았다.
