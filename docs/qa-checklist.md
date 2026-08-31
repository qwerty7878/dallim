# QA 체크리스트

이 문서는 QA 라운드마다 "이번에 자동화로 검증한 것 / 자동화가 불가능해서 사람이 직접 확인해야
하는 것 / 발견된 버그·튜닝 필요 항목"을 누적 기록한다. 최신 라운드가 위에 온다.

---

## 라운드 1 — 러닝(run) 도메인 완주 판정 로직 (2026-08-31)

대상: `RunJudgementService`, `GeoMath`, `FrechetDistance`, `DouglasPeucker`,
`RunRoutes`/`RunService`/`RunRepository`의 5개 엔드포인트, `FinisherCountSync`.

### 자동화된 테스트

| 파일 | 유형 | 개수 | 결과 |
|---|---|---|---|
| `backend/src/test/kotlin/com/dallim/run/RunJudgementServiceTest.kt` | 단위 | 20 | 통과 |
| `backend/src/test/kotlin/com/dallim/common/GeoMathTest.kt` | 단위 | 7 | 통과 |
| `backend/src/test/kotlin/com/dallim/common/FrechetDistanceTest.kt` | 단위 | 4 | 통과 |
| `backend/src/test/kotlin/com/dallim/common/DouglasPeuckerTest.kt` | 단위 | 4 | 통과 |
| `backend/src/test/kotlin/com/dallim/integration/RunFlowIntegrationTest.kt` | 통합(실DB) | 13 | 통과 |

`./gradlew test` 로 실행 확인 완료 (로컬 docker-compose Postgres/PostGIS + Redis,
`scripts/dev-db.sh up` 기준). 통합 테스트는 실제 DB/Redis에 대해 실행되며, 반복 실행해도
(`--rerun`) 통과하는 것을 확인함 — 고유 이메일 생성 등으로 기존 데이터와 충돌하지 않게 작성.

**GPS fixture**: `backend/src/test/resources/gps-fixtures/{completed,partial,aborted,under_review}_run.json`.
모두 `rt_001`(고래, 안양천변 — `V2__seed_curated_routes.sql`)의 실제 계획 경로 좌표를 그대로
사용해, 단위 테스트와 통합 테스트(실제 시드 라우트로 API 호출)가 같은 지리 데이터를 공유하도록
만듦. 4개 시나리오 모두 커버리지 목표 구간에 정확히 맞춰 생성 확인:

- `completed_run`: 커버리지 100%, 이상속도 없음 → `COMPLETED`
- `partial_run`: 경로 62%만 추종 후 이탈 → 커버리지 63% → `PARTIAL`
- `aborted_run`: 경로 15%만 추종 후 이탈 → 커버리지 17% → `ABORTED`
- `under_review_run`: 커버리지 100%지만 ~144km/h 순간이동 15회(비정상속도 비율 ~8%) 포함 →
  `UNDER_REVIEW` (커버리지가 완주 기준을 넘어도 속도 이상이 우선한다는 회귀 테스트)

경계값(90%/50% 정확히)과 비정상속도 비율 경계(정확히 5% vs 5% 초과)도 별도 합성 데이터로 검증.

### 발견된 버그 / 이슈

