-- Dev/QA seed data for the race calendar (docs/02-api-spec.md 16장, V11__race_calendar.sql).
--
-- 실제 특정 대회의 정확한 날짜·참가비 등 사실 정보를 단정적으로 재현하지 않는다 — 전부
-- 일반적인 이름("서울 하프 마라톤", "탄천 10K 러닝 페스티벌" 등)과 미래의 그럴듯한 날짜를
-- 쓴 UI 검증용 예시 데이터다(작업 브리핑 지시, 법무 표기 원칙과도 맞물림).
--
-- 접수 상태가 UPCOMING/OPEN/CLOSED 세 가지 다 나오도록 등록 시작/마감을 오늘(2026-09-07
-- 기준 개발 시점) 전후로 섞었고, 지역(서울/성남/수원/안양/과천)과 종목(5K/10K/하프/풀/
-- 울트라/트레일)도 다양하게 섞었다.

-- rce_001: 성남, OPEN (등록 2026-08-20 ~ 2026-09-25)
INSERT INTO races (id, name, region, location, race_date, registration_start, registration_end, organizer, souvenir)
VALUES ('rce_001', '탄천 10K 러닝 페스티벌', '성남', '탄천종합운동장', '2026-10-05T09:00:00Z', '2026-08-20T00:00:00Z', '2026-09-25T23:59:59Z', '성남시체육회', '완주 메달, 기능성 티셔츠');

INSERT INTO race_category_options (id, race_id, category, distance_km, fee_krw, capacity, cutoff_minutes) VALUES
('rcc_001a', 'rce_001', 'FIVE_K', 5.0, 20000, 1000, NULL),
('rcc_001b', 'rce_001', 'TEN_K', 10.0, 25000, 1000, 90);

-- rce_002: 서울, OPEN (등록 2026-09-01 ~ 2026-09-20)
INSERT INTO races (id, name, region, location, race_date, registration_start, registration_end, organizer, souvenir)
VALUES ('rce_002', '서울 하프 마라톤', '서울', '잠실종합운동장', '2026-11-01T08:00:00Z', '2026-09-01T00:00:00Z', '2026-09-20T23:59:59Z', '서울러닝협회', '완주 메달');

INSERT INTO race_category_options (id, race_id, category, distance_km, fee_krw, capacity, cutoff_minutes) VALUES
('rcc_002a', 'rce_002', 'FIVE_K', 5.0, 20000, 500, NULL),
('rcc_002b', 'rce_002', 'TEN_K', 10.0, 25000, 500, 90),
('rcc_002c', 'rce_002', 'HALF', 21.0975, 35000, 1000, 180);

-- rce_003: 수원, UPCOMING (등록 2026-10-01 ~ 2026-10-20, 아직 시작 전)
INSERT INTO races (id, name, region, location, race_date, registration_start, registration_end, organizer, souvenir)
VALUES ('rce_003', '수원 화성 하프마라톤', '수원', '수원종합운동장', '2026-11-15T08:00:00Z', '2026-10-01T00:00:00Z', '2026-10-20T23:59:59Z', '수원시체육회', NULL);

INSERT INTO race_category_options (id, race_id, category, distance_km, fee_krw, capacity, cutoff_minutes) VALUES
('rcc_003a', 'rce_003', 'HALF', 21.0975, 40000, 800, 190),
('rcc_003b', 'rce_003', 'FULL', 42.195, 50000, 500, 360);

-- rce_004: 성남, OPEN (등록 2026-09-01 ~ 2026-09-30)
INSERT INTO races (id, name, region, location, race_date, registration_start, registration_end, organizer, souvenir)
VALUES ('rce_004', '판교 테크노밸리 나이트런', '성남', '판교테크노밸리', '2026-10-20T19:00:00Z', '2026-09-01T00:00:00Z', '2026-09-30T23:59:59Z', '판교스타트업포럼', '야광 러닝 조끼');

INSERT INTO race_category_options (id, race_id, category, distance_km, fee_krw, capacity, cutoff_minutes) VALUES
('rcc_004a', 'rce_004', 'FIVE_K', 5.0, 15000, 600, NULL),
('rcc_004b', 'rce_004', 'TEN_K', 10.0, 20000, 600, 80);

-- rce_005: 수원, CLOSED (등록 2026-07-01 ~ 2026-08-10, 이미 마감)
INSERT INTO races (id, name, region, location, race_date, registration_start, registration_end, organizer, souvenir)
VALUES ('rce_005', '수원 시민 마라톤', '수원', '수원월드컵경기장', '2026-09-20T08:00:00Z', '2026-07-01T00:00:00Z', '2026-08-10T23:59:59Z', '수원시', '완주 메달, 수건');

INSERT INTO race_category_options (id, race_id, category, distance_km, fee_krw, capacity, cutoff_minutes) VALUES
('rcc_005a', 'rce_005', 'TEN_K', 10.0, 20000, 700, 100),
('rcc_005b', 'rce_005', 'HALF', 21.0975, 30000, 700, 180),
('rcc_005c', 'rce_005', 'FULL', 42.195, 40000, 400, 360);

-- rce_006: 서울, UPCOMING (등록 2026-11-01 ~ 2026-11-20, 아직 시작 전)
INSERT INTO races (id, name, region, location, race_date, registration_start, registration_end, organizer, souvenir)
VALUES ('rce_006', '서울 신년 마라톤', '서울', '광화문광장', '2027-01-10T08:00:00Z', '2026-11-01T00:00:00Z', '2026-11-20T23:59:59Z', '서울특별시', '새해 기념 메달');

INSERT INTO race_category_options (id, race_id, category, distance_km, fee_krw, capacity, cutoff_minutes) VALUES
('rcc_006a', 'rce_006', 'HALF', 21.0975, 35000, 1500, 180),
('rcc_006b', 'rce_006', 'FULL', 42.195, 45000, 1000, 360);

-- rce_007: 안양, OPEN (등록 2026-08-15 ~ 2026-09-15)
INSERT INTO races (id, name, region, location, race_date, registration_start, registration_end, organizer, souvenir)
VALUES ('rce_007', '안양천 러닝 페스티벌', '안양', '안양천 시민공원', '2026-10-01T09:00:00Z', '2026-08-15T00:00:00Z', '2026-09-15T23:59:59Z', '안양시체육회', '기능성 양말');

INSERT INTO race_category_options (id, race_id, category, distance_km, fee_krw, capacity, cutoff_minutes) VALUES
('rcc_007a', 'rce_007', 'FIVE_K', 5.0, 15000, 800, NULL),
('rcc_007b', 'rce_007', 'TEN_K', 10.0, 18000, 800, 85),
('rcc_007c', 'rce_007', 'HALF', 21.0975, 28000, 500, 180);

-- rce_008: 과천, CLOSED (등록 2026-06-01 ~ 2026-07-15, 이미 마감 — 트레일/울트라)
INSERT INTO races (id, name, region, location, race_date, registration_start, registration_end, organizer, souvenir)
VALUES ('rce_008', '과천 사슴벌레 트레일런', '과천', '서울대공원 산림욕장 입구', '2026-09-10T07:00:00Z', '2026-06-01T00:00:00Z', '2026-07-15T23:59:59Z', '과천시', NULL);

INSERT INTO race_category_options (id, race_id, category, distance_km, fee_krw, capacity, cutoff_minutes) VALUES
('rcc_008a', 'rce_008', 'TRAIL', 15.0, 30000, 300, 240),
('rcc_008b', 'rce_008', 'ULTRA', 50.0, 60000, 150, 600);
