ALTER TABLE stores ADD COLUMN manual_plan_override BOOLEAN NOT NULL DEFAULT FALSE;

-- Preserve existing paid overrides only when they are still the latest plan decision.
UPDATE stores s
SET manual_plan_override = TRUE, billing_suspended = FALSE
WHERE s.plan IN ('PREMIUM', 'PREMIUM_PLUS', 'PREMIUM_ULTRA')
  AND NOT EXISTS (
    SELECT 1 FROM payment_subscriptions paid
    WHERE paid.store_id = s.store_id AND paid.access_active = TRUE
  )
  AND EXISTS (
    SELECT 1 FROM plan_events e
    WHERE e.store_id = s.store_id AND e.source = 'OVERRIDE' AND e.to_plan = s.plan
      AND NOT EXISTS (
        SELECT 1 FROM plan_events newer
        WHERE newer.store_id = e.store_id AND newer.id > e.id
      )
  );
