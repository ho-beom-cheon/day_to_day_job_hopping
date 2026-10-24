-- Preserve published catalog versions and learner submissions while changing the
-- assigned four-week course to self-confirmed hands-on practice.
UPDATE daily_career.assigned_content a
SET completion_rule = 'READ_ACK'
FROM daily_career.learning_day_item i
JOIN daily_career.user_curriculum uc ON uc.id = i.user_curriculum_id AND uc.user_id = i.user_id
JOIN daily_career.curriculum_version v ON v.id = uc.curriculum_version_id
JOIN daily_career.curriculum c ON c.id = v.curriculum_id
WHERE a.learning_day_item_id = i.id
  AND a.user_id = i.user_id
  AND c.code = 'JAVA-CORE-4W'
  AND a.content_type = 'PRACTICE'
  AND a.completion_rule = 'ALL_PROBLEMS_ATTEMPTED';
