-- Per-user marks on recommendation links: hidden, or already solved before using the app (design §4.7.3).
-- A link carries at most one mark. Neither counts toward the weekly target or success rates.

alter table daily_career.coding_problem_hidden rename to coding_problem_mark;
alter table daily_career.coding_problem_mark rename constraint pk_coding_problem_hidden to pk_coding_problem_mark;
alter table daily_career.coding_problem_mark rename column hidden_at to marked_at;
alter table daily_career.coding_problem_mark
    add column kind varchar(16) not null default 'HIDDEN',
    add constraint ck_coding_problem_mark_01 check (kind in ('HIDDEN','SOLVED_BEFORE'));
comment on table daily_career.coding_problem_mark is '사용자별 추천 링크 표시. HIDDEN은 숨김, SOLVED_BEFORE는 앱을 쓰기 전에 이미 푼 문제. 둘 다 추천에서 빠지고 주간 목표·성공률에는 세지 않는다';
