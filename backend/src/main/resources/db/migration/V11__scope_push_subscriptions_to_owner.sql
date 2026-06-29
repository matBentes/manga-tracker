-- Existing subscriptions were global and cannot be safely assigned to a user.
-- Clear them so browsers re-subscribe under the authenticated owner.
DELETE FROM push_subscription;

ALTER TABLE push_subscription
    ADD COLUMN owner_id UUID;

ALTER TABLE push_subscription
    ALTER COLUMN owner_id SET NOT NULL;

ALTER TABLE push_subscription
    ADD CONSTRAINT fk_push_subscription_owner FOREIGN KEY (owner_id) REFERENCES app_user (id);

CREATE INDEX idx_push_subscription_owner_id ON push_subscription (owner_id);
