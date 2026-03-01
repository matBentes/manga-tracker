CREATE TABLE manga (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    title                 VARCHAR(255) NOT NULL,
    source_url            TEXT         NOT NULL,
    current_chapter       INTEGER      NOT NULL DEFAULT 0,
    latest_chapter        INTEGER      NOT NULL DEFAULT 0,
    notifications_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    last_checked_at       TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_manga PRIMARY KEY (id),
    CONSTRAINT uq_manga_source_url UNIQUE (source_url)
);

CREATE UNIQUE INDEX idx_manga_source_url ON manga (source_url);
