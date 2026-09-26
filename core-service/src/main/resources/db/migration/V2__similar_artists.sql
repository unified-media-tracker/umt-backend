-- A row here with no similar_artist rows means ListenBrainz was asked and had nothing for the artist.
CREATE TABLE artist_similarity
(
    artist_mbid VARCHAR(36) PRIMARY KEY,
    fetched_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE similar_artist
(
    artist_mbid         VARCHAR(36)  NOT NULL REFERENCES artist_similarity (artist_mbid) ON DELETE CASCADE,
    similar_artist_mbid VARCHAR(36)  NOT NULL,
    name                VARCHAR(255) NOT NULL,
    score               INTEGER      NOT NULL,
    PRIMARY KEY (artist_mbid, similar_artist_mbid)
);