1. **[수정 완료] `sketchMatchPercent`가 실제 커브 완성도와 무관하게 0점에 가깝게 나옴 (영향도 높음)**
   `RunJudgementServiceTest.kt`의 `sketchMatchPercent - BUG - ...` 테스트로 재현/고정해둠.
   원인: discrete Fréchet distance는 "정점 대 정점" 대응 지표인데, `sketchMatchPercent`는
   `planned`(계획 경로, 시드 데이터 기준 정점 5개, 정점 간 300~400m)와 `actual`(실제 GPS
   트랙, 수백 개 점)을 밀도 보정 없이 그대로 넣는다. 두 곡선의 점 밀도 차이가 크면, 계획
   경로가 한 정점에 "묶여" 있는 동안 실제 트랙이 그 정점에서 멀리 이동해버려 leash 길이가
   실제 경로 이탈과 무관하게 계획 경로 최장 세그먼트의 절반 수준까지 부풀어 오른다.
   실측: `completed_run` fixture(경로를 오차 거의 없이 따라간 트랙)로 계산한 Fréchet
   거리가 약 192m → `SKETCH_MATCH_ZERO_METERS`(150m) 초과 → `sketchMatchPercent = 0`.
   같은 트랙에서 `planned`를 `actual`과 비슷한 점 개수로 리샘플링만 하면 Fréchet 거리가
   약 3.6m로 떨어짐(→ 98점대) — 즉 알고리즘이 아니라 "입력 준비" 단계의 버그.
   **제안**: `FrechetDistance.discreteMeters` 호출 전에 `planned`/`actual` 중 더 성긴 쪽을
   `GeoMath.resample`로 다른 쪽과 비슷한 밀도까지 올려서 넣을 것 (현재는 더 긴 쪽을
   `FRECHET_MAX_POINTS`로 자르기만 함). `POST /runs/{id}/finish`, `GET /runs/{id}` 응답의
   `sketchMatchPercent` 필드가 실사용자에게 그대로 노출되므로 우선순위 높음.

   **수정 완료 (2026-08-31, backend-dev)**: `RunJudgementService.sketchMatchPercent`에서
   `FRECHET_MAX_POINTS`로 각 변을 독립적으로 캡한 뒤, 두 변 중 점 개수가 적은 쪽을
   `GeoMath.resample`로 많은 쪽의 점 개수까지 선형보간 리샘플링하도록 수정
   (`backend/src/main/kotlin/com/dallim/run/RunJudgementService.kt`). 새 유틸 파일은
   추가하지 않음 — `GeoMath.resample`이 이미 호(arc-length) 기준 선형보간 리샘플링을
   임의의 점 개수(업샘플링 포함)로 지원하는 순수 함수라 그대로 재사용. `completed_run`
   fixture로 실측 확인: Fréchet 거리가 리샘플링 전 192.04m → 리샘플링 후 3.61m로 감소,
   `sketchMatchPercent`는 0점 → 98점으로 정상화됨(qa-engineer가 보고한 ~192m → ~3.6m와
   일치). `RunJudgementServiceTest.kt`의 "BUG" 문서화 테스트 2개를 실제 수정 후 동작을
   검증하는 테스트로 갱신함(`sketchMatchPercent - a dead-on-course trace scores near 100
   despite a sparse planned route`, `sketchMatchPercent - pre-resampling the planned route to
   actual's density gives the same high score`). `SKETCH_MATCH_ZERO_METERS`(150m) 재보정은
   여전히 별도 항목(아래 "SPEC에 명시 안 돼 있어..." 섹션)으로 남겨둠 — 이번 수정 범위 아님.

2. **[수정 완료] `abnormalSpeedRatio`가 1초 미만 간격의 GPS 쌍을 통째로 무시함**
   `Duration.between(prev.timestamp, curr.timestamp).seconds`는 정수 초만 반환(나노초
   버림)한다. 두 점의 간격이 1초 미만이면 `seconds == 0`이 되어 `if (seconds <= 0) continue`
   에 걸려 분자(이상속도 카운트)와 분모(전체 샘플 수) 모두에서 완전히 제외된다. 현재 시드
   데이터/앱 설계상 GPS 배치 간격이 초 단위라 당장 문제를 일으키진 않지만, 클라이언트가
   1Hz보다 촘촘히 포인트를 보내는 경우(고정밀 모드 등) 그 구간은 이상속도 판정에서 전혀
   반영되지 않는다. `Duration.between(...).toMillis() / 1000.0` 등 소수 초 단위로 바꾸는
   것을 권장. (`RunJudgementServiceTest`의 "exactly at the 5pct threshold" 테스트 주석에
   재현 상황을 남겨둠.)

   **수정 완료 (2026-08-31, backend-dev)**: `RunJudgementService.abnormalSpeedRatio`에서
   `Duration.between(prev.timestamp, curr.timestamp).seconds`(정수 초, 나노초 버림) 대신
   `.toMillis() / 1000.0`(밀리초 정밀도의 Double 초)로 변경
   (`backend/src/main/kotlin/com/dallim/run/RunJudgementService.kt`). 기존 "exactly at the
   5pct threshold" 테스트는 애초에 15m/leg로 각 구간의 raw duration이 1초 이상이 되도록
   설계돼 있어 이 버그를 우회했던 것이라 수정 전/후 동일하게 통과함(갱신 불필요). 대신
   `RunJudgementServiceTest.kt`에 1초 미만 간격 쌍을 직접 재현하는 새 테스트
   (`abnormalSpeedRatio - millisecond precision counts sub-1-second GPS pairs instead of
   dropping them`)를 추가함 — 수정 전 코드로 되돌려 이 테스트가 실제로 실패하는 것까지
   확인한 뒤 수정 코드로 복원함(0.3초 간격 60km/h 이상속도 구간이 수정 전엔 분자/분모에서
   완전히 누락돼 ratio=0.0으로 나오던 것이, 수정 후엔 ratio=0.5로 정상 반영되고
   `hasAbnormalSpeed`가 true로 뒤집힘).

3. **[리소스 누수] `Application.module()`이 시작한 `HikariDataSource`/Redis 연결에
   종료 훅이 없음** — 이번 통합 테스트를 작성하며 발견. `module()`은 `Databases.dataSource()`로
   커넥션 풀을 만들고 `configureDependencyInjection`에 등록만 할 뿐, `environment.monitor`의
   `ApplicationStopping` 등 종료 이벤트에 `dataSource.close()` / Redis 연결 종료를 구독하지
   않는다. 단일 장수 프로세스로 운영되는 한 문제되지 않지만, 무중단 재배포·리로드 상황이나
   테스트처럼 애플리케이션을 반복 기동하는 환경에서는 커넥션이 누적되어 결국
   `HikariPool$PoolInitializationException`(too many clients)으로 이어진다. 실제로 이번
   통합 테스트를 처음 만들 때 이 문제로 뒤쪽 테스트들이 전부 실패했었고, 우회책으로
   `application-test.conf`에서 `maxPoolSize=2`로 낮춰 회피함 — **우회이지 근본 수정 아님**.
   운영 환경에서도 정상 종료 훅을 추가하는 것을 권장.

