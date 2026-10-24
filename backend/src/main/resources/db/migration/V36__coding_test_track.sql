-- Coding test track, phase 1: link-only recommendations, practice log, retry schedule
-- (docs/competency-daily-loop-design-20261024.md §4.7). Problem text is never stored.

alter table daily_career.user_setting
    add column weekly_coding_target smallint not null default 2,
    add constraint ck_user_setting_15 check (weekly_coding_target between 0 and 7);

create table daily_career.coding_problem_link (
    id bigint generated always as identity,
    owner_user_id bigint references daily_career.app_user(id),
    site varchar(40) not null,
    site_problem_ref varchar(40),
    title varchar(200) not null,
    url varchar(500) not null,
    level smallint,
    category varchar(32) not null,
    link_checked_at date,
    created_at timestamptz not null default now(),
    constraint pk_coding_problem_link primary key (id),
    constraint ck_coding_problem_link_01 check (level is null or level between 0 and 5),
    constraint ck_coding_problem_link_02 check (url ~ '^https://')
);
create unique index uq_coding_problem_link_01 on daily_career.coding_problem_link (site, site_problem_ref) where owner_user_id is null;
create unique index uq_coding_problem_link_02 on daily_career.coding_problem_link (owner_user_id, url) where owner_user_id is not null;
comment on table daily_career.coding_problem_link is '코딩 테스트 추천 목록. 이름·링크·번호·레벨·유형만 저장한다. owner_user_id가 NULL이면 seed';

create table daily_career.coding_problem_hidden (
    user_id bigint not null references daily_career.app_user(id),
    link_id bigint not null references daily_career.coding_problem_link(id),
    hidden_at timestamptz not null default now(),
    constraint pk_coding_problem_hidden primary key (user_id, link_id)
);

create table daily_career.coding_practice_log (
    id bigint generated always as identity,
    user_id bigint not null references daily_career.app_user(id),
    problem_link_id bigint references daily_career.coding_problem_link(id),
    problem_key varchar(520) not null,
    solved_on date not null,
    site varchar(40) not null,
    problem_title varchar(200) not null,
    problem_url varchar(500),
    category varchar(32) not null,
    language varchar(32) not null default '',
    result varchar(16) not null,
    minutes smallint,
    stuck_note text not null default '',
    note text not null default '',
    source varchar(8) not null default 'MANUAL',
    retry_step smallint not null default 0,
    next_retry_at date,
    revision integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint pk_coding_practice_log primary key (id),
    constraint ck_coding_practice_log_01 check (result in ('SUCCESS','FAILED','TIMEOUT','SOLUTION_VIEWED')),
    constraint ck_coding_practice_log_02 check (source in ('MANUAL','GITHUB')),
    constraint ck_coding_practice_log_03 check (minutes is null or minutes between 0 and 600),
    constraint ck_coding_practice_log_04 check (retry_step between 0 and 3 and (retry_step = 0) = (next_retry_at is null))
);
create index ix_coding_practice_log_01 on daily_career.coding_practice_log (user_id, solved_on desc, id desc);
create index ix_coding_practice_log_02 on daily_career.coding_practice_log (user_id, problem_key, id desc);
comment on column daily_career.coding_practice_log.problem_key is '같은 문제 판정 키(link:/url:/title:). 다시 풀기 일정은 키별 최신 기록에만 남는다';

