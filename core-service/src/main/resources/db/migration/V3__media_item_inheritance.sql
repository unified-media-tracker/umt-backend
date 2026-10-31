-- ============================================================
-- V3: move to JOINED inheritance.
-- Common columns of movie / tv_show / game / book / music are extracted into media_item.
-- Existing UUIDs are globally unique, so every row keeps its id: media_item.id = <child>.id.
-- That means genre / platform join tables and all loosely referenced tables keep working untouched.
-- Back up the database before running: the common columns are dropped from the child tables.
-- ============================================================


-- ------------------------------------------------------------
-- 1. Parent table
-- ------------------------------------------------------------
-- media_category is VARCHAR (not the native media_category enum): Hibernate sends the discriminator
-- as a string, and PostgreSQL would reject varchar -> enum on insert. CHECK keeps values under control.
CREATE TABLE media_item
(
    id                  UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    media_category      VARCHAR(31)    NOT NULL
        CONSTRAINT chk_media_item_category CHECK (media_category IN ('MOVIE', 'TV_SHOW', 'GAME', 'BOOK', 'MUSIC')),
    title               VARCHAR(255)   NOT NULL,
    description         TEXT,
    cover_image_url     TEXT,
    age_rating          VARCHAR(10),
    release_date        DATE,
    release_date_status release_status NOT NULL DEFAULT 'TBA',
    popularity_score    NUMERIC        NOT NULL DEFAULT 0,
    average_user_rating NUMERIC(3, 1),
    rating_count        INTEGER        NOT NULL DEFAULT 0,
    franchise_id        UUID, -- points at a (:Franchise) node in Neo4j; no local FK by design
    created_at          TIMESTAMP      NOT NULL DEFAULT now()
);


-- ------------------------------------------------------------
-- 2. Copy common data from the child tables (ids are preserved)
-- ------------------------------------------------------------
INSERT INTO media_item (id, media_category, title, description, cover_image_url, age_rating, release_date,
                        release_date_status, popularity_score, average_user_rating, rating_count,
                        franchise_id, created_at)
SELECT id, 'MOVIE', title, description, cover_image_url, age_rating, release_date,
       release_date_status, popularity_score, average_user_rating, rating_count, franchise_id, created_at
FROM movie;

INSERT INTO media_item (id, media_category, title, description, cover_image_url, age_rating, release_date,
                        release_date_status, popularity_score, average_user_rating, rating_count,
                        franchise_id, created_at)
SELECT id, 'TV_SHOW', title, description, cover_image_url, age_rating, release_date,
       release_date_status, popularity_score, average_user_rating, rating_count, franchise_id, created_at
FROM tv_show;

INSERT INTO media_item (id, media_category, title, description, cover_image_url, age_rating, release_date,
                        release_date_status, popularity_score, average_user_rating, rating_count,
                        franchise_id, created_at)
SELECT id, 'GAME', title, description, cover_image_url, age_rating, release_date,
       release_date_status, popularity_score, average_user_rating, rating_count, franchise_id, created_at
FROM game;

INSERT INTO media_item (id, media_category, title, description, cover_image_url, age_rating, release_date,
                        release_date_status, popularity_score, average_user_rating, rating_count,
                        franchise_id, created_at)
SELECT id, 'BOOK', title, description, cover_image_url, age_rating, release_date,
       release_date_status, popularity_score, average_user_rating, rating_count, franchise_id, created_at
FROM book;

INSERT INTO media_item (id, media_category, title, description, cover_image_url, age_rating, release_date,
                        release_date_status, popularity_score, average_user_rating, rating_count,
                        franchise_id, created_at)
SELECT id, 'MUSIC', title, description, cover_image_url, age_rating, release_date,
       release_date_status, popularity_score, average_user_rating, rating_count, franchise_id, created_at
FROM music;


-- ------------------------------------------------------------
-- 3. Slim down the child tables: drop moved columns (their indexes go with them),
--    drop the id default (the id now comes from media_item) and add PK -> FK link.
-- ------------------------------------------------------------
ALTER TABLE movie
DROP COLUMN title,
    DROP COLUMN description,
    DROP COLUMN cover_image_url,
    DROP COLUMN age_rating,
    DROP COLUMN release_date,
    DROP COLUMN release_date_status,
    DROP COLUMN popularity_score,
    DROP COLUMN average_user_rating,
    DROP COLUMN rating_count,
    DROP COLUMN franchise_id,
    DROP COLUMN created_at,
    ALTER COLUMN id DROP DEFAULT,
    ADD CONSTRAINT fk_movie_media_item FOREIGN KEY (id) REFERENCES media_item (id) ON DELETE CASCADE;

