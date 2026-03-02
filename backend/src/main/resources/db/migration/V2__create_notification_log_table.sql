CREATE TABLE notification_log (
    id             UUID      NOT NULL DEFAULT gen_random_uuid(),
    manga_id       UUID      NOT NULL,
    chapter_number INTEGER   NOT NULL,
    sent_at        TIMESTAMP NOT NULL,

    CONSTRAINT pk_notification_log PRIMARY KEY (id),
    CONSTRAINT fk_notification_log_manga FOREIGN KEY (manga_id) REFERENCES manga (id) ON DELETE CASCADE,
    CONSTRAINT uq_notification_log_manga_chapter UNIQUE (manga_id, chapter_number)
);

CREATE UNIQUE INDEX idx_notification_log_manga_chapter ON notification_log (manga_id, chapter_number);
