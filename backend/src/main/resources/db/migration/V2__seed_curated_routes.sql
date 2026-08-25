-- Dev/test seed data: curated SketchRoutes for MVP1's routes domain (docs/01-feature-spec.md
-- 2.2.C — MVP1 serves only operator-curated routes, no AI Discovery/draw-convert yet).
-- Five routes across the 1순위 벨트 (안양·군포·의왕·과천·성남 분당/판교) with plausible
-- real-road-ish coordinates (not surveyed GPS tracks — good enough for local dev/QA fixtures).
--
-- rt_001 intentionally mirrors the docs/02-api-spec.md 4장 GET /routes/{id} example values
-- (name/emoji/distanceKm/finisherCount/etc.) so that example can be sanity-checked against a
-- real response.

INSERT INTO sketch_routes (
    id, name, emoji, path, distance_km, estimated_minutes, difficulty, status,
    finisher_count, traffic_light_count, elevation_gain_m, repeat_segment_percent, runability
) VALUES
(
    'rt_001', '고래', '🐳',
    -- 안양천변 (Anyang)
    ST_SetSRID(ST_MakeLine(ARRAY[
        ST_MakePoint(126.9235, 37.3905),
        ST_MakePoint(126.9268, 37.3928),
        ST_MakePoint(126.9301, 37.3951),
        ST_MakePoint(126.9330, 37.3975),
        ST_MakePoint(126.9310, 37.4001)
    ]), 4326)::geography,
    5.1, 36, 'EASY', 'POPULAR', 148, 4, 32, 5, 0.87
),
(
    'rt_002', '물고기', '🐟',
    -- 산본천변 (Gunpo)
    ST_SetSRID(ST_MakeLine(ARRAY[
        ST_MakePoint(126.9300, 37.3580),
        ST_MakePoint(126.9330, 37.3605),
        ST_MakePoint(126.9360, 37.3630),
        ST_MakePoint(126.9390, 37.3655),
        ST_MakePoint(126.9370, 37.3680)
    ]), 4326)::geography,
    4.3, 30, 'EASY', 'VERIFIED', 12, 3, 18, 8, 0.81
),
(
    'rt_003', '나비', '🦋',
    -- 백운호수 둘레 (Uiwang)
    ST_SetSRID(ST_MakeLine(ARRAY[
        ST_MakePoint(126.9600, 37.3190),
        ST_MakePoint(126.9635, 37.3215),
        ST_MakePoint(126.9670, 37.3240),
        ST_MakePoint(126.9705, 37.3265),
        ST_MakePoint(126.9680, 37.3290)
    ]), 4326)::geography,
    6.4, 44, 'MEDIUM', 'VERIFIED', 6, 2, 55, 3, 0.78
),
(
    'rt_004', '토끼', '🐰',
    -- 서울대공원 인근 (Gwacheon)
    ST_SetSRID(ST_MakeLine(ARRAY[
        ST_MakePoint(127.0020, 37.4230),
        ST_MakePoint(127.0055, 37.4255),
        ST_MakePoint(127.0090, 37.4280),
        ST_MakePoint(127.0125, 37.4305),
        ST_MakePoint(127.0100, 37.4330)
    ]), 4326)::geography,
    3.8, 26, 'EASY', 'DISCOVERY', 0, 1, 40, 10, 0.83
),
(
    'rt_005', '별', '⭐',
    -- 분당/판교 (Seongnam)
    ST_SetSRID(ST_MakeLine(ARRAY[
        ST_MakePoint(127.1050, 37.3900),
        ST_MakePoint(127.1085, 37.3925),
        ST_MakePoint(127.1120, 37.3950),
        ST_MakePoint(127.1155, 37.3975),
        ST_MakePoint(127.1130, 37.4000)
    ]), 4326)::geography,
    5.6, 39, 'MEDIUM', 'DISCOVERY', 0, 6, 20, 4, 0.75
);
