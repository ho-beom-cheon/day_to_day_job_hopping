# 이미지 자산 매니페스트

## 핵심 UI 시안 10종 — 완료 기준

아래 10개 시안은 디자인/UI/UX 방에서 완료 처리된 최종 구현 참조 대상이다.

### 01 오늘 홈 / 대시보드 PC 승인 시안

2026-09-21 사용자 확인으로 다음 두 장을 하나의 메인페이지 시안 세트로 등록한다.

- `ui_mockups/core/01a_today_home_dashboard_pc_top.png`: 실제 PC 폭, 고정 사이드바·헤더, 상단 콘텐츠와 좌우 여백 기준
- `ui_mockups/core/01b_today_home_dashboard_pc_scrolled.png`: 같은 폭에서 스크롤한 최근 활동·빠른 메뉴·커리어 목표·성장 기록 기준

두 이미지는 같은 페이지의 서로 다른 스크롤 위치다. PC 홈 구현 시 두 장을 함께 참조하며,
레이아웃과 콘텐츠 밀도는 이 승인 세트가 기존 `01_today_home_dashboard.png`보다 우선한다.

| No | 의미 | 권장 파일명 | 패키지 바이너리 |
|---:|---|---|---|
| 01 | 오늘 홈 / 대시보드 | `01_dashboard.png` | 현재 런타임에서 회수 불가 |
| 02 | 6개월 커리큘럼 / 로드맵 | `02_curriculum_roadmap.png` | 현재 런타임에서 회수 불가 |
| 03 | 오늘 학습 상세 | `03_daily_learning.png` | 현재 런타임에서 회수 불가 |
| 04 | 문제 풀이 | `04_problem_solving.png` | 현재 런타임에서 회수 불가 |
| 05 | 시험 현황 | `05_test_status.png` | 현재 런타임에서 회수 불가 |
| 06 | 시험 응시 | `06_test_attempt.png` | 현재 런타임에서 회수 불가 |
| 07 | 시험 결과 / 오답 분석 | `07_test_result_wrong_answer.png` | 현재 런타임에서 회수 불가 |
| 08 | 진척도 / 성장 분석 | `08_progress_growth.png` | 현재 런타임에서 회수 불가 |
| 09 | 휴식일 / 일정 변경 | `09_rest_reschedule.png` | 현재 런타임에서 회수 불가 |
| 10 | AI 학습 도우미 / 피드백 | `10_ai_tutor_feedback.png` | 현재 런타임에서 회수 불가 |

## 공식 캐릭터

최종 캐릭터 시트는 파일 라이브러리에서 다음 파일명으로 확인됨.

- `1000045163.png`
- `1000045163(1).png`

특징:
- 검은 풍성한 둥근 헤어
- 둥근 금테 안경
- 검은 상의
- 부드러운 2.5D/3D 렌더
- 큰 엄지척 메인 캐릭터
- 학습/코딩/문제풀이/정답/오답/휴식/레벨업/이직 성공 포즈

현재 패키지 런타임은 과거 대화 파일의 바이너리를 직접 내려받을 수 없어 실제 PNG를 포함하지 못했다.
실제 파일을 프로젝트에 복사할 때 권장 위치:

```text
frontend/public/assets/character/hobeom-character-sheet.png
```

## 구현 규칙

- 실제 UI 시안 이미지가 제공되면 문서보다 픽셀 수준 시각 참조를 우선하되, 기능/상태/API 계약은 문서를 우선한다.
- 시안에 없는 상태는 UI/UX v0.3의 Loading/Empty/Error/Disabled/Completed/Processing/Conflict/Partial Failure 규칙으로 확장한다.
- 캐릭터는 홈, 휴식, 학습 완료, 성공/격려, AI 피드백 중심으로 제한적으로 사용한다.
- 캐릭터를 모든 카드/화면에 반복 배치하지 않는다.
