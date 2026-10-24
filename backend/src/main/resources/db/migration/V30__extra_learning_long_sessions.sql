-- Preserve legacy 15-minute records; offer 30/60/90/120 minute sessions in the UI.
ALTER TABLE daily_career.extra_learning DROP CONSTRAINT extra_learning_target_minutes_check;
ALTER TABLE daily_career.extra_learning ADD CONSTRAINT extra_learning_target_minutes_check CHECK (target_minutes IN (15,30,60,90,120));
