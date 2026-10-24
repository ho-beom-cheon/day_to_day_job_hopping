# 작업 15 시험 UI 검증 — 2026-09-21

## 결과

- `/tests`, `/tests/attempts/{attemptId}`, `/tests/attempts/{attemptId}/result`를 추가했다.
- 시험 메뉴를 앱 셸에 연결하고, 현황 카드·응시 타이머·큰 답안 선택 영역·채점 상태·결과 해설을 구현했다.
- API에 없는 통계·북마크는 표시하지 않았다.

## 검증

| 항목 | 결과 |
|---|---|
| TypeScript | PASS |
| 전체 ESLint | PASS |
| 전체 Vitest | PASS, 20 files / 74 tests |
| Next production build | PASS, 시험 3개 route 생성 |
| 실행 브라우저 `/tests` | PASS, 인증 사용자 앱 셸·빈 상태·시험 메뉴 활성 |
| 데스크톱 렌더 | PASS, 2560×1989 캡처에서 clipping/overlap 없음 |
| 좁은 화면 | 부분 확인, 390×844 headless 캡처에서 주요 콘텐츠/하단 시험 메뉴 확인 |
| 브라우저 console | 오류 없음 |

운영 시험 콘텐츠가 비어 있어 실제 응시·결과 화면은 실행 브라우저에서 데이터 기반으로 확인하지 못했다. 해당 상태 UI는 계약 decoder와 production build로 확인했으며 fixture 기반 상호작용 테스트를 보강해야 한다.
