"""Create the next immutable release of optional cross-subject lessons.

The manuscript is intentionally readable here; the SQL file is generated output.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path


def question(prompt: str, choices: list[str], answer: int, explanation: str) -> dict:
    return dict(prompt=prompt, choices=choices, answer=answer, explanation=explanation)


def lesson(slug: str, subject: str, subject_name: str, title: str, objective: str,
           key_points: list[str], sources: list[str], body: str, questions: list[dict]) -> dict:
    return dict(slug=slug, subject=subject, subjectName=subject_name, title=title,
                objective=objective, keyPoints=key_points,
                sources=[dict(title=url.split('/')[2], url=url) for url in sources],
                body=body.strip(), questions=questions)


LESSONS = [
    lesson(
        "spring-rollback-rules", "SPRING", "Spring Boot", "예외와 트랜잭션 롤백 검증",
        "승인 처리의 실패 조건과 실제 롤백 결과를 구분해 검증한다.",
        ["실패를 삼키면 커밋될 수 있다.", "예외 종류와 rollback 규칙을 확인한다.", "실제 DB 상태로 결과를 검증한다."],
        ["https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html"],
        """# 실패했는데 승인 상태가 바뀌어 있다면

승인 서비스가 상태를 변경한 다음 이력 저장에 실패했다. 화면에는 오류가 나타났지만 다시 조회하면 상태는 승인으로 남아 있다. 이 현상을 ‘트랜잭션이 안 된다’고 한마디로 결론 내리기 전에 실제 호출 경로와 예외 흐름을 그린다. Controller에서 서비스를 호출하는지, 같은 객체 내부 호출인지, 두 저장 작업이 같은 트랜잭션에 참여하는지 확인한다. 서비스 안에서 예외를 잡아 로그만 남기고 정상 반환하면 선언된 롤백 규칙이 작동할 기회가 없을 수 있다.

## 실패 계약을 먼저 적기

승인 상태와 이력이 함께 성공해야 한다면 두 DB 변경은 한 작업의 성공 조건이다. 이력 저장의 실패를 업무상 정상 결과로 간주할지, 전체 승인을 실패로 되돌릴지 결정한다. Spring의 기본 선언적 트랜잭션은 모든 예외에 똑같이 반응하지 않는다. 체크 예외를 사용하거나 예외를 변환하는 코드가 있으면 적용 중인 rollback 규칙을 명시적으로 확인한다. `rollbackFor`를 무조건 넓히기보다 어떤 실패가 데이터 변경을 취소해야 하는지 먼저 정한다.

## 재현 실습

테스트 DB에 승인 대기 건을 넣고 이력 저장만 실패하게 만든다. API 오류 코드뿐 아니라 승인 행과 이력 행을 다시 조회해 둘 다 이전 상태인지 검증한다. 예외를 삼키는 구현, 서비스 내부 호출, 서비스 경유 호출을 나눠 결과를 비교한다. 동시에 같은 승인을 처리하는 요청 두 개도 준비해 조건부 변경의 영향 행 수를 확인한다. 원격 알림 실패는 DB 저장 실패와 다른 경계이므로 이 테스트의 롤백 기대값에 섞지 않는다. 리뷰에는 재현 입력, 호출 경로, 변경 전후 행, 예외 종류를 함께 기록한다.""",
        [
            question("이력 저장 예외를 서비스가 잡아 로그만 남긴 뒤 정상 반환했다. 승인 상태가 커밋된 이유로 가장 먼저 확인할 것은?", ["예외가 트랜잭션 경계 밖으로 전달되지 않았는지", "Controller의 응답 문구 길이", "테이블 이름의 알파벳 순서", "로그 파일 크기"], 1, "실패가 삼켜져 정상 종료되면 롤백 판단이 달라진다. 예외 흐름과 실제 저장 상태를 함께 본다."),
            question("체크 예외에서만 승인 행이 남는다. 다음 검증으로 가장 적절한 것은?", ["모든 예외를 무시한다", "해당 예외의 롤백 정책과 트랜잭션 경로를 확인하고 DB 결과를 검증한다", "화면에서 승인 행을 숨긴다", "이력 테이블을 삭제한다"], 2, "선언된 롤백 규칙과 호출 경로를 확인해야 기대한 저장 경계를 보장할 수 있다."),
            question("롤백 테스트가 HTTP 500만 확인한다. 놓친 핵심 검증은?", ["화면의 버튼 색", "승인 상태와 이력이 함께 이전 상태인지 DB에서 조회", "응답 JSON의 들여쓰기", "로그 줄 수"], 2, "응답 실패와 실제 데이터 롤백은 별개 결과다."),
        ],
    ),
    lesson(
        "spring-input-contract", "SPRING", "Spring Boot", "입력 검증과 API 회귀 테스트",
        "입력 형식 오류와 업무 규칙 위반을 구분하고 변경 계약을 테스트한다.",
        ["형식과 업무 규칙을 구분한다.", "오류 응답도 API 계약이다.", "경계값과 기존 호출자를 테스트한다."],
        ["https://docs.spring.io/spring-framework/reference/testing/mockmvc.html", "https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html"],
        """# 필수 입력이 하나 늘어난 API

