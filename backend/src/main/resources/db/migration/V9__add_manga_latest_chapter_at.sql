-- When the latest chapter was last detected (set on add and whenever a newer chapter is scraped).
ALTER TABLE manga ADD COLUMN latest_chapter_at TIMESTAMP;
