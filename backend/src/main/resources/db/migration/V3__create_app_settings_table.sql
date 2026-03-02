CREATE TABLE app_settings (
    id                         INTEGER      NOT NULL DEFAULT 1,
    email_notifications_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    notification_email         VARCHAR(255) NOT NULL,
    poll_interval_minutes      INTEGER      NOT NULL DEFAULT 30,

    CONSTRAINT pk_app_settings PRIMARY KEY (id),
    CONSTRAINT chk_app_settings_single_row CHECK (id = 1)
);

INSERT INTO app_settings (id, email_notifications_enabled, notification_email, poll_interval_minutes)
VALUES (1, TRUE, 'user@localhost', 30);