배치 요청 API에 처리 기준일을 추가한다. 새 화면은 값을 보내지만 기존 호출자는 아직 보내지 않는다. 요청 DTO에 필드를 추가하고 필수 검증만 걸면 누가 어떤 오류를 받을지 모른 채 배포하기 쉽다. 먼저 계약 표에 이전 요청, 새 요청, 날짜 형식 오류, 미래 날짜, 허용된 재처리를 적는다. 날짜 형식은 입력 검증의 문제이고, 처리 가능한 기간인지는 업무 규칙의 문제다. 둘을 같은 메시지로 돌려주면 호출자가 수정할 방법을 찾기 어렵다.

## 경계와 오류 응답

Controller에서 요청 본문을 읽고 필수 값과 형식을 검증한다. Service에서는 기준일이 처리 상태와 맞는지 확인한다. 자동 변환이 실패할 때 서버 내부 오류처럼 보이지 않도록 상태 코드와 오류 코드를 기존 API 계약에 맞춘다. 기존 클라이언트가 값을 생략할 수 있어야 한다면 기본값의 의미와 적용 기간을 결정한다. `null`을 오늘 날짜로 간주하는 변경은 재시도 때 날짜가 달라질 수 있으므로 무심코 넣지 않는다.

## 회귀 실습

MockMvc로 이전 본문과 새 본문을 각각 보내 응답 상태와 오류 코드를 확인한다. 형식이 맞아도 업무상 거부되는 날짜를 따로 넣는다. 서비스 테스트에서는 이미 처리된 건, 경계 날짜, 중복 요청을 검증한다. 가능하면 실제 HTTP와 DB를 묶은 통합 테스트 한 개로 요청 변환부터 저장 결과까지 확인한다. MockMvc는 MVC 요청 처리를 검증하지만 실제 네트워크와 배포 환경까지 검증하는 것은 아니다. 리뷰에는 지원할 이전 요청의 범위와 제거 시점을 명시한다.""",
        [
            question("새 필수 날짜를 추가했는데 이전 호출자가 생략한다. 가장 먼저 정할 것은?", ["오류 메시지 색상", "기존 요청을 계속 허용할지와 기본값의 업무 의미", "DTO 클래스 이름", "테스트 파일 개수"], 2, "호환 정책과 기본값 의미를 정해야 이전 호출자의 결과를 예측할 수 있다."),
            question("날짜 형식은 맞지만 마감된 배치를 다시 처리하려 한다. 어느 경계에서 확인할 규칙인가?", ["JSON 구문 분석만", "업무 상태와 처리 가능 기간을 확인하는 서비스", "CSS", "로그 회전 설정"], 2, "입력 형식과 처리 가능 여부는 다른 검증이다."),
            question("MockMvc 테스트만 통과했다. 아직 별도 확인이 필요한 것은?", ["실제 배포 경로와 DB 변경 결과", "문서의 글꼴", "IDE 테마", "Java 파일명 길이"], 1, "MockMvc는 MVC 경로를 검증하지만 배포 환경과 실제 네트워크 결과까지 보장하지 않는다."),
        ],
    ),
    lesson(
        "sql-latest-row", "SQL", "SQL", "최신 이력 한 건을 고르는 조회",
        "1:N 이력에서 동률까지 고려해 대표 행을 결정한다.",
        ["대표 행의 업무 기준을 먼저 정한다.", "동률 순서를 명시한다.", "목록과 건수 쿼리를 함께 검증한다."],
        ["https://www.postgresql.org/docs/17/tutorial-window.html"],
        """# 가장 최근 상태가 두 개라면

고객 목록에서 최근 상태 이력 하나를 보여주려 한다. 단순 JOIN은 이력 수만큼 고객 행을 복제한다. `MAX(changed_at)`을 구해 다시 JOIN해도 같은 시각의 이력이 두 개면 두 행이 돌아온다. 먼저 ‘최근’의 정의를 업무 담당자와 정한다. 기록 시각인지 적용 시각인지, 동률이면 이력 ID가 큰 행인지, 취소된 이력은 제외하는지 정해야 쿼리가 결정된다. 페이지 목록의 기준 행이 고객 한 건이라는 사실도 고정한다.

## 대표 행을 고르기

PostgreSQL의 `row_number()`를 고객별로 나누고 적용 시각 내림차순, 이력 ID 내림차순으로 정렬할 수 있다. `rn=1`인 행만 연결하면 한 고객에 한 이력을 고른다. 다만 두 번째 정렬 열이 업무상 동률 해소 규칙과 일치해야 한다. 최신 상태가 없는 고객도 목록에 남겨야 한다면 결과를 고객에 `LEFT JOIN`한다. 이력의 유효 여부 조건을 대표 행을 고르기 전에 적용할지 후에 적용할지에 따라 의미가 달라진다.

## 검증 실습