-- Seed: Programmers public problems, title and level checked on the site on 2026-10-25.
insert into daily_career.coding_problem_link (site, site_problem_ref, title, url, level, category, link_checked_at) values
    ('PROGRAMMERS', '42576', '완주하지 못한 선수', 'https://school.programmers.co.kr/learn/courses/30/lessons/42576', 1, 'DATA_STRUCTURE', date '2026-10-25'),
    ('PROGRAMMERS', '1845', '폰켓몬', 'https://school.programmers.co.kr/learn/courses/30/lessons/1845', 1, 'DATA_STRUCTURE', date '2026-10-25'),
    ('PROGRAMMERS', '42577', '전화번호 목록', 'https://school.programmers.co.kr/learn/courses/30/lessons/42577', 2, 'DATA_STRUCTURE', date '2026-10-25'),
    ('PROGRAMMERS', '42586', '기능개발', 'https://school.programmers.co.kr/learn/courses/30/lessons/42586', 2, 'DATA_STRUCTURE', date '2026-10-25'),
    ('PROGRAMMERS', '12909', '올바른 괄호', 'https://school.programmers.co.kr/learn/courses/30/lessons/12909', 2, 'DATA_STRUCTURE', date '2026-10-25'),
    ('PROGRAMMERS', '42626', '더 맵게', 'https://school.programmers.co.kr/learn/courses/30/lessons/42626', 2, 'DATA_STRUCTURE', date '2026-10-25'),
    ('PROGRAMMERS', '42748', 'K번째수', 'https://school.programmers.co.kr/learn/courses/30/lessons/42748', 1, 'SORT', date '2026-10-25'),
    ('PROGRAMMERS', '42746', '가장 큰 수', 'https://school.programmers.co.kr/learn/courses/30/lessons/42746', 2, 'SORT', date '2026-10-25'),
    ('PROGRAMMERS', '42747', 'H-Index', 'https://school.programmers.co.kr/learn/courses/30/lessons/42747', 2, 'SORT', date '2026-10-25'),
    ('PROGRAMMERS', '12915', '문자열 내 마음대로 정렬하기', 'https://school.programmers.co.kr/learn/courses/30/lessons/12915', 1, 'SORT', date '2026-10-25'),
    ('PROGRAMMERS', '86491', '최소직사각형', 'https://school.programmers.co.kr/learn/courses/30/lessons/86491', 1, 'BRUTE_FORCE', date '2026-10-25'),
    ('PROGRAMMERS', '42840', '모의고사', 'https://school.programmers.co.kr/learn/courses/30/lessons/42840', 1, 'BRUTE_FORCE', date '2026-10-25'),
    ('PROGRAMMERS', '42839', '소수 찾기', 'https://school.programmers.co.kr/learn/courses/30/lessons/42839', 2, 'BRUTE_FORCE', date '2026-10-25'),
    ('PROGRAMMERS', '42842', '카펫', 'https://school.programmers.co.kr/learn/courses/30/lessons/42842', 2, 'BRUTE_FORCE', date '2026-10-25'),
    ('PROGRAMMERS', '87946', '피로도', 'https://school.programmers.co.kr/learn/courses/30/lessons/87946', 2, 'BRUTE_FORCE', date '2026-10-25'),
    ('PROGRAMMERS', '84512', '모음사전', 'https://school.programmers.co.kr/learn/courses/30/lessons/84512', 2, 'BRUTE_FORCE', date '2026-10-25'),
    ('PROGRAMMERS', '43165', '타겟 넘버', 'https://school.programmers.co.kr/learn/courses/30/lessons/43165', 2, 'GRAPH_SEARCH', date '2026-10-25'),
    ('PROGRAMMERS', '43162', '네트워크', 'https://school.programmers.co.kr/learn/courses/30/lessons/43162', 3, 'GRAPH_SEARCH', date '2026-10-25'),
    ('PROGRAMMERS', '1844', '게임 맵 최단거리', 'https://school.programmers.co.kr/learn/courses/30/lessons/1844', 2, 'GRAPH_SEARCH', date '2026-10-25'),
    ('PROGRAMMERS', '43163', '단어 변환', 'https://school.programmers.co.kr/learn/courses/30/lessons/43163', 3, 'GRAPH_SEARCH', date '2026-10-25'),
    ('PROGRAMMERS', '49189', '가장 먼 노드', 'https://school.programmers.co.kr/learn/courses/30/lessons/49189', 3, 'GRAPH_SEARCH', date '2026-10-25'),
    ('PROGRAMMERS', '43238', '입국심사', 'https://school.programmers.co.kr/learn/courses/30/lessons/43238', 3, 'BINARY_SEARCH', date '2026-10-25'),
    ('PROGRAMMERS', '43236', '징검다리', 'https://school.programmers.co.kr/learn/courses/30/lessons/43236', 4, 'BINARY_SEARCH', date '2026-10-25'),
    ('PROGRAMMERS', '64062', '징검다리 건너기', 'https://school.programmers.co.kr/learn/courses/30/lessons/64062', 3, 'BINARY_SEARCH', date '2026-10-25'),
    ('PROGRAMMERS', '178870', '연속된 부분 수열의 합', 'https://school.programmers.co.kr/learn/courses/30/lessons/178870', 2, 'TWO_POINTER', date '2026-10-25'),
    ('PROGRAMMERS', '131701', '연속 부분 수열 합의 개수', 'https://school.programmers.co.kr/learn/courses/30/lessons/131701', 2, 'TWO_POINTER', date '2026-10-25'),
    ('PROGRAMMERS', '67258', '보석 쇼핑', 'https://school.programmers.co.kr/learn/courses/30/lessons/67258', 3, 'TWO_POINTER', date '2026-10-25'),
    ('PROGRAMMERS', '12924', '숫자의 표현', 'https://school.programmers.co.kr/learn/courses/30/lessons/12924', 2, 'TWO_POINTER', date '2026-10-25'),
    ('PROGRAMMERS', '42862', '체육복', 'https://school.programmers.co.kr/learn/courses/30/lessons/42862', 1, 'GREEDY', date '2026-10-25'),
    ('PROGRAMMERS', '42883', '큰 수 만들기', 'https://school.programmers.co.kr/learn/courses/30/lessons/42883', 2, 'GREEDY', date '2026-10-25'),
    ('PROGRAMMERS', '42885', '구명보트', 'https://school.programmers.co.kr/learn/courses/30/lessons/42885', 2, 'GREEDY', date '2026-10-25'),
    ('PROGRAMMERS', '42861', '섬 연결하기', 'https://school.programmers.co.kr/learn/courses/30/lessons/42861', 3, 'GREEDY', date '2026-10-25'),
    ('PROGRAMMERS', '42884', '단속카메라', 'https://school.programmers.co.kr/learn/courses/30/lessons/42884', 3, 'GREEDY', date '2026-10-25'),
    ('PROGRAMMERS', '43105', '정수 삼각형', 'https://school.programmers.co.kr/learn/courses/30/lessons/43105', 3, 'DP', date '2026-10-25'),
    ('PROGRAMMERS', '42898', '등굣길', 'https://school.programmers.co.kr/learn/courses/30/lessons/42898', 3, 'DP', date '2026-10-25'),
    ('PROGRAMMERS', '12914', '멀리 뛰기', 'https://school.programmers.co.kr/learn/courses/30/lessons/12914', 2, 'DP', date '2026-10-25'),
    ('PROGRAMMERS', '12900', '2 x n 타일링', 'https://school.programmers.co.kr/learn/courses/30/lessons/12900', 2, 'DP', date '2026-10-25'),
    ('PROGRAMMERS', '12913', '땅따먹기', 'https://school.programmers.co.kr/learn/courses/30/lessons/12913', 2, 'DP', date '2026-10-25'),
    ('PROGRAMMERS', '42897', '도둑질', 'https://school.programmers.co.kr/learn/courses/30/lessons/42897', 4, 'DP', date '2026-10-25'),
    ('PROGRAMMERS', '60057', '문자열 압축', 'https://school.programmers.co.kr/learn/courses/30/lessons/60057', 2, 'STRING', date '2026-10-25'),
    ('PROGRAMMERS', '12951', 'JadenCase 문자열 만들기', 'https://school.programmers.co.kr/learn/courses/30/lessons/12951', 2, 'STRING', date '2026-10-25'),
    ('PROGRAMMERS', '17682', '[1차] 다트 게임', 'https://school.programmers.co.kr/learn/courses/30/lessons/17682', 1, 'STRING', date '2026-10-25'),
    ('PROGRAMMERS', '60058', '괄호 변환', 'https://school.programmers.co.kr/learn/courses/30/lessons/60058', 2, 'STRING', date '2026-10-25'),
    ('PROGRAMMERS', '12926', '시저 암호', 'https://school.programmers.co.kr/learn/courses/30/lessons/12926', 1, 'STRING', date '2026-10-25'),
    ('PROGRAMMERS', '92334', '신고 결과 받기', 'https://school.programmers.co.kr/learn/courses/30/lessons/92334', 1, 'IMPLEMENTATION', date '2026-10-25'),
    ('PROGRAMMERS', '118666', '성격 유형 검사하기', 'https://school.programmers.co.kr/learn/courses/30/lessons/118666', 1, 'IMPLEMENTATION', date '2026-10-25'),
    ('PROGRAMMERS', '72410', '신규 아이디 추천', 'https://school.programmers.co.kr/learn/courses/30/lessons/72410', 1, 'IMPLEMENTATION', date '2026-10-25'),
    ('PROGRAMMERS', '92341', '주차 요금 계산', 'https://school.programmers.co.kr/learn/courses/30/lessons/92341', 2, 'IMPLEMENTATION', date '2026-10-25'),
    ('PROGRAMMERS', '150370', '개인정보 수집 유효기간', 'https://school.programmers.co.kr/learn/courses/30/lessons/150370', 1, 'IMPLEMENTATION', date '2026-10-25'),
    ('PROGRAMMERS', '17680', '[1차] 캐시', 'https://school.programmers.co.kr/learn/courses/30/lessons/17680', 2, 'IMPLEMENTATION', date '2026-10-25'),
    ('PROGRAMMERS', '59034', '모든 레코드 조회하기', 'https://school.programmers.co.kr/learn/courses/30/lessons/59034', 1, 'SQL_SELECT', date '2026-10-25'),
    ('PROGRAMMERS', '59036', '아픈 동물 찾기', 'https://school.programmers.co.kr/learn/courses/30/lessons/59036', 1, 'SQL_SELECT', date '2026-10-25'),
    ('PROGRAMMERS', '59037', '어린 동물 찾기', 'https://school.programmers.co.kr/learn/courses/30/lessons/59037', 1, 'SQL_SELECT', date '2026-10-25'),
    ('PROGRAMMERS', '59039', '이름이 없는 동물의 아이디', 'https://school.programmers.co.kr/learn/courses/30/lessons/59039', 1, 'SQL_SELECT', date '2026-10-25'),
    ('PROGRAMMERS', '59038', '최솟값 구하기', 'https://school.programmers.co.kr/learn/courses/30/lessons/59038', 2, 'SQL_AGGREGATE', date '2026-10-25'),
    ('PROGRAMMERS', '59406', '동물 수 구하기', 'https://school.programmers.co.kr/learn/courses/30/lessons/59406', 2, 'SQL_AGGREGATE', date '2026-10-25'),
    ('PROGRAMMERS', '59408', '중복 제거하기', 'https://school.programmers.co.kr/learn/courses/30/lessons/59408', 2, 'SQL_AGGREGATE', date '2026-10-25'),
    ('PROGRAMMERS', '59040', '고양이와 개는 몇 마리 있을까', 'https://school.programmers.co.kr/learn/courses/30/lessons/59040', 2, 'SQL_AGGREGATE', date '2026-10-25'),
    ('PROGRAMMERS', '59041', '동명 동물 수 찾기', 'https://school.programmers.co.kr/learn/courses/30/lessons/59041', 2, 'SQL_AGGREGATE', date '2026-10-25'),
    ('PROGRAMMERS', '59412', '입양 시각 구하기(1)', 'https://school.programmers.co.kr/learn/courses/30/lessons/59412', 2, 'SQL_AGGREGATE', date '2026-10-25'),
    ('PROGRAMMERS', '59042', '없어진 기록 찾기', 'https://school.programmers.co.kr/learn/courses/30/lessons/59042', 3, 'SQL_JOIN', date '2026-10-25'),
    ('PROGRAMMERS', '59043', '있었는데요 없었습니다', 'https://school.programmers.co.kr/learn/courses/30/lessons/59043', 3, 'SQL_JOIN', date '2026-10-25'),
    ('PROGRAMMERS', '59044', '오랜 기간 보호한 동물(1)', 'https://school.programmers.co.kr/learn/courses/30/lessons/59044', 3, 'SQL_JOIN', date '2026-10-25'),
    ('PROGRAMMERS', '59045', '보호소에서 중성화한 동물', 'https://school.programmers.co.kr/learn/courses/30/lessons/59045', 4, 'SQL_JOIN', date '2026-10-25'),
    ('PROGRAMMERS', '131123', '즐겨찾기가 가장 많은 식당 정보 출력하기', 'https://school.programmers.co.kr/learn/courses/30/lessons/131123', 3, 'SQL_SUBQUERY', date '2026-10-25'),
    ('PROGRAMMERS', '131124', '그룹별 조건에 맞는 식당 목록 출력하기', 'https://school.programmers.co.kr/learn/courses/30/lessons/131124', 4, 'SQL_SUBQUERY', date '2026-10-25'),
    ('PROGRAMMERS', '157339', '특정 기간동안 대여 가능한 자동차들의 대여비용 구하기', 'https://school.programmers.co.kr/learn/courses/30/lessons/157339', 4, 'SQL_SUBQUERY', date '2026-10-25'),
    ('PROGRAMMERS', '301649', '대장균의 크기에 따라 분류하기 2', 'https://school.programmers.co.kr/learn/courses/30/lessons/301649', 3, 'SQL_WINDOW', date '2026-10-25'),
    ('PROGRAMMERS', '59410', 'NULL 처리하기', 'https://school.programmers.co.kr/learn/courses/30/lessons/59410', 2, 'SQL_FUNCTION', date '2026-10-25'),
    ('PROGRAMMERS', '59409', '중성화 여부 파악하기', 'https://school.programmers.co.kr/learn/courses/30/lessons/59409', 2, 'SQL_FUNCTION', date '2026-10-25'),
    ('PROGRAMMERS', '59414', 'DATETIME에서 DATE로 형 변환', 'https://school.programmers.co.kr/learn/courses/30/lessons/59414', 2, 'SQL_FUNCTION', date '2026-10-25'),
    ('PROGRAMMERS', '59046', '루시와 엘라 찾기', 'https://school.programmers.co.kr/learn/courses/30/lessons/59046', 2, 'SQL_FUNCTION', date '2026-10-25'),
    ('PROGRAMMERS', '59047', '이름에 el이 들어가는 동물 찾기', 'https://school.programmers.co.kr/learn/courses/30/lessons/59047', 2, 'SQL_FUNCTION', date '2026-10-25');