### SPEC에 명시 안 돼 있어 backend-dev가 임의로 정한 값 (실측 데이터 튜닝 필요)

`RunJudgementService.Companion`에 문서화돼 있음. SPEC(`docs/01-feature-spec.md` 2.2.D)은
방향만 정하고 정확한 수치는 비워둔 부분들이라, 실제 사용자 GPS 데이터를 모아본 뒤
재검토가 필요하다:

- `METERS_PER_COVERAGE_SEGMENT = 15.0` (커버리지 체크포인트 밀도), `MIN/MAX_COVERAGE_SEGMENTS = 10/200`
- `COVERAGE_RADIUS_METERS = 20.0` — SPEC 고정값("반경 20m")이라 튜닝 대상 아님, 참고로 남김
- `ABNORMAL_SPEED_RATIO_THRESHOLD = 0.05` (5%) — 너무 낮으면 GPS 노이즈에 오탐, 너무 높으면
  부정행위 구간이 짧을 때 놓침. 실측 GPS 노이즈 분포를 보고 조정 필요
- `SKETCH_MATCH_ZERO_METERS = 150.0` — 위 버그 #1 수정 후 재보정 필요 (현재 값은 밀도 불일치
  버그가 있는 상태에서 정해진 값이라 그대로 신뢰하기 어려움)

### 이번 라운드에서 제외한 항목 (SPEC엔 있으나 아직 미구현)

- `POST /users/me/profile`, `GET /users/me`, `GET /users/nickname-check`,
  `GET /users/me/runs` (달림북) — `plugins/Routing.kt`에 아직 마운트되지 않음. 태스크에서
  요청한 "토큰 하나로 auth → profile → routes → run → 달림북" 전 구간 통합 테스트는 이
  4개 API가 없어 끝까지 이어서 검증할 수 없었음. 대신 `/auth/signup`으로 토큰만 발급받아
  `GET /routes → POST /runs → gps-batch → finish → GET /runs/{id}` 구간만 검증함.
  이 4개 API가 붙는 다음 라운드에서 전체 플로우 통합 테스트를 이어서 작성해야 함.
- 닉네임 중복(`NICKNAME_TAKEN`) 케이스도 같은 이유로 검증 불가 — 대신 이미 구현된
  이메일 중복 가입(`EMAIL_ALREADY_EXISTS`, 409)으로 대체 검증함.

### 수동 QA 체크리스트 (자동화 불가 — 사람이 직접 확인)

**Android / 실기기 필요**
- [ ] 화면을 끄고 5분 이상 방치했을 때 GPS(Foreground Service) 기록이 끊기지 않는지
- [ ] 배터리 최적화(Doze 모드) 예외 설정이 없을 때도 위치 기록이 유지되는지, 또는 사용자에게
      예외 설정을 안내하는 온보딩이 뜨는지
- [ ] 앱이 백그라운드에서 시스템에 의해 강제 종료(kill)된 뒤 재실행 시 진행 중이던 러닝
      기록이 얼마나 유실되는지 (Room에 로컬 저장된 부분까지는 배치 업로드로 복구되는지)
- [ ] 실제 GPS 신호(터널/고층빌딩 밀집 지역 등 신호 약한 구간)에서 커버리지/이상속도
      판정이 체감상 합리적인지 — 이번 라운드 테스트는 모두 "깨끗한" 합성 GPS라 실측 노이즈
      환경에서의 임계값 재검증 필요 (위 튜닝 필요 항목과 연결)
- [ ] Google/Kakao 실 계정으로 로그인 → 러닝 시작 → 종료까지 실제 소셜 토큰으로 E2E 확인
      (이번 통합 테스트는 이메일/비밀번호 가입만 사용 — Google/Kakao는 외부 토큰 검증이
      필요해 자동화 테스트에서 실제 호출을 걸지 않음)

**서버 운영 관련**
- [ ] `Databases.migrate()`가 운영 DB에 처음 적용될 때 PostGIS 확장 설치 권한 등 문제 없는지
      실제 운영 환경(AWS EC2)에서 1회 수동 확인
- [ ] Redis 장애/재시작 시 `FinisherCountSync`의 dirty-routes 집계가 유실 없이 복구되는지
      (현재 구현은 Redis가 유일한 중간 저장소라 Redis 자체가 죽으면 미반영분 손실 가능성 있음 —
      운영 배포 전 장애 시나리오 리허설 권장)
- [ ] 위 "튜닝 필요 값" 4가지를 실제 사용자 러닝 데이터 100건 이상 모은 뒤 분포를 보고 재조정

---