이력이 없는 고객, 하나인 고객, 세 개인 고객, 시각이 같은 두 이력을 가진 고객을 만든다. 예상 대표 이력을 표로 먼저 적고 쿼리 결과와 비교한다. 목록 행 수, 전체 건수, 페이지 경계가 같은 고객 집합을 세는지 확인한다. 인덱스는 필터와 정렬 조건, 실제 데이터 크기를 보고 결정한다. 실행 계획이 좋다는 이유로 대표 행 규칙 검증을 생략하지 않는다. 같은 데이터에서 반복 실행 결과가 안정적인지도 확인한다.""",
        [
            question("MAX(changed_at)로 최신 이력을 JOIN했더니 고객 한 명이 두 줄이다. 원인은?", ["시간 동률인 이력이 둘 이상 있다", "LEFT JOIN이 항상 느리다", "고객 ID가 문자열이다", "페이지 크기가 작다"], 1, "최댓값만으로 동률을 해소하지 못한다. 대표 행의 추가 정렬 기준이 필요하다."),
            question("row_number()로 대표 행을 고를 때 가장 먼저 확정해야 하는 것은?", ["색상", "업무상 최신의 기준과 동률 규칙", "SELECT 별칭 길이", "DB 포트"], 2, "정렬 기준은 업무 의미에서 나온다."),
            question("이력 없는 고객도 목록에 남겨야 한다. 대표 이력 결과와 고객의 연결 방식은?", ["대표 이력에만 INNER JOIN", "고객에서 대표 이력으로 LEFT JOIN", "고객 행 삭제", "이력 없는 고객을 임의 생성"], 2, "기준 고객 행을 보존하려면 바깥 연결이 필요하다."),
        ],
    ),
    lesson(
        "sql-null-unique", "SQL", "SQL", "NULL과 유일 제약의 경계",
        "선택 입력을 포함한 중복 규칙을 DB 제약으로 표현한다.",
        ["NULL의 업무 의미를 정한다.", "기본 UNIQUE의 NULL 취급을 확인한다.", "동시 요청을 저장 제약으로 막는다."],
        ["https://www.postgresql.org/docs/17/ddl-constraints.html"],
        """# 화면에서는 중복인데 DB에는 두 건

업무 코드와 외부 참조 번호로 중복을 막는 테이블이 있다. 참조 번호는 선택 입력이다. 같은 코드에 참조 번호가 없는 요청 두 건이 모두 저장되자, 개발자는 `UNIQUE(code, reference_no)`가 있는데 왜 그러냐고 묻는다. PostgreSQL의 기본 UNIQUE는 NULL을 서로 다른 값으로 다룰 수 있다. 먼저 참조 번호가 없다는 것이 ‘아직 모름’인지 ‘동일한 기본 대상’인지 정해야 한다. 이 해석에 따라 중복 규칙이 달라진다.

## 제약 선택

없는 참조 번호도 같은 코드에서 한 건만 허용한다면 PostgreSQL의 `NULLS NOT DISTINCT` 옵션이나 요구에 맞는 별도 제약을 검토한다. 참조 번호가 없는 여러 건이 정상이라면 현재 제약이 맞을 수도 있다. 문자열의 공백, 대소문자, 빈 문자열을 같은 것으로 볼지도 결정한다. 애플리케이션의 선조회만으로 중복을 막으면 두 요청이 동시에 ‘없음’을 읽은 뒤 모두 저장을 시도할 수 있다. 최종 저장 경계의 제약과 충돌 응답 정책이 필요하다.

## 검증 실습

같은 코드와 NULL을 두 번 넣고, 같은 코드에 서로 다른 참조 번호를 넣고, 서로 다른 코드에 NULL을 넣어 기대 결과를 표로 만든다. 동시 요청 테스트에서는 저장 성공 건수와 반환 오류를 함께 확인한다. 기존 데이터에 이미 중복이 있다면 제약 추가는 실패할 수 있으므로 중복 목록과 정리 방침을 먼저 검토한다. 제약을 바꾸면서 기존 데이터를 임의 삭제하지 않는다. 화면 검증 문구와 DB 규칙이 같은 업무 의미를 표현하는지 확인한다.""",
        [
            question("UNIQUE(code, reference_no)가 있는데 reference_no가 NULL인 같은 코드 행이 두 개 저장됐다. 우선 확인할 것은?", ["NULL의 중복 의미와 DB 제약의 NULL 처리", "컬럼 표시 너비", "서버 시간대", "프런트 버튼 순서"], 1, "기본 UNIQUE의 NULL 처리와 업무 중복 규칙을 맞춰야 한다."),
            question("동시 요청 두 개가 모두 선조회에서 중복이 없다고 봤다. 최종 방어선은?", ["조회 사이에 sleep", "저장소 유일 제약과 충돌 응답 처리", "버튼 비활성화만", "로그 삭제"], 2, "선조회는 경쟁 조건을 막지 못한다."),
            question("제약을 추가하기 전 기존 중복 행을 찾았다. 적절한 순서는?", ["바로 임의 삭제", "업무상 대표 행과 정리 방침을 확인한 뒤 마이그레이션", "중복을 숨기는 SELECT만 추가", "제약을 포기"], 2, "기존 데이터 정리는 업무 규칙에 따라 검토해야 한다."),
        ],
    ),
    lesson(
        "algorithm-bfs-workflow", "ALGORITHM", "알고리즘", "의존 작업을 탐색하는 큐",
        "업무 단계의 연결을 너비 우선으로 탐색하고 방문 상태를 검증한다.",
        ["단계와 의존 관계를 그래프로 표현한다.", "방문 표시는 중복 확장을 막는다.", "순환과 미연결 데이터를 테스트한다."],
        ["https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayDeque.html"],
        """# 영향받는 후속 작업을 찾기

