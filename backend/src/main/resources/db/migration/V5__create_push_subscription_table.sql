CREATE TABLE push_subscription (
    id         UUID      NOT NULL,
    endpoint   TEXT      NOT NULL,
    p256dh     TEXT      NOT NULL,
    auth       TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT pk_push_subscription PRIMARY KEY (id),
    CONSTRAINT uq_push_subscription_endpoint UNIQUE (endpoint)
);
