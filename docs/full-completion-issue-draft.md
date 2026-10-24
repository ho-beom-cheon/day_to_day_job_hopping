# 이슈 초안: OpenAPI 전 범위 및 외부 연동 완성

## 배경

핵심 학습·문제·시험·진척 흐름과 로컬 운영 기반은 검증했지만 OpenAPI v1.2.1의 91개 operation 중 현재 경로 기준 27개가 누락되어 있다. 알림·Web Push·Notion·Slack은 상태와 일부 설정 UI만 있으며 실제 구독·전달·동기화 worker가 없다. 전 페이지와 전체 사용자 흐름을 100%로 완료하려면 계약 누락, 외부 비동기 처리, 브라우저 권한, 운영 HTTPS를 하나의 상위 목표 아래 순차적으로 마무리해야 한다.

## 범위

- 공개 계약 불일치 수정과 후속 Flyway migration
- 공통코드·통합 대시보드·제출 이력·일/주/월 통계·캘린더·사용자 설정 API
- 앱 알림 목록·읽음·전체 읽음과 outbox dispatcher
- Web Push 구독·암호화·Service Worker·전송 worker
- Slack 테스트 메시지·발송 worker·이력·상태
- Notion OAuth·연결 해제·6종 target·동기화 batch/item·부분 실패 재처리
- 전체 Backend/Frontend/PostgreSQL/브라우저 E2E와 접근성 회귀
- 실제 Google·AI·Notion·Slack·Web Push 및 HTTPS 운영 검증
- migration, secret, backup/restore, 재시작과 OCI 최종 배포 검증

## 제외 및 별도 승인

- 비밀값을 저장소·문서·응답·로그에 기록하지 않는다.
- 사용자 승인 없이 운영 DB 또는 volume을 삭제하지 않는다.
- 자격정보가 없는 외부 서비스의 mock 성공을 실제 성공으로 기록하지 않는다.
- OCI 비용 발생 자원 생성, 공인 도메인·DNS 변경, 인증서 발급은 실행 전에 대상과 권한을 확인한다.

## 완료 조건

- [ ] OpenAPI 91개 operation의 경로 대응 누락이 0개다.
- [ ] 공개 DTO·enum·required/nullable·status·오류 코드가 계약 테스트와 일치한다.
- [ ] 신규·업그레이드 PostgreSQL migration과 기존 데이터 보존을 검증한다.
- [ ] 업무 commit과 outbox가 원자적이며 외부 장애가 업무 성공을 롤백하지 않는다.
- [ ] Push endpoint와 secret은 암호화되고 응답·로그에 노출되지 않는다.
- [ ] Slack timeout/응답 유실과 Notion 부분 성공·결과 불명을 별도 상태로 보존한다.
- [ ] Notion 재연결·대상 변경 뒤 오래된 작업의 외부 호출을 차단한다.
- [ ] 핵심 사용자 흐름과 외부 연동 E2E가 실제 상태 변화를 검증한다.
- [ ] PC·모바일·다크모드·키보드·오류/빈/처리/부분실패 상태를 검증한다.
- [ ] 운영 HTTPS, secure cookie, secret manager, backup/restore, 재시작을 검증한다.
- [ ] 실제 외부 자격정보가 필요한 항목은 실제 공급자 성공 결과를 기록한다.

## 세부 이슈 분할안

1. 계약 정렬 및 내부 누락 API 9개
2. 알림 센터와 outbox dispatcher
3. Web Push 구독·암호화·전송
4. Slack delivery와 이력
5. Notion OAuth·targets·sync worker
6. 전체 E2E·예외·접근성 회귀
7. HTTPS·secret·OCI 운영 인수

## 검증 기준

- Backend 일반 테스트와 실제 PostgreSQL 통합 테스트
- Frontend typecheck, ESLint, Vitest, production build
- Compose config/build/up/health와 runtime smoke
- clean DB와 기존 V8 DB의 roll-forward migration
- 실제 브라우저 데스크톱·모바일·다크모드 E2E
- 외부 provider fake의 성공·실패·timeout·UNKNOWN 계약 테스트
- 자격정보 준비 후 실제 Google·AI·Notion·Slack·Web Push smoke

## 참고

- 설계 분석: `docs/full-completion-batch-analysis-20260923.md`
- 공개 계약: `design-package/supplemental-20260920/openapi.yaml`
- DB 기준선: `backend/src/main/resources/db/migration/V2__business_schema.sql`
- 현재 인계: `진행.md`