배치 단계 A가 실패했을 때 A에 의존하는 모든 후속 단계를 찾아 재처리 후보로 표시한다. 단계는 정점이고 ‘B가 A의 결과를 사용한다’는 관계는 A에서 B로 향하는 간선이다. 먼저 의존 방향을 확인한다. 반대로 저장하면 선행 단계 목록만 나오므로, 코드가 돌아가더라도 업무 결과는 틀린다. A와 직접 연결된 단계뿐 아니라 그 뒤 단계까지 탐색해야 한다.

## 큐와 방문 상태

시작 단계를 큐에 넣고, 하나를 꺼낼 때마다 아직 방문하지 않은 후속 단계를 넣는다. Java에서는 `ArrayDeque`를 큐로 사용할 수 있다. 방문 표시는 큐에 넣을 때 한다. 꺼낼 때 표시하면 여러 부모가 같은 단계를 가리킬 때 같은 작업이 큐에 여러 번 들어갈 수 있다. 단계의 표시 순서가 중요하면 인접 목록의 정렬 기준을 정한다. 탐색 결과가 곧 실행 순서라는 보장은 없으므로 재처리 실행에는 의존성·상태·멱등성 규칙을 별도로 둔다.

## 실습과 검증

A→B, A→C, B→D, C→D의 작은 그래프를 그려 D가 한 번만 결과에 들어가는지 확인한다. 관계 데이터가 잘못되어 D→A 순환이 있어도 무한 반복하지 않아야 한다. 없는 단계 ID, 후속 단계가 없는 경우, 서로 연결되지 않은 단계도 테스트한다. 메모리에 전체 그래프를 올리기 어려운 규모라면 조회를 어떻게 나눌지 검토하고 같은 단계에 대한 반복 DB 조회 수를 측정한다. 이 탐색은 후보 식별 알고리즘이며 실제 재처리 승인 정책을 대신하지 않는다.""",
        [
            question("A→B, A→C, B→D, C→D에서 D가 큐에 두 번 들어간다. 방문 표시 시점은?", ["큐에 넣을 때", "모든 탐색이 끝난 뒤", "화면을 그릴 때", "DB 백업 뒤"], 1, "삽입 시 방문 처리하면 여러 부모에서 같은 정점이 반복 삽입되지 않는다."),
            question("관계 데이터에 D→A가 추가됐다. 반드시 필요한 것은?", ["탐색을 무한 반복", "방문 집합으로 순환을 종료", "A 삭제", "큐를 정렬하지 않기"], 2, "방문 집합이 있으면 순환 관계에서도 각 단계를 제한된 횟수로 처리한다."),
            question("영향 후보 목록을 찾았다. 곧바로 실행 순서로 써도 되는가?", ["항상 가능", "의존성과 재처리 상태를 별도로 검증해야 한다", "역순이면 언제나 가능", "정렬만 하면 충분"], 2, "탐색 결과와 안전한 업무 재처리 순서는 다른 계약이다."),
        ],
    ),
    lesson(
        "algorithm-priority-deadline", "ALGORITHM", "알고리즘", "마감 작업의 우선순위 큐",
        "마감 시각과 안정적인 동률 규칙으로 처리 후보를 고른다.",
        ["비교 기준을 업무 규칙으로 정한다.", "동률에 안정적인 두 번째 키를 둔다.", "큐 안의 값 변경은 재삽입 정책을 검토한다."],
        ["https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/PriorityQueue.html"],
        """# 가장 급한 작업부터 처리하기

수백 개의 대기 작업 중 마감 시각이 가장 이른 작업을 반복해서 선택한다. 전체 목록을 매번 정렬하거나 매번 선형 검색해도 작은 양에서는 충분할 수 있다. 양이 커지고 삽입과 꺼내기가 반복된다면 우선순위 큐를 검토한다. 다만 ‘급하다’는 말만으로 비교 함수를 작성할 수 없다. 마감 시각이 같으면 생성 순서인지 업무 등급인지 정하고, 취소된 작업은 언제 제외하는지도 정한다.

## 비교와 갱신

Java `PriorityQueue`의 head는 정의한 비교 순서에서 가장 앞선 원소다. 마감 시각과 고유 ID를 차례로 비교하면 동률 결과를 재현하기 쉽다. 큐에 들어 있는 작업 객체의 마감 시각을 외부에서 바꾸고 곧바로 올바른 head가 나온다고 가정하지 않는다. 변경이 잦다면 이전 항목을 제거하고 다시 넣거나 버전이 붙은 새 항목을 넣고 꺼낼 때 오래된 항목을 버리는 정책을 정한다. 큐 순회 결과가 우선순위 순서라는 가정도 피한다.

