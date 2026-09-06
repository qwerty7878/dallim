-- 러닝 걸음수(step count) 수집 — docs/달림_화면별_상세기획서_v1.3.md PART 4.1 "부정행위 방지"
-- 근거("걸음 수 센서 병행 수집은 MVP1에 넣으세요. 나중에 넣으면 과거 데이터가 없어 판정 불가").
--
-- 이번 라운드는 저장/노출만 한다 — RunJudgementService의 COMPLETED/PARTIAL/ABORTED/UNDER_REVIEW
-- 판정 로직에는 전혀 영향을 주지 않는다. "걸음 수 대비 이동 거리 불일치" 실제 체크는 데이터가
-- 쌓인 뒤 별도 라운드에서 붙인다.

ALTER TABLE run_records ADD COLUMN step_count INTEGER;
