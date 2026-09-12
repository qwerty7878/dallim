-- Dev/QA seed data for 대회 코스 미리 달리기(S-85) — docs/02-api-spec.md 16.6(신규).
--
-- 실제 서울 하프 마라톤(rce_002, V12__seed_races.sql)의 공식 코스를 정확히 재현하지 않는다 —
-- "그럴듯한 데모용" 짧은(4km대) LineString이다(V2__seed_curated_routes.sql과 동일한 원칙).
-- 21km 하프 트랙 전체를 손으로 그릴 필요가 없어 데모 목적의 몇 km짜리로 축소했다(작업
-- 브리핑 지시).
--
-- 잠실종합운동장(rce_002의 location) 인근에서 시작해 탄천을 따라 북동쪽으로 올라가는
-- 4.2km짜리 데모 코스를 3개 구간(각 1.4km)으로 나눈다. 구간용 SketchRoute는 모두
-- is_preview_segment = TRUE라서 GET /routes 목록에는 뜨지 않지만, GET /routes/{routeId}·
-- POST /runs로는 평범한 코스처럼 그대로 시작할 수 있다.

-- 공식 코스 전체 (참고/지도 표시용 — 이 자체를 GET /routes 목록에 노출해도 무방한 일반
-- SketchRoute라 is_preview_segment는 FALSE로 둔다).
INSERT INTO sketch_routes (
    id, name, emoji, path, distance_km, estimated_minutes, difficulty, status,
    finisher_count, traffic_light_count, elevation_gain_m, repeat_segment_percent, runability,
    is_preview_segment
) VALUES (
    'rt_race002_course', '서울 하프 코스(데모)', '🏅',
    ST_SetSRID(ST_MakeLine(ARRAY[
        ST_MakePoint(127.0730, 37.5145),
        ST_MakePoint(127.0805, 37.5185),
        ST_MakePoint(127.0880, 37.5225),
        ST_MakePoint(127.0955, 37.5265),
        ST_MakePoint(127.1030, 37.5305),
        ST_MakePoint(127.1105, 37.5345),
        ST_MakePoint(127.1180, 37.5385)
    ]), 4326)::geography,
    4.2, 28, 'MEDIUM', 'VERIFIED', 0, 2, 15, 0, 0.8,
    FALSE
);

-- 구간 1: 출발~1.4km
INSERT INTO sketch_routes (
    id, name, emoji, path, distance_km, estimated_minutes, difficulty, status,
    finisher_count, traffic_light_count, elevation_gain_m, repeat_segment_percent, runability,
    is_preview_segment
) VALUES (
    'rt_race002_seg1', '서울 하프 코스 - 출발~1.4km', '🏅',
    ST_SetSRID(ST_MakeLine(ARRAY[
        ST_MakePoint(127.0730, 37.5145),
        ST_MakePoint(127.0805, 37.5185),
        ST_MakePoint(127.0880, 37.5225)
    ]), 4326)::geography,
    1.4, 9, 'EASY', 'DISCOVERY', 0, 1, 5, 0, 0.8,
    TRUE
);

-- 구간 2: 1.4km~2.8km
INSERT INTO sketch_routes (
    id, name, emoji, path, distance_km, estimated_minutes, difficulty, status,
    finisher_count, traffic_light_count, elevation_gain_m, repeat_segment_percent, runability,
    is_preview_segment
) VALUES (
    'rt_race002_seg2', '서울 하프 코스 - 1.4km~2.8km', '🏅',
    ST_SetSRID(ST_MakeLine(ARRAY[
        ST_MakePoint(127.0880, 37.5225),
        ST_MakePoint(127.0955, 37.5265),
        ST_MakePoint(127.1030, 37.5305)
    ]), 4326)::geography,
    1.4, 9, 'EASY', 'DISCOVERY', 0, 1, 5, 0, 0.8,
    TRUE
);

-- 구간 3: 2.8km~4.2km(피니시)
INSERT INTO sketch_routes (
    id, name, emoji, path, distance_km, estimated_minutes, difficulty, status,
    finisher_count, traffic_light_count, elevation_gain_m, repeat_segment_percent, runability,
    is_preview_segment
) VALUES (
    'rt_race002_seg3', '서울 하프 코스 - 2.8km~4.2km(피니시)', '🏅',
    ST_SetSRID(ST_MakeLine(ARRAY[
        ST_MakePoint(127.1030, 37.5305),
        ST_MakePoint(127.1105, 37.5345),
        ST_MakePoint(127.1180, 37.5385)
    ]), 4326)::geography,
    1.4, 9, 'EASY', 'DISCOVERY', 0, 1, 5, 0, 0.8,
    TRUE
);

INSERT INTO race_course_segments (id, race_id, route_id, label, order_index) VALUES
    ('csg_race002_1', 'rce_002', 'rt_race002_seg1', '출발~1.4km', 1),
    ('csg_race002_2', 'rce_002', 'rt_race002_seg2', '1.4km~2.8km', 2),
    ('csg_race002_3', 'rce_002', 'rt_race002_seg3', '2.8km~4.2km(피니시)', 3);

UPDATE races SET course_route_id = 'rt_race002_course' WHERE id = 'rce_002';