## 실습과 검증

같은 마감 시각의 두 작업, 마감 변경, 취소, 빈 큐, 마감이 지난 작업을 작은 표로 만든다. 연속 삽입과 꺼내기 결과를 수작업 기대값과 비교한다. 재처리 워커가 두 개라면 프로세스 안의 큐만으로 중복 실행을 막을 수 없다. 저장소에서 작업 선점과 상태 전이를 원자적으로 처리해야 한다. 알고리즘 선택의 성능은 실제 예상 건수와 갱신 빈도에서 측정하며, 단순 구조로 충분한 규모라면 과도한 최적화를 피한다.""",
        [
            question("우선순위 큐의 두 작업이 같은 마감 시각이다. 실행 순서를 재현하려면?", ["고유 ID 등 동률 기준 추가", "HashMap 순서 사용", "랜덤 선택", "순회 순서 사용"], 1, "비교 규칙에 동률 해소 키가 있어야 순서가 안정적이다."),
            question("큐 안 객체의 마감 시각을 바꾼 직후 head가 예전 순서다. 적절한 대응은?", ["큐가 자동 재정렬된다고 가정", "제거 후 재삽입하거나 새 버전 항목 정책 사용", "로그만 삭제", "모든 마감을 같게 수정"], 2, "내부 우선순위는 원소의 외부 변경만으로 자동 갱신되지 않는다."),
            question("워커 둘이 같은 작업을 꺼낼 수 있다. 중복 실행 방어는?", ["메모리 큐만 공유", "저장소의 원자적 선점과 상태 전이", "화면 버튼 숨기기", "워커 이름 변경"], 2, "프로세스별 큐만으로 분산 선점을 보장할 수 없다."),
        ],
    ),
    lesson(
        "finance-ledger-reconcile", "FINANCE", "금융 도메인", "거래 원장과 집계 차이 대조",
        "거래 목록과 집계의 기준을 맞춰 누락·중복 원인을 찾는다.",
        ["집계 대상 상태를 명시한다.", "거래 ID로 누락과 중복을 대조한다.", "차이 원인을 보존하고 재처리한다."],
        ["https://www.postgresql.org/docs/17/tutorial-agg.html", "https://www.postgresql.org/docs/17/ddl-constraints.html"],
        """# 같은 날짜인데 합계가 다르다

거래 원장 화면의 금액 합계와 일별 집계 테이블의 합계가 다르다. 한쪽을 임의 수정하기 전에 두 숫자가 같은 집합을 세는지 확인한다. 거래 발생일과 처리 완료일 중 어느 날짜가 기준인지, 취소·보류·실패 거래를 포함하는지, 통화와 금액 단위가 같은지 적는다. ‘오늘 거래’라는 화면 문구도 시간대와 마감 시각이 정해지지 않으면 같은 집합을 가리키지 않는다. 이 학습은 시스템 설계 예시이며 실제 기관의 회계 규칙을 대신하지 않는다.

## 대조 절차

먼저 원장의 거래 ID, 상태, 금액, 반영 날짜를 고정된 시점으로 추출한다. 집계 작업의 입력 ID 또는 처리 기록과 비교해 누락 ID, 중복 ID, 상태 불일치를 나눈다. 금액의 `SUM`만 비교하면 +100과 -100의 두 오류가 서로 상쇄될 수 있다. 건수와 ID 집합, 상태별 합계도 함께 본다. 조인으로 거래가 여러 행으로 늘어나는 경우에는 집계 전 기준 행을 다시 확인한다. 집계 시점 이후 도착한 거래는 오류와 지연 반영을 구분한다.

## 복구와 실습

거래 네 건 중 하나가 중복 처리되고 하나가 누락된 예시를 만든다. 합계가 우연히 같아도 ID 대조가 두 차이를 찾는지 확인한다. 원장을 직접 덮어쓰지 않고 수정 이력, 재계산 범위, 멱등 재처리 키를 정한다. 재계산 중 새 거래가 들어올 수 있으므로 기준 시점이나 스냅샷을 정하고 실행 후 같은 기준으로 다시 대조한다. 보고서에는 확인된 차이와 아직 설명되지 않은 차이를 구분해 남긴다.""",
        [
            question("원장 합계와 집계 합계가 같다. 반드시 일치한다고 결론 낼 수 없는 이유는?", ["누락과 중복 금액이 상쇄될 수 있다", "SUM은 항상 문자열이다", "ID는 비교할 수 없다", "건수는 항상 같다"], 1, "합계 외에 ID 집합과 건수, 상태별 결과를 대조해야 한다."),
            question("거래 발생일 기준 화면과 처리 완료일 기준 집계를 비교했다. 첫 수정은?", ["집계 값을 임의 수정", "기준 날짜와 상태 집합을 맞춰 차이를 재계산", "이력을 삭제", "화면만 숨김"], 2, "서로 다른 모집단의 합계는 직접 비교할 수 없다."),
            question("차이 복구에서 원장 행을 바로 덮어쓰려 한다. 적절한 대응은?", ["수정 이력과 재처리 범위·기준 시점을 먼저 정의", "오류 로그 삭제", "집계만 임의 반올림", "중복 ID 무시"], 1, "복구 과정도 추적 가능하고 재현 가능해야 한다."),
        ],
    ),
    lesson(
        "finance-cutoff-retry", "FINANCE", "금융 도메인", "마감 시점과 재처리 경계",
        "마감 전후 도착한 요청을 분류하고 같은 요청의 재처리를 검증한다.",
        ["접수·처리·반영 시각을 구분한다.", "마감 기준 시간대와 예외를 정한다.", "재처리 때 중복 반영을 막는다."],
        ["https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/Instant.html", "https://www.postgresql.org/docs/17/transaction-iso.html"],
        """# 마감 1분 전에 접수된 요청

