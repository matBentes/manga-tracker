ALTER TABLE manga ADD COLUMN reading_status VARCHAR(32) NOT NULL DEFAULT 'READING';
ALTER TABLE manga ADD COLUMN mangadex_id UUID;
ALTER TABLE manga ALTER COLUMN source_url DROP NOT NULL;
DROP INDEX uq_manga_owner_source_url;
CREATE UNIQUE INDEX uq_manga_owner_mangadex_id
    ON manga (owner_id, mangadex_id)
    WHERE mangadex_id IS NOT NULL;
