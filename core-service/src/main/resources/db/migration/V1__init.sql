CREATE TYPE plan_tier AS ENUM ('FREE', 'PREMIUM');
CREATE TYPE media_category AS ENUM ('MOVIE','TV_SHOW', 'GAME', 'BOOK', 'MUSIC');
CREATE TYPE release_status AS ENUM ('TBA', 'ANNOUNCED', 'RUMORED', 'CONFIRMED', 'DELAYED', 'RELEASED', 'CANCELED');
CREATE TYPE external_source_type AS ENUM ('TMDB', 'IGDB', 'MUSICBRAINZ', 'HARDCOVER');
CREATE TYPE tag_type AS ENUM ('MOOD', 'SETTING', 'KEYWORD');
CREATE TYPE contributor_type AS ENUM ('PERSON', 'ORGANIZATION');
CREATE TYPE role_type AS ENUM ('DIRECTOR', 'DEVELOPER', 'AUTHOR', 'ARTIST', 'WRITER', 'STUDIO', 'PUBLISHER');
CREATE TYPE availability_status AS ENUM ('AVAILABLE', 'PREORDER', 'UNAVAILABLE');
CREATE TYPE trend_direction AS ENUM ('RISING', 'FALLING', 'STABLE');


-- ============================================================
-- USER (thin local shadow of Keycloak — no credentials here)
-- ============================================================

CREATE TABLE "user"
(
    id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    keycloak_id VARCHAR(255) NOT NULL UNIQUE,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    avatar_url  TEXT,
    plan_tier   plan_tier    NOT NULL DEFAULT 'FREE',
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);


-- ============================================================
-- MEDIA
-- ============================================================