ALTER TABLE tv_show
DROP COLUMN title,
    DROP COLUMN description,
    DROP COLUMN cover_image_url,
    DROP COLUMN age_rating,
    DROP COLUMN release_date,
    DROP COLUMN release_date_status,
    DROP COLUMN popularity_score,
    DROP COLUMN average_user_rating,
    DROP COLUMN rating_count,
    DROP COLUMN franchise_id,
    DROP COLUMN created_at,
    ALTER COLUMN id DROP DEFAULT,
    ADD CONSTRAINT fk_tv_show_media_item FOREIGN KEY (id) REFERENCES media_item (id) ON DELETE CASCADE;

ALTER TABLE game
DROP COLUMN title,
    DROP COLUMN description,
    DROP COLUMN cover_image_url,
    DROP COLUMN age_rating,
    DROP COLUMN release_date,
    DROP COLUMN release_date_status,
    DROP COLUMN popularity_score,
    DROP COLUMN average_user_rating,
    DROP COLUMN rating_count,
    DROP COLUMN franchise_id,
    DROP COLUMN created_at,
    ALTER COLUMN id DROP DEFAULT,
    ADD CONSTRAINT fk_game_media_item FOREIGN KEY (id) REFERENCES media_item (id) ON DELETE CASCADE;

ALTER TABLE book
DROP COLUMN title,
    DROP COLUMN description,
    DROP COLUMN cover_image_url,
    DROP COLUMN age_rating,
    DROP COLUMN release_date,
    DROP COLUMN release_date_status,
    DROP COLUMN popularity_score,
    DROP COLUMN average_user_rating,
    DROP COLUMN rating_count,
    DROP COLUMN franchise_id,
    DROP COLUMN created_at,
    ALTER COLUMN id DROP DEFAULT,
    ADD CONSTRAINT fk_book_media_item FOREIGN KEY (id) REFERENCES media_item (id) ON DELETE CASCADE;

ALTER TABLE music
DROP COLUMN title,
    DROP COLUMN description,
    DROP COLUMN cover_image_url,
    DROP COLUMN age_rating,
    DROP COLUMN release_date,
    DROP COLUMN release_date_status,
    DROP COLUMN popularity_score,
    DROP COLUMN average_user_rating,
    DROP COLUMN rating_count,
    DROP COLUMN franchise_id,
    DROP COLUMN created_at,
    ALTER COLUMN id DROP DEFAULT,
    ADD CONSTRAINT fk_music_media_item FOREIGN KEY (id) REFERENCES media_item (id) ON DELETE CASCADE;


-- ------------------------------------------------------------
-- 4. Indexes on the parent table (replace the per-table ones dropped above)
-- ------------------------------------------------------------
CREATE INDEX idx_media_item_release_date ON media_item (release_date);
CREATE INDEX idx_media_item_release_date_status ON media_item (release_date_status);
CREATE INDEX idx_media_item_franchise_id ON media_item (franchise_id);
CREATE INDEX idx_media_item_category_popularity ON media_item (media_category, popularity_score DESC);

-- fast "title like '%query%'" search across all media (needs privileges to create the extension).
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_media_item_title_trgm ON media_item USING gin (lower(title) gin_trgm_ops);


-- ------------------------------------------------------------
-- 5. Real FKs for the tables that were "loosely referenced" before.
--    There is a single table to point at now. NOT VALID enforces the FK for new rows right away
--    without failing on possible old orphans; clean them up and run VALIDATE CONSTRAINT afterwards.
-- ------------------------------------------------------------
ALTER TABLE credit
    ADD CONSTRAINT fk_credit_media_item
        FOREIGN KEY (media_item_id) REFERENCES media_item (id) ON DELETE CASCADE NOT VALID;

ALTER TABLE rumor_snapshot
    ADD CONSTRAINT fk_rumor_snapshot_media_item
        FOREIGN KEY (media_item_id) REFERENCES media_item (id) ON DELETE CASCADE NOT VALID;

ALTER TABLE release_status_history
    ADD CONSTRAINT fk_release_status_history_media_item
        FOREIGN KEY (media_item_id) REFERENCES media_item (id) ON DELETE CASCADE NOT VALID;

ALTER TABLE purchase_link
    ADD CONSTRAINT fk_purchase_link_media_item
        FOREIGN KEY (media_item_id) REFERENCES media_item (id) ON DELETE CASCADE NOT VALID;

-- After checking for orphans (e.g. SELECT * FROM credit c WHERE NOT EXISTS (SELECT 1 FROM media_item m WHERE m.id = c.media_item_id)):
-- ALTER TABLE credit VALIDATE CONSTRAINT fk_credit_media_item;
-- ALTER TABLE rumor_snapshot VALIDATE CONSTRAINT fk_rumor_snapshot_media_item;
-- ALTER TABLE release_status_history VALIDATE CONSTRAINT fk_release_status_history_media_item;
-- ALTER TABLE purchase_link VALIDATE CONSTRAINT fk_purchase_link_media_item;