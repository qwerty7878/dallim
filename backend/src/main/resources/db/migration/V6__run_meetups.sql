-- 같이 달리기 모집 (docs/02-api-spec.md 14장, docs/01-feature-spec.md 1.8, 2026-09-05 신규 —
-- 사용자 요청으로 8.3에서 유보했던 "소셜 세션 전체" 중 일부를 재개). A one-off recruiting post
-- tied to a single existing sketch_route + date/time, NOT a persistent "crew"/club (1.8.3) --
-- see com.dallim.meetup.Meetup for the domain model this backs.

CREATE TABLE run_meetups (
    id                VARCHAR(32) PRIMARY KEY,
    route_id          VARCHAR(32) NOT NULL REFERENCES sketch_routes (id),
    host_user_id      VARCHAR(32) NOT NULL REFERENCES users (id),

    scheduled_at      TIMESTAMPTZ NOT NULL,
    max_participants  INTEGER NOT NULL,             -- 2..20, enforced in MeetupService (14.3)
    description       TEXT,

    -- OPEN | CANCELLED only. "마감"(full)/"종료"(scheduled_at passed) are NOT separate status
    -- values -- they're computed at read time from participant count / scheduled_at vs now(), per
    -- 14.1/14.2 (no batch job keeps this column in sync with the clock).
    status            VARCHAR(16) NOT NULL DEFAULT 'OPEN',

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- GET /routes/{routeId}/meetups -- listed soonest-first, scoped to one route.
CREATE INDEX idx_run_meetups_route_id_scheduled_at ON run_meetups (route_id, scheduled_at ASC);

CREATE TABLE run_meetup_participants (
    meetup_id  VARCHAR(32) NOT NULL REFERENCES run_meetups (id),
    user_id    VARCHAR(32) NOT NULL REFERENCES users (id),
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (meetup_id, user_id)
);

-- "am I in any of these meetups" / participant-count lookups by meetup.
CREATE INDEX idx_run_meetup_participants_user_id ON run_meetup_participants (user_id);