마감 시각 직전에 들어온 요청이 외부 응답 지연으로 마감 뒤 저장됐다. 어느 날짜의 처리량에 넣을지 단순히 서버의 현재 날짜로 결정하면 재시도할 때 다른 결과가 나올 수 있다. 접수 시각, 검증 완료 시각, 실제 반영 시각을 각각 기록하고 어느 시각이 업무 날짜를 결정하는지 계약으로 정한다. 시간대와 마감 예외일도 명시한다. 이 예시는 설계 연습이며 실제 결제·회계 제도의 기준을 제시하지 않는다.

## 상태와 재시도

요청 ID를 논리 요청의 키로 삼는다면 같은 ID와 같은 본문에는 저장된 결과를 돌려줄지, 처리 중 상태를 돌려줄지 정한다. 같은 ID에 다른 본문은 충돌로 다뤄야 할 수 있다. 응답이 끊겼다고 새 ID를 만들어 다시 보내면 이중 반영이 가능하므로 호출자와 서버의 재시도 계약을 함께 검토한다. 마감 작업이 입력을 읽는 시점과 새 요청이 저장되는 시점이 겹친다면 스냅샷 또는 컷오프 상태를 명확히 한다. DB 격리 수준만 올리면 모든 업무 경계가 자동 정의되는 것은 아니다.

## 실습과 검증

마감 직전 접수·직후 반영, 직후 접수, 동일 요청 재전송, 같은 ID의 다른 본문, 외부 성공 후 로컬 저장 실패를 나눠 기대 상태를 적는다. 각 시나리오에서 거래 행, 처리 이력, 집계 대상 날짜를 확인한다. 경계 시각은 문자열 비교가 아니라 시간대가 정해진 시각 값으로 비교한다. 재처리 후에도 같은 논리 요청이 한 번만 반영되는지 저장 제약과 결과 조회로 검증한다.""",
        [
            question("마감 전 접수됐으나 마감 뒤 저장됐다. 업무 날짜를 정하려면?", ["서버 현재 날짜만 사용", "어떤 시각이 기준인지와 시간대를 계약으로 확정", "항상 전날로 이동", "화면에서 숨김"], 2, "접수와 반영 시각의 의미를 분리해야 경계 결과가 재현된다."),
            question("응답 시간 초과 후 호출자가 새 요청 ID로 다시 보냈다. 위험은?", ["같은 논리 요청이 두 번 반영될 수 있다", "날짜 형식이 바뀐다", "DB 포트가 바뀐다", "로그 색상이 달라진다"], 1, "재시도 키가 달라지면 서버가 같은 요청으로 식별하지 못할 수 있다."),
            question("DB 격리 수준만 높이면 마감 기준이 정해지는가?", ["그렇다", "업무상 컷오프 시점과 상태 정책을 별도로 정의해야 한다", "요청 ID가 사라진다", "모든 재처리가 자동 성공한다"], 2, "격리는 동시성 관찰 규칙이고 업무 날짜·상태 계약은 별도로 정해야 한다."),
        ],
    ),
    lesson(
        "interview-incident-evidence", "INTERVIEW", "기술 면접", "장애 대응을 증거 중심으로 설명하기",
        "증상, 원인 가설, 검증, 복구를 자신의 역할에 맞게 설명한다.",
        ["관찰과 추정을 구분한다.", "재현 데이터와 로그를 제시한다.", "개인 기여와 팀 결정을 나눈다."],
        ["https://www.postgresql.org/docs/17/using-explain.html"],
        """# ‘장애를 해결했다’ 다음에 나올 질문

면접에서 가장 어려웠던 장애를 묻는다. ‘쿼리를 튜닝해서 해결했다’만 말하면 어떤 증상이었고 왜 그 변경이 효과가 있었는지 설명하기 어렵다. 먼저 사용자에게 보인 현상, 발생 범위, 최초 관찰 시각을 적는다. 그다음 확인한 로그와 데이터, 가능한 원인, 실제 원인, 완화 조치와 영구 수정을 구분한다. 당시 확인하지 않은 사실을 지금 추측해서 성과로 말하지 않는다.

## 증거를 연결하기