CREATE TABLE genre
(
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE platform
(
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE movie
(
    id                  UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
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
    tmdb_id             VARCHAR(255)   NOT NULL UNIQUE,
    runtime_minutes     INTEGER,
    created_at          TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_movie_release_date ON movie (release_date);
CREATE INDEX idx_movie_release_date_status ON movie (release_date_status);
CREATE INDEX idx_movie_franchise_id ON movie (franchise_id);

CREATE TABLE movie_genre
(
    movie_id UUID NOT NULL REFERENCES movie (id) ON DELETE CASCADE,
    genre_id UUID NOT NULL REFERENCES genre (id) ON DELETE CASCADE,
    PRIMARY KEY (movie_id, genre_id)
);

CREATE TABLE tv_show
(
    id                  UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    title               VARCHAR(255)   NOT NULL,
    description         TEXT,
    cover_image_url     TEXT,
    age_rating          VARCHAR(10),
    release_date        DATE,
    release_date_status release_status NOT NULL DEFAULT 'TBA',
    popularity_score    NUMERIC        NOT NULL DEFAULT 0,
    average_user_rating NUMERIC(3, 1),
    rating_count        INTEGER        NOT NULL DEFAULT 0,
    franchise_id        UUID,
    tmdb_id             VARCHAR(255)   NOT NULL UNIQUE,
    created_at          TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_tv_show_release_date ON tv_show (release_date);
CREATE INDEX idx_tv_show_release_date_status ON tv_show (release_date_status);
CREATE INDEX idx_tv_show_franchise_id ON tv_show (franchise_id);

CREATE TABLE tv_show_genre
(
    tv_show_id UUID NOT NULL REFERENCES tv_show (id) ON DELETE CASCADE,
    genre_id   UUID NOT NULL REFERENCES genre (id) ON DELETE CASCADE,
    PRIMARY KEY (tv_show_id, genre_id)
);

CREATE TABLE game
(
    id                  UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    title               VARCHAR(255)   NOT NULL,
    description         TEXT,
    cover_image_url     TEXT,
    age_rating          VARCHAR(10),
    release_date        DATE,
    release_date_status release_status NOT NULL DEFAULT 'TBA',
    popularity_score    NUMERIC        NOT NULL DEFAULT 0,
    average_user_rating NUMERIC(3, 1),
    rating_count        INTEGER        NOT NULL DEFAULT 0,
    franchise_id        UUID,
    igdb_id             VARCHAR(255)   NOT NULL UNIQUE,
    created_at          TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_game_release_date ON game (release_date);
CREATE INDEX idx_game_release_date_status ON game (release_date_status);
CREATE INDEX idx_game_franchise_id ON game (franchise_id);

CREATE TABLE game_genre
(
    game_id  UUID NOT NULL REFERENCES game (id) ON DELETE CASCADE,
    genre_id UUID NOT NULL REFERENCES genre (id) ON DELETE CASCADE,
    PRIMARY KEY (game_id, genre_id)
);

CREATE TABLE game_platform
(
    game_id     UUID NOT NULL REFERENCES game (id) ON DELETE CASCADE,
    platform_id UUID NOT NULL REFERENCES platform (id) ON DELETE CASCADE,
    PRIMARY KEY (game_id, platform_id)
);

CREATE TABLE book
(
    id                  UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    title               VARCHAR(255)   NOT NULL,
    description         TEXT,
    cover_image_url     TEXT,
    age_rating          VARCHAR(10),
    release_date        DATE,
    release_date_status release_status NOT NULL DEFAULT 'TBA',
    popularity_score    NUMERIC        NOT NULL DEFAULT 0,
    average_user_rating NUMERIC(3, 1),
    rating_count        INTEGER        NOT NULL DEFAULT 0,
    franchise_id        UUID,
    hardcover_id        VARCHAR(255)   NOT NULL UNIQUE,
    created_at          TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_book_release_date ON book (release_date);
CREATE INDEX idx_book_release_date_status ON book (release_date_status);
CREATE INDEX idx_book_franchise_id ON book (franchise_id);

CREATE TABLE book_genre
(
    book_id  UUID NOT NULL REFERENCES book (id) ON DELETE CASCADE,
    genre_id UUID NOT NULL REFERENCES genre (id) ON DELETE CASCADE,
    PRIMARY KEY (book_id, genre_id)
);

CREATE TABLE music
(
    id                  UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    title               VARCHAR(255)   NOT NULL,
    description         TEXT,
    cover_image_url     TEXT,
    age_rating          VARCHAR(10),
    release_date        DATE,
    release_date_status release_status NOT NULL DEFAULT 'TBA',
    popularity_score    NUMERIC        NOT NULL DEFAULT 0,
    average_user_rating NUMERIC(3, 1),
    rating_count        INTEGER        NOT NULL DEFAULT 0,
    franchise_id        UUID,
    musicbrainz_id      VARCHAR(255)   NOT NULL UNIQUE,
    created_at          TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_music_release_date ON music (release_date);
CREATE INDEX idx_music_release_date_status ON music (release_date_status);
CREATE INDEX idx_music_franchise_id ON music (franchise_id);

CREATE TABLE music_genre
(
    music_id UUID NOT NULL REFERENCES music (id) ON DELETE CASCADE,
    genre_id UUID NOT NULL REFERENCES genre (id) ON DELETE CASCADE,
    PRIMARY KEY (music_id, genre_id)
);

CREATE TABLE tag
(
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name     VARCHAR(100) NOT NULL,
    tag_type tag_type     NOT NULL,
    CONSTRAINT uq_tag_name_type UNIQUE (name, tag_type)
);

-- ============================================================
-- CONTRIBUTION
-- ============================================================

CREATE TABLE contributor
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contributor_type   contributor_type NOT NULL,
    name               VARCHAR(255)     NOT NULL,
    description        TEXT,
    image_url          TEXT,
    external_source    external_source_type,
    external_source_id VARCHAR(100)
);

CREATE UNIQUE INDEX uq_contributor_external ON contributor (external_source, external_source_id)
    WHERE external_source IS NOT NULL;

-- Loosely referenced (media_item_id + media_category, no FK): movie/tv_show/game/book/music are five
-- independent tables now, so there's no single table left to put a real FK on. Enforced in
-- application code instead - see Credit.kt's class doc.
CREATE TABLE credit
(
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_item_id  UUID        NOT NULL,
    media_category     media_category  NOT NULL,
    contributor_id UUID        NOT NULL REFERENCES contributor (id) ON DELETE CASCADE,
    role           role_type   NOT NULL
);

CREATE INDEX idx_credit_media_item_id ON credit (media_item_id);
CREATE INDEX idx_credit_contributor_id ON credit (contributor_id);


-- ============================================================
-- RUMOR SNAPSHOT (computed result written from ai-analyser's event)
-- ============================================================

-- Loosely referenced (media_item_id + media_category, no FK) - see credit above.
CREATE TABLE rumor_snapshot
(
    id                        UUID PRIMARY KEY       DEFAULT gen_random_uuid(),
    media_item_id             UUID          NOT NULL,
    media_category                media_category    NOT NULL,
    delay_probability         NUMERIC(5, 2) NOT NULL,
    aggregate_sentiment_score NUMERIC,
    confidence_trend          trend_direction,
    top_source_name           VARCHAR(100),
    computed_at               TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_rumor_snapshot_media_item_computed_at
    ON rumor_snapshot (media_item_id, computed_at DESC);


-- ============================================================
-- RELEASE STATUS HISTORY / PURCHASE LINK — also loosely referenced
-- ============================================================

CREATE TABLE release_status_history
(
    id            UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    media_item_id UUID           NOT NULL,
    media_category    media_category     NOT NULL,
    status        release_status NOT NULL,
    changed_at    TIMESTAMP      NOT NULL DEFAULT now(),
    source_note   TEXT
);

CREATE INDEX idx_release_status_history_media_item_id ON release_status_history (media_item_id);

CREATE TABLE purchase_link
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_item_id       UUID                NOT NULL,
    media_category          media_category          NOT NULL,
    platform_name       VARCHAR(50)         NOT NULL,
    affiliate_url       TEXT                NOT NULL,
    price               NUMERIC,
    currency            CHAR(3),
    availability_status availability_status NOT NULL,
    last_checked_at     TIMESTAMP           NOT NULL
);
