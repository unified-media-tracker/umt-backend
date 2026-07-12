CREATE TYPE plan_tier AS ENUM ('free', 'premium');

CREATE TABLE "user"
(
    id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    keycloak_id VARCHAR(255) NOT NULL UNIQUE,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    avatar_url  TEXT,
    plan_tier   plan_tier    NOT NULL DEFAULT 'free',
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);