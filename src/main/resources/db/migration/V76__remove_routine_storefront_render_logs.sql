-- Remove only routine successful render samples; retain warnings and errors.
-- Safe when these events have already been removed manually.
DELETE FROM integration_logs
WHERE level = 'INFO'
  AND event_type IN (
      'storefront.sdk.rendered',
      'storefront.sdk.patagonia_transition_rendered'
  );
