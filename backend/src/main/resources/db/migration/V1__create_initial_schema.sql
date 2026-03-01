-- ── manga ──────────────────────────────────────────────────────────────────
CREATE TABLE manga (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    title                  VARCHAR(255) NOT NULL,
    source_url             TEXT         NOT NULL,
    current_chapter        INTEGER      NOT NULL DEFAULT 0,
    latest_chapter         INTEGER      NOT NULL DEFAULT 0,
    notifications_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    last_checked_at        TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_manga PRIMARY KEY (id),
    CONSTRAINT uq_manga_source_url UNIQUE (source_url),
    CONSTRAINT chk_manga_current_chapter CHECK (current_chapter >= 0),
    CONSTRAINT chk_manga_latest_chapter  CHECK (latest_chapter  >= 0)
);

-- ── notification_log ───────────────────────────────────────────────────────
CREATE TABLE notification_log (
    id             UUID        NOT NULL DEFAULT gen_random_uuid(),
    manga_id       UUID        NOT NULL,
    chapter_number INTEGER     NOT NULL,
    sent_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_notification_log PRIMARY KEY (id),
    CONSTRAINT fk_notification_log_manga FOREIGN KEY (manga_id)
        REFERENCES manga (id) ON DELETE CASCADE,
    CONSTRAINT uq_notification_log_manga_chapter UNIQUE (manga_id, chapter_number)
);

-- ── app_settings ───────────────────────────────────────────────────────────
CREATE TABLE app_settings (
    id                           INTEGER      NOT NULL DEFAULT 1,
    email_notifications_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    notification_email           VARCHAR(255) NOT NULL,
    poll_interval_minutes        INTEGER      NOT NULL DEFAULT 30,

    CONSTRAINT pk_app_settings PRIMARY KEY (id),
    CONSTRAINT chk_app_settings_single_row CHECK (id = 1)
);

-- Seed default settings row
INSERT INTO app_settings (id, email_notifications_enabled, notification_email, poll_interval_minutes)
VALUES (1, TRUE, 'mateus1337bentes@gmail.com', 30);