예를 들어 목록의 중복 행 문제라면 요청 ID, 중복된 업무 ID, JOIN 전후 행 수, 작은 재현 데이터를 제시한다. `EXPLAIN`에서 본 실행 계획은 성능 판단의 근거일 수 있지만, 중복 행의 업무 원인을 대신 설명하지 않는다. 성능 수치를 주장한다면 전후 입력량과 측정 환경을 같이 말한다. 즉시 완화가 화면 필터였고 근본 수정이 대표 이력 규칙의 변경이었다면 두 조치의 역할을 구분한다.

## 2분 답변 실습

첫 20초에는 사용자 영향과 자신의 담당 범위를 말한다. 다음에는 확인한 증거와 버린 가설 한 가지, 선택한 수정, 회귀 테스트를 말한다. 마지막에는 실제 확인한 결과와 아직 관찰 기간이 부족한 부분을 말한다. 팀이 결정한 운영 조치를 개인 단독 성과로 바꾸지 않는다. 본인의 기록에 없는 처리량, 개선율, 장애 시간을 만들어 넣지 않는다. 예상 꼬리 질문은 ‘왜 다른 원인이 아니라고 봤나’, ‘롤백하면 어떤 데이터가 남나’, ‘같은 문제가 다시 생기면 어떤 지표로 알 수 있나’로 준비한다.""",
        [
            question("면접 답변에 성능 개선율을 넣고 싶지만 전후 측정값이 없다. 가장 좋은 표현은?", ["추정 수치를 단정", "수정과 검증 결과를 설명하되 개선율은 측정하지 않았다고 말함", "팀 수치를 내 단독 성과로 사용", "기술 이름만 나열"], 2, "관찰된 결과와 측정하지 않은 효과를 분리해야 한다."),
            question("JOIN 중복 장애의 원인을 설명할 때 필요한 증거는?", ["기준 행과 관계 수, 재현 데이터, 수정 전후 결과", "IDE 테마", "서버 이름만", "DISTINCT를 썼다는 말만"], 1, "원인과 수정 효과를 연결하는 재현 증거가 중요하다."),
            question("팀이 긴급 완화를 결정했고 내가 근본 수정을 구현했다. 답변은?", ["모두 혼자 했다고 말함", "팀 결정과 본인의 구현·검증 범위를 구분", "완화를 숨김", "근본 수정만 주장하고 증거 생략"], 2, "역할과 결과를 분리해야 정확한 경험 설명이 된다."),
        ],
    ),
    lesson(
        "interview-test-boundaries", "INTERVIEW", "기술 면접", "테스트 범위와 한계를 설명하기",
        "단위·통합·운영 검증의 근거와 남은 위험을 명확히 말한다.",
        ["테스트가 통과한 경계를 명시한다.", "실패 사례와 회귀 조건을 선택한다.", "검증하지 않은 배포 결과를 단정하지 않는다."],
        ["https://docs.spring.io/spring-framework/reference/testing/mockmvc.html", "https://www.postgresql.org/docs/17/transaction-iso.html"],
        """# ‘테스트를 했다’는 말의 범위

면접관이 변경 기능을 어떻게 검증했는지 묻는다. 단위 테스트 몇 개가 통과했다는 사실만으로 실제 DB 제약이나 배포된 화면의 동작을 말할 수는 없다. 먼저 가장 위험한 실패 경로를 고른다. 예를 들어 승인 취소 변경이라면 정상 취소, 이미 취소됨, 동시 요청, 이력 저장 실패, 외부 알림 실패가 서로 다른 결과를 요구한다. 모든 조합을 무작정 나열하기보다 업무 불변식이 깨지는 조건을 우선한다.

## 검증 경계 구분

순수 함수의 규칙은 단위 테스트로 빠르게 검증한다. Controller의 요청 변환과 오류 응답은 MockMvc로 확인할 수 있다. 저장 제약, 롤백, 동시성은 실제 사용하는 DB에 가까운 통합 테스트가 필요하다. 사용자 경로는 실행 중인 화면에서 로그인, 요청, 결과 재조회까지 확인한다. 각 단계의 통과 사실은 그 단계에서 관찰한 것만 뒷받침한다. 로컬 브라우저에서 성공했어도 외부 공급자 전송이나 운영 배포가 검증된 것은 아니다.

## 답변 실습

