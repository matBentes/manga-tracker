-- The only configurable settings (email recipient, poll interval) were removed. Notifications are
-- Web Push only and the scrape runs on a fixed daily 08:00 schedule, so app_settings is now dead.
DROP TABLE app_settings;
