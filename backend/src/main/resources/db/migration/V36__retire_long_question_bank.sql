-- 과제 생성 규칙이 '짧은 질문 하나 + 나머지는 꼬리질문'으로 바뀌었다.
-- 요구를 한꺼번에 나열하던 이전 공용 과제는 더 배정하지 않고, 다음 배정 때 새 규칙으로 다시 만든다.
-- 이미 배정된 과제와 이력은 bank_item_id로 그대로 남는다.
update daily_career.task_bank_item set status = 'RETIRED'
where owner_user_id is null and status = 'ACTIVE';
