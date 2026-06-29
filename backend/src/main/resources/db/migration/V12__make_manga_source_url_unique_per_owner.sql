-- Libraries are owner-scoped, so the same source URL may be tracked by different users.
ALTER TABLE manga DROP CONSTRAINT IF EXISTS uq_manga_source_url;
DROP INDEX IF EXISTS idx_manga_source_url;

CREATE UNIQUE INDEX uq_manga_owner_source_url
    ON manga (owner_id, source_url)
    WHERE owner_id IS NOT NULL;
