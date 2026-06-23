-- Email notifications were replaced by Web Push. Drop the now-unused email columns
-- from the single-row app_settings table; poll_interval_minutes remains in use.
ALTER TABLE app_settings DROP COLUMN email_notifications_enabled;
ALTER TABLE app_settings DROP COLUMN notification_email;
