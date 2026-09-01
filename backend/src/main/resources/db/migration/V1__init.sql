-- 달림 MVP1 initial schema.
-- Entities per docs/01-feature-spec.md / docs/02-api-spec.md: User, SketchRoute, SavedRoute,
-- RunRecord, GpsPoint. Geometry columns use PostGIS `geography` (accurate spherical distance)
-- and are intentionally not modeled in Exposed — see com.dallim.common.PostGis.

CREATE EXTENSION IF NOT EXISTS postgis;

-- ============================================================
-- users
-- ============================================================
CREATE TABLE users (
    id                    VARCHAR(32) PRIMARY KEY,
    provider              VARCHAR(16) NOT NULL,               -- GOOGLE | KAKAO | EMAIL
    provider_id           VARCHAR(255) NOT NULL,               -- EMAIL provider: the email address
    email                 VARCHAR(254),
    password_hash         VARCHAR(100),                        -- BCrypt hash, EMAIL provider only. Never log/expose.

    nickname              VARCHAR(30),
    avatar_id             VARCHAR(32),
    running_experience    VARCHAR(32),
    comfortable_pace      VARCHAR(32),

    -- Server-only. Must never be selected into a response DTO (CLAUDE.md rule 2).
    gender                VARCHAR(16),

    total_runs            INTEGER NOT NULL DEFAULT 0,
    total_distance_km     DOUBLE PRECISION NOT NULL DEFAULT 0,

    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_users_provider_provider_id ON users (provider, provider_id);
CREATE UNIQUE INDEX uq_users_nickname ON users (nickname) WHERE nickname IS NOT NULL;

-- ============================================================
-- sketch_routes  (operator-curated MVP1 routes)
-- ============================================================
CREATE TABLE sketch_routes (
    id                      VARCHAR(32) PRIMARY KEY,
    name                    VARCHAR(50) NOT NULL,
    emoji                   VARCHAR(8) NOT NULL,

    -- Planned route geometry (GET /routes/{id}.geoJson). SRID 4326 = WGS84 lat/lng.
    path                    geography(LineString, 4326) NOT NULL,

    distance_km             DOUBLE PRECISION NOT NULL,
    estimated_minutes       INTEGER NOT NULL,
    difficulty              VARCHAR(16),
    status                  VARCHAR(16) NOT NULL DEFAULT 'DISCOVERY', -- DISCOVERY | VERIFIED | POPULAR

    finisher_count          INTEGER NOT NULL DEFAULT 0,
    traffic_light_count     INTEGER NOT NULL DEFAULT 0,
    elevation_gain_m        INTEGER NOT NULL DEFAULT 0,
    repeat_segment_percent  INTEGER NOT NULL DEFAULT 0,
    runability              DOUBLE PRECISION NOT NULL DEFAULT 0,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Radius search index (GET /routes?lat&lng&radiusKm -> ST_DWithin).
CREATE INDEX idx_sketch_routes_path_gist ON sketch_routes USING GIST (path);

-- ============================================================
-- saved_routes  (User <-> SketchRoute bookmark, many-to-many)
-- ============================================================
CREATE TABLE saved_routes (
    id          VARCHAR(32) PRIMARY KEY,
    user_id     VARCHAR(32) NOT NULL REFERENCES users (id),
    route_id    VARCHAR(32) NOT NULL REFERENCES sketch_routes (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_saved_routes_user_route ON saved_routes (user_id, route_id);

-- ============================================================
-- run_records
-- ============================================================
CREATE TABLE run_records (
    id                            VARCHAR(32) PRIMARY KEY,
    user_id                       VARCHAR(32) NOT NULL REFERENCES users (id),
    route_id                      VARCHAR(32) NOT NULL REFERENCES sketch_routes (id),

    mode                          VARCHAR(16) NOT NULL DEFAULT 'SOLO',
    status                        VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS',
    -- IN_PROGRESS | RUNNING | PAUSED | COMPLETED | PARTIAL | ABORTED | UNDER_REVIEW

    started_at                    TIMESTAMPTZ NOT NULL,
    finished_at                   TIMESTAMPTZ,

    -- Server-computed judgement outputs (docs/01-feature-spec.md 2.2.D), null until finish.
    -- Douglas-Peucker-simplified actual GPS track.
    actual_path                   geography(LineString, 4326),
    distance_km                   DOUBLE PRECISION,
    duration_seconds              INTEGER,
    average_pace_sec_per_km       INTEGER,
    sketch_match_percent          INTEGER,
    route_completion_percent      INTEGER,
    is_first_discoverer           BOOLEAN NOT NULL DEFAULT FALSE,
    earned_ink                    INTEGER NOT NULL DEFAULT 0,

    created_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_run_records_user_id ON run_records (user_id);
CREATE INDEX idx_run_records_route_id ON run_records (route_id);

-- ============================================================
-- gps_points  (raw points, POST /runs/{runId}/gps-batch)
-- ============================================================
CREATE TABLE gps_points (
    id           VARCHAR(32) PRIMARY KEY,
    run_id       VARCHAR(32) NOT NULL REFERENCES run_records (id),
    sequence     INTEGER NOT NULL,
    lat          DOUBLE PRECISION NOT NULL,
    lng          DOUBLE PRECISION NOT NULL,
    "timestamp"  TIMESTAMPTZ NOT NULL,
    accuracy_m   DOUBLE PRECISION,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_gps_points_run_id_sequence ON gps_points (run_id, sequence);
