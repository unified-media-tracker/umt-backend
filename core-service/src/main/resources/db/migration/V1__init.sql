CREATE TYPE plan_tier AS ENUM ('FREE', 'PREMIUM');
CREATE TYPE media_type AS ENUM ('MOVIE', 'GAME', 'BOOK', 'MUSIC');
CREATE TYPE release_status AS ENUM ('TBA', 'ANNOUNCED', 'RUMORED', 'CONFIRMED', 'DELAYED', 'RELEASED', 'CANCELED');
CREATE TYPE external_source_type AS ENUM ('TMDB', 'IGDB', 'GOODREADS', 'SPOTIFY');
CREATE TYPE tag_type AS ENUM ('MOOD', 'SETTING', 'KEYWORD');
CREATE TYPE contributor_type AS ENUM ('PERSON', 'ORGANIZATION');
CREATE TYPE role_type AS ENUM ('DIRECTOR', 'DEVELOPER', 'AUTHOR', 'ARTIST', 'WRITER', 'STUDIO', 'PUBLISHER');
CREATE TYPE availability_status AS ENUM ('AVAILABLE', 'PREORDER', 'UNAVAILABLE');


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

CREATE TABLE media_item
(
    id                  UUID PRIMARY KEY              DEFAULT gen_random_uuid(),
    media_type          media_type           NOT NULL,
    title               VARCHAR(255)         NOT NULL,
    description         TEXT,
    cover_image_url     TEXT,
    age_rating          VARCHAR(10),
    release_date        DATE,
    release_date_status release_status       NOT NULL DEFAULT 'TBA',
    popularity_score    NUMERIC              NOT NULL DEFAULT 0,
    average_user_rating NUMERIC(3, 1),
    rating_count        INTEGER              NOT NULL DEFAULT 0,
    franchise_id        UUID, -- points at a (:Franchise) node in Neo4j; no local FK by design
    external_source     external_source_type NOT NULL,
    external_source_id  VARCHAR(100)         NOT NULL,
    created_at          TIMESTAMP            NOT NULL DEFAULT now(),
    CONSTRAINT uq_media_item_external UNIQUE (external_source, external_source_id)
);

CREATE INDEX idx_media_item_media_type ON media_item (media_type);
CREATE INDEX idx_media_item_release_date ON media_item (release_date);
CREATE INDEX idx_media_item_franchise_id ON media_item (franchise_id);

CREATE TABLE platform
(
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE movie_details
(
    media_item_id   UUID PRIMARY KEY REFERENCES media_item (id) ON DELETE CASCADE,
    runtime_minutes INTEGER
);

CREATE TABLE game_details
(
    media_item_id UUID PRIMARY KEY REFERENCES media_item (id) ON DELETE CASCADE
);

CREATE TABLE game_platform
(
    media_item_id UUID NOT NULL REFERENCES game_details (media_item_id) ON DELETE CASCADE,
    platform_id   UUID NOT NULL REFERENCES platform (id) ON DELETE CASCADE,
    PRIMARY KEY (media_item_id, platform_id)
);

CREATE TABLE external_rating
(
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_item_id UUID        NOT NULL REFERENCES media_item (id) ON DELETE CASCADE,
    source        VARCHAR(50) NOT NULL,
    score         NUMERIC     NOT NULL,
    max_scale     NUMERIC     NOT NULL
);

CREATE TABLE purchase_link
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_item_id       UUID                NOT NULL REFERENCES media_item (id) ON DELETE CASCADE,
    platform_name       VARCHAR(50)         NOT NULL,
    affiliate_url       TEXT                NOT NULL,
    price               NUMERIC,
    currency            CHAR(3),
    availability_status availability_status NOT NULL,
    last_checked_at     TIMESTAMP           NOT NULL
);

CREATE TABLE release_status_history
(
    id            UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    media_item_id UUID           NOT NULL REFERENCES media_item (id) ON DELETE CASCADE,
    status        release_status NOT NULL,
    changed_at    TIMESTAMP      NOT NULL DEFAULT now(),
    source_note   TEXT
);

CREATE INDEX idx_release_status_history_media_item_id ON release_status_history (media_item_id);

CREATE TABLE genre
(
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE media_item_genre
(
    media_item_id UUID NOT NULL REFERENCES media_item (id) ON DELETE CASCADE,
    genre_id      UUID NOT NULL REFERENCES genre (id) ON DELETE CASCADE,
    PRIMARY KEY (media_item_id, genre_id)
);

CREATE TABLE tag
(
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name     VARCHAR(100) NOT NULL,
    tag_type tag_type     NOT NULL,
    CONSTRAINT uq_tag_name_type UNIQUE (name, tag_type)
);

CREATE TABLE media_item_tag
(
    media_item_id    UUID NOT NULL REFERENCES media_item (id) ON DELETE CASCADE,
    tag_id           UUID NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    confidence_score NUMERIC,
    PRIMARY KEY (media_item_id, tag_id)
);

-- ============================================================
-- CONTRIBUTION
-- ============================================================

CREATE TABLE contributor
(
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contributor_type contributor_type NOT NULL,
    name             VARCHAR(255)     NOT NULL,
    description      TEXT,
    image_url        TEXT
);

CREATE TABLE credit
(
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_item_id  UUID      NOT NULL REFERENCES media_item (id) ON DELETE CASCADE,
    contributor_id UUID      NOT NULL REFERENCES contributor (id) ON DELETE CASCADE,
    role           role_type NOT NULL
);

CREATE INDEX idx_credit_media_item_id ON credit (media_item_id);
CREATE INDEX idx_credit_contributor_id ON credit (contributor_id);