변경 전 실패를 재현한 테스트 한 개와 수정 뒤 통과한 테스트 한 개를 골라 입력, 예상, 실제 결과를 30초 안에 말해본다. 이어서 수행하지 못한 테스트와 그 이유, 남은 위험을 말한다. 예를 들어 ‘동시 승인 요청은 테스트 DB에서 확인했지만 외부 알림 공급자의 운영 재시도는 확인하지 않았다’라고 구분한다. 검증 결과를 과장하지 않으면 후속 질문에서도 어떤 증거를 더 모을지 설명할 수 있다.""",
        [
            question("MockMvc 테스트가 통과했다. DB 롤백도 검증됐다고 말할 수 있는가?", ["항상 가능", "실제 저장 결과를 확인하는 통합 테스트가 별도로 필요할 수 있다", "화면만 보면 충분", "로그가 있으면 충분"], 2, "MVC 요청 처리와 저장 경계는 다른 검증 대상이다."),
            question("수많은 테스트 중 먼저 고를 사례는?", ["업무 불변식이 깨질 위험이 큰 실패 경로", "가장 짧은 테스트 이름", "성공만 반복", "화면 색상"], 1, "위험한 경계 조건을 우선하면 테스트의 목적을 설명하기 쉽다."),
            question("로컬 화면과 테스트 DB는 확인했지만 외부 알림 전송은 확인하지 않았다. 보고는?", ["전체 운영 성공이라고 말함", "확인한 범위와 외부 전송 미검증을 나눠 설명", "테스트 결과 생략", "공급자 성공을 추측"], 2, "증거가 있는 범위만 성공으로 말해야 한다."),
        ],
    ),
]


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    output = root / "backend/src/main/resources/db/migration/V28__extra_learning_more_subject_topics.sql"
    subjects = {"SPRING", "SQL", "ALGORITHM", "FINANCE", "INTERVIEW"}
    assert {item["subject"] for item in LESSONS} == subjects
    assert all(sum(item["subject"] == subject for item in LESSONS) >= 2 for subject in subjects)
    assert len({item["slug"] for item in LESSONS}) == len(LESSONS)
    for item in LESSONS:
        assert len(item["body"]) >= 650, item["slug"]
        assert len(item["questions"]) >= 3, item["slug"]
        for entry in item["questions"]:
            assert len(entry["choices"]) == 4 and len(set(entry["choices"])) == 4
            assert 1 <= entry["answer"] <= 4
        item["digest"] = hashlib.sha256(item["body"].encode("utf-8")).hexdigest()
    payload = json.dumps(LESSONS, ensure_ascii=False, separators=(",", ":")).replace("'", "''")
    sql = """-- Additional authored optional lessons. Source: content/extra-learning-v2/build.py.
-- Existing published catalog entries and learner records are unchanged.
DO $extra_catalog_more$
DECLARE
    lesson jsonb;
    question jsonb;
    subject_id bigint;
    content_id bigint;
    content_version_id bigint;
    problem_id bigint;
    problem_version_id bigint;
    question_no integer;
    lesson_no integer := 5;
BEGIN
    FOR lesson IN SELECT value FROM jsonb_array_elements('""" + payload + """'::jsonb) LOOP
        lesson_no := lesson_no + 1;
        SELECT id INTO STRICT subject_id FROM daily_career.subject WHERE code=lesson->>'subject';
        INSERT INTO daily_career.learning_content(subject_id,title)
            VALUES (subject_id,lesson->>'title') RETURNING id INTO content_id;
        INSERT INTO daily_career.learning_content_version
            (learning_content_id,version_no,status,body_markdown,source_references,content_digest)
            VALUES (content_id,1,'DRAFT',lesson->>'body',lesson->'sources',lesson->>'digest')
            RETURNING id INTO content_version_id;
        INSERT INTO daily_career.learning_content_detail
            (content_version_id,title,learning_objective,key_points,interview_points)
            VALUES (content_version_id,lesson->>'title',lesson->>'objective',lesson->'keyPoints','[]'::jsonb);
        question_no := 0;
        FOR question IN SELECT value FROM jsonb_array_elements(lesson->'questions') LOOP
            question_no := question_no + 1;
            INSERT INTO daily_career.problem(subject_id,title)
                VALUES (subject_id,(lesson->>'title') || ' · 확인 ' || question_no)
                RETURNING id INTO problem_id;
            INSERT INTO daily_career.problem_version
                (problem_id,version_no,status,question_type,difficulty,prompt_markdown,choices,
                 answer_key,rubric,explanation_markdown,grading_rule_version,published_at)
                SELECT problem_id,1,'PUBLISHED','SINGLE_CHOICE',4,question->>'prompt',
                    (SELECT jsonb_agg(jsonb_build_object('id',ordinality::text,'text',value) ORDER BY ordinality)
                     FROM jsonb_array_elements_text(question->'choices') WITH ORDINALITY),
                    jsonb_build_object('correct',(question->>'answer')),'{}'::jsonb,
                    question->>'explanation','rule-choice-v1',clock_timestamp()
                RETURNING id INTO problem_version_id;
            INSERT INTO daily_career.learning_content_problem(content_version_id,problem_version_id,sequence)
                VALUES (content_version_id,problem_version_id,question_no);
        END LOOP;
        UPDATE daily_career.learning_content_version
            SET status='PUBLISHED',published_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
            WHERE id=content_version_id;
        INSERT INTO daily_career.extra_learning_catalog(slug,content_version_id,display_order)
            VALUES (lesson->>'slug',content_version_id,lesson_no);
    END LOOP;
END
$extra_catalog_more$;
"""
    output.write_text(sql, encoding="utf-8")
    print(f"Wrote {output.relative_to(root)}: {len(LESSONS)} lessons, {sum(len(x['questions']) for x in LESSONS)} questions")


if __name__ == "__main__":
    main()
