# PACKAGE STATUS

- 패키지명: daily_career_codex_implementation_20260919_full
- 목적: Codex 구현 착수용 최종 설계 + 실제 핵심 시안/캐릭터 이미지 포함 패키지
- 기준일: 2026-09-19

## 포함 범위
- 설계 문서: 기능, 제작 스펙, UI/UX, DB/ERD, API, Backend, Frontend, Notion, Slack/WebPush, 통합 기준
- Codex 구현 지시문: 구현 시작 프롬프트, 단계별 구현 순서, 수용 기준 체크리스트
- 실제 이미지 자산:
  - 핵심 시안 10종 PNG
  - 추가 참고 시안 3종 PNG
  - 공식 캐릭터 마스터 시트 PNG
  - 이미지 매핑 문서

## 이미지 위치
- 핵심 시안: `02_assets/images/ui_mockups/core/`
- 추가 시안: `02_assets/images/ui_mockups/extra/`
- 캐릭터: `02_assets/images/character/`

## 구현 우선 원칙
1. `openapi.yaml` 원본이 저장소에 있으면 그것을 canonical source로 사용
2. 이 패키지 문서는 구현 기준선 및 누락 시 참조용
3. UI는 핵심 시안 10종과 설계 문서를 함께 참고
