-- Legacy ENDURANCE goals were stored in seconds.
-- Normalize stored values to minutes so API, iOS, and schema docs use one canonical unit.

UPDATE goal_checkpoints gc
SET actual_value = ROUND(gc.actual_value / 60.0, 2),
    projected_value = ROUND(gc.projected_value / 60.0, 2)
FROM goals g
WHERE gc.goal_id = g.id
  AND g.goal_type = 'ENDURANCE'
  AND g.target_unit = 'seconds';

UPDATE goals
SET target_value = ROUND(target_value / 60.0, 2),
    start_value = ROUND(start_value / 60.0, 2),
    weekly_rate_target = ROUND(weekly_rate_target / 60.0, 2),
    target_unit = 'minutes',
    updated_at = NOW()
WHERE goal_type = 'ENDURANCE'
  AND target_unit = 'seconds';
