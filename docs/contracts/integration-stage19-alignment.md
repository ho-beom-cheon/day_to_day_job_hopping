# 작업 19 알림·외부 연동 계약 정렬

기준: OpenAPI v1.2.1의 NOTI/PUSH/Notion/Slack 경로와 DB HTML v0.1의 알림·연동 테이블.

## 채택

- 앱 PostgreSQL이 원본이다. 외부 실패는 학습·시험·평가 성공을 롤백하지 않는다.
- 알림 설정은 하나의 aggregate revision과 ETag로 갱신한다. V8에서 `user_setting.notification_settings`, `notification_revision`을 추가한다.
- Web Push 권한과 구독은 별개다. 공개 VAPID 키가 없으면 화면은 사용 불가로 표시한다.
- Notion은 앱에서 외부로만 동기화하며, 자격증명 참조가 없으면 연결됨으로 표시하지 않는다.
- Slack 웹훅 URL은 사용자 입력으로 받지 않는다. 서버 비밀 저장소의 참조가 있을 때만 활성화할 수 있다.
- 연결 상태와 비민감 설정만 API에 노출하며 토큰, 웹훅, endpoint 원문은 응답·로그·문서에 남기지 않는다.

## 구현 확장 상태 (2026-09-23)

초기 설정·진단 범위에 이어 Push 구독 암호화·전송 worker, Slack 실제 전송·이력 재시도, Notion OAuth callback·대상 6종 매핑·batch/item sync worker와 전체 통합 설정 UI까지 구현했다. 실제 공급자 수신은 외부 자격정보와 HTTPS가 필요한 별도 인수 항목이며, 화면은 미설정 상태를 성공으로 가장하지 않고 필요한 조건과 안전한 실패를 표시한다.
