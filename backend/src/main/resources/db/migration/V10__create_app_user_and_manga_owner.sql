-- Authentication: users table (owner + demo, seeded from env at startup) and
-- per-user ownership of manga. Existing manga rows keep owner_id NULL (no backfill
-- in this slice); owner-scoped queries simply won't return them.

CREATE TABLE app_user (
    id            UUID         NOT NULL,
    username      VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_username UNIQUE (username)
);

ALTER TABLE manga
    ADD COLUMN owner_id UUID;

ALTER TABLE manga
    ADD CONSTRAINT fk_manga_owner FOREIGN KEY (owner_id) REFERENCES app_user (id);

CREATE INDEX idx_manga_owner_id ON manga (owner_id);
