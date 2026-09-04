# 달림(Dallim) 기능명세서 — MVP1 (핵심 러닝)

> 범위: `달림_화면별_상세기획서_v1.3.md` 중 **MVP1(온보딩 → 큐레이션 코스 → 러닝 → 결과 → 달림북 → 공유카드)** 만 우선 명세화.
> 전체 80여 화면을 한 번에 명세하면 실행이 늦어지므로, 첫 배포 가능 단위만 끊었습니다.
> 스택: **Android(Kotlin/Jetpack Compose)** + **Ktor 3.x + Exposed + PostgreSQL/PostGIS + Redis**

---

## 0. MVP1 성공 기준 (재확인)

> 사용자가 실제 Android 폰으로: 고래 코스를 선택하고 → 밖에서 실제로 달리고 → GPS로 고래 형태를 남기고 → 결과를 저장할 수 있어야 한다.

이번 명세의 화면/API는 전부 이 한 문장을 만족시키기 위해서만 존재합니다. 소셜·수익화·게임요소는 이번 범위에서 제외합니다.

---

## 1. 프론트엔드(Android) 기능명세

### 1.0 전역 내비게이션 구조 (2026-09-01 추가)

> 기존에는 하단 탭바 없이 홈 상단 아이콘(달림북/탐색)으로만 이동하는 구조였으나, 첫 사용자
> 테스트에서 "탐색 구조가 부실해 보인다"는 피드백을 받아 **하단 탭바를 신규로 도입**한다.
> `03-design-system.md`의 그라디언트 적용 범위 표에 "탭바"가 이미 그라디언트 금지 대상으로
> 명시돼 있었던 것으로 미루어, 원래도 탭바 도입이 전제였던 것으로 보인다.

| 탭 | 진입 화면 | 아이콘(비활성/활성) |
|---|---|---|
| 홈 | S-10 | Outlined Home / Filled Home |
| 탐색 | S-11 | Outlined Search / Filled Search |
| 달림북 | S-40 | Outlined MenuBook / Filled MenuBook |
| 마이 | S-42 (신규) | Outlined Person / Filled Person |

- **마이(S-42) 신규 추가 사유**: `GET /users/me`(닉네임/아바타/총 러닝 횟수/총 거리)와
  `POST /auth/logout`이 백엔드에 이미 구현돼 있는데 이걸 보여주거나 호출하는 화면이 하나도
  없어서, **로그인한 뒤 로그아웃할 방법 자체가 앱에 없었다**. 4번째 탭으로 신설한다. 상세는
  아래 1.5절 참고.
- 탭바는 위 4개 최상위 화면(S-10/S-11/S-40/S-42)에서만 보인다. Route 상세(S-16), 저장한
  코스(S-17), 러닝 플로우(S-20~S-26), 온보딩(S-00~S-06)에서는 탭바 없이 기존처럼 push 이동한다.
- 탭 전환은 뒤로가기 스택을 쌓지 않는다 (탭 간 이동 시 `popUpTo(첫 탭) { saveState = true }` +
  `launchSingleTop = true` + `restoreState = true` 패턴 — 각 탭의 스크롤/상태는 유지하되 탭을
  반복 전환해도 back stack이 무한히 늘어나지 않게 한다).
- 홈 화면 상단의 기존 책(달림북)/돋보기(탐색) 아이콘은 하단 탭바와 기능이 중복되므로 제거한다
  (04-ui-guide.md의 "조용하고 절제된 화면" 원칙 — 같은 이동 경로를 두 곳에 두지 않는다).
- 탭바 자체의 시각 규칙은 `04-ui-guide.md` §6 참고.

### 1.1 온보딩 모듈

| 화면 ID | 화면명 | 기능 | 입력 | 출력/화면전환 | 로컬 상태 |
|---|---|---|---|---|---|
| S-00 | 스플래시 | 저장된 토큰 유효성 확인 후 분기 | - | 유효→S-10 / 무효→S-01 | AccessToken(DataStore) |
| S-01 | 가치 제안 캐러셀 | 3장 온보딩 슬라이드, 스킵 가능 | 스와이프/탭 | S-02 | - |
| S-02 | 로그인 | Google/Kakao 소셜 + 일반(이메일) 로그인 3종 | Google ID Token / Kakao Access Token / 이메일+비밀번호 | 서버에 전달 → JWT 수신 | - |
| S-02b | 일반 회원가입 | 이메일·비밀번호 가입 폼 (신규 화면) | 이메일, 비밀번호, 비밀번호 확인 | 가입 성공 → S-03 | - |
| S-03 | 약관 동의 | 필수 약관 3종 + 마케팅 1종 개별 동의 | 체크박스 | 동의 완료 → S-04 | - |
| S-04 | 프로필 설정 | 닉네임 중복확인, 아바타 6종 중 선택, 러닝경험/페이스/성별 선택 | 텍스트+선택 | 서버 저장 → S-05 | - |
| S-05 | 권한 요청 | 위치(포그라운드) 권한 요청 + 사전 설명 다이얼로그 | 시스템 권한 다이얼로그 | 허용/거부 무관 진행 → S-06 | - |
| S-06 | 첫 코스 제안 | 현재 위치 기준 1.5~2.5km 코스 1개 조회·표시 | 현재 위치(FusedLocationProviderClient) | [지금 달리기]→S-20 / [나중에]→S-10 | - |

**핵심 비기능 요구사항**
- 백그라운드 위치 권한은 S-05(온보딩)에서 요청하지 않고, **S-20(러닝 준비)에서 최초 요청**한다 (거부율 최소화).
- 닉네임 중복확인은 300ms 디바운스 후 API 호출.
- 로그인 화면(S-02) 배치 순서: **Kakao(Primary) → Google → 구분선 → [이메일로 시작하기](텍스트 버튼)**. 소셜 로그인을 우선 노출하고 일반 가입은 보조 경로로 둔다.
- 일반 회원가입(S-02b) 검증: 이메일 형식, 비밀번호 8자 이상 + 영문/숫자 조합, 비밀번호 확인 일치. 검증은 클라이언트·서버 양쪽에서 수행.
- 3가지 경로 모두 동일한 온보딩 플로우(S-03~S-06)로 합류한다.

### 1.2 홈 & 탐색 모듈

| 화면 ID | 화면명 | 기능 | 데이터 소스 | 캐싱 정책 |
|---|---|---|---|---|
| S-10 | 홈 | Hero 카드(오늘의 달림), 최근 달림 3개, 저장 코스 리스트 | GET /home | 5분 메모리 캐시 |
| S-11 | 탐색(코스 리스트) | 필터(거리/난이도/신호등/평지) 적용 코스 목록, 무한스크롤 | GET /routes | 페이지당 캐시 없음(실시간) |
| S-16 | Route 상세 | 코스 지도/스펙/러너 GPS 썸네일 | GET /routes/{id} | 없음 |
| S-17 | 저장한 코스 | 내가 저장한 코스 목록 | GET /users/me/saved-routes | 없음 |

**핵심 컴포넌트**
- `RouteThumbnailView`: 서버가 내려주는 GPS LineString(GeoJSON)을 Canvas로 렌더링해 실루엣 썸네일 생성(제네릭 아이콘 금지 원칙 구현)
- Map SDK: 네이버맵 or 카카오맵 SDK(국내 서비스이므로 Google Maps 대비 도로 정밀도 우위)

### 1.3 러닝 모듈 (핵심 중 핵심)

| 화면 ID | 화면명 | 기능 | 안드로이드 구성요소 |
|---|---|---|---|
| S-20 | 러닝 준비 | GPS 신호 강도 체크, 배터리 최적화 안내, 백그라운드 위치 권한 요청, 3초 카운트다운 | `FusedLocationProviderClient`, `PowerManager.isIgnoringBatteryOptimizations` |
| S-21 | Sketch Navigation | 실시간 지도(계획경로/실제궤적), 진행률 링, 거리/시간/페이스, 음성 안내 | `ForegroundService` + `LocationCallback`(1초 간격 요청, 실제 기록은 5~10m 이동 시에만) |
| S-22 | 일시정지/종료 | Pause/Resume/Finish 상태 제어 | Service 내부 상태 머신 |
| S-23 | 코스 이탈 안내 | 계획경로 대비 이격거리 60m↑ 15초 지속 시 트리거 | 클라이언트 측 point-to-line 거리 계산(서버 왕복 없이 즉시 반응) |
| S-24 | 완주 판정 처리 | Room에 쌓인 GPS batch를 서버로 업로드, 판정 결과 폴링/대기 | `Room DB` → `WorkManager`(네트워크 재시도) |
| S-25 | 달림 결과 | GPS 그림 애니메이션, 지표, 액션(공유/저장) | Canvas 애니메이션(Path drawing) |
| S-26 | 공유 카드 편집 | 배경/비율/표시항목 선택 → 로컬 이미지 합성 | `Canvas` → `Bitmap` → `ShareSheet` |

**GPS 기록 파이프라인 (원문 핵심 요구사항 반영)**

```
FusedLocationProviderClient (1~3초 간격 요청)
        ↓
ForegroundService (화면 OFF/백그라운드에서도 유지)
        ↓
Room DB (로컬 우선 저장, 네트워크 상태 무관)
        ↓ (러닝 종료 시)
WorkManager 배치 업로드 (실패 시 지수 백오프 재시도)
        ↓
POST /runs/{runId}/gps-batch
```

**완주 판정 로컬 프리체크(서버 검증 이전 UX용)**
- 이동거리, 경로 커버리지(%), 비정상 속도(>25km/h 지속) 클라이언트에서 1차 계산 → 즉시 애니메이션 표시
- 최종 판정은 서버 재계산 값으로 덮어씀(신뢰 소스는 항상 서버)

### 1.4 달림북 모듈

| 화면 ID | 화면명 | 기능 |
|---|---|---|
| S-40 | 달림북 그리드 | 완주 GPS 그림 2열 그리드 + 빈 슬롯(저장했지만 미완주 코스) |
| S-41 | 작품 상세 | 개별 Run 상세, 리플레이 진입 |

### 1.5 마이(프로필) 모듈 (2026-09-01 신규 — 1.0절 참고)

| 화면 ID | 화면명 | 기능 | 데이터 소스 |
|---|---|---|---|
| S-42 | 마이 | 아바타/닉네임/총 러닝 횟수/총 거리 표시, 로그아웃 | `GET /users/me` |

- **범위**: 조회 + 로그아웃만. 닉네임/아바타 **수정은 이번 범위 아님** —
  `PATCH /users/me` 같은 수정용 API가 SPEC에 없으므로 새 백엔드 작업 없이는 만들지 않는다.
- 로그아웃 버튼 탭 → `POST /auth/logout` 호출(실패해도 클라이언트는 진행 — 서버 세션 폐기는
  best-effort, 로컬 토큰 삭제가 실제 로그아웃의 핵심) → 로컬 토큰(DataStore) 삭제 →
  `S-02`(로그인)로 이동하며 그 아래 전체 백스택(홈/탭 포함)을 비운다.
- `gender`는 이 화면에도, 어떤 화면에도 표시하지 않는다(`GET /users/me` 응답 자체에 없음 —
  CLAUDE.md 규칙 2).

### 1.6 코스 생성 모듈 (2026-09-03 신규 — 사용자 요청, MVP 구분 없이 조기 착수)

| 화면 ID | 화면명 | 기능 | 데이터 소스 |
|---|---|---|---|
| S-43 | 코스 만들기 진입 | 직접 그리기 / AI 자동 생성 중 선택 | - |
| S-44 | 직접 그리기 | 지도 위 드래그로 그림 → 실도로 변환, 다시 그리기 | `POST /routes/draw-convert` |
| S-45 | AI 자동 생성 | 목표 거리(+페이스)로 순환 코스 자동 생성, 다시 생성 | `POST /routes/discovery` |

- 탐색(S-11) 화면의 FloatingActionButton("코스 만들기")에서 진입.
- "저장"은 UI만 존재 — 생성된 코스를 `sketch_routes`에 실제로 저장하는 API는 이번 범위 밖(`docs/02-api-spec.md` 8.3), 탭하면 "저장 기능은 준비 중이에요" 스낵바만 보여준다.
- 2026-09-04 확장: S-44에 "출발점으로 돌아오기" 토글(체크 시에만 `closeLoop: true`로 전송 —
  직선 코스로 뛰고 싶은 사람도 있어 기본값은 꺼짐), S-45에 "꼭 지나갈 장소" 선택(지도 롱프레스로
  최대 3곳 지정 — 각각 마커 탭하면 개별 삭제, `requiredWaypoints`) 추가. S-45에 "목적지 직접
  지정" 모드도 추가 — 켜면 지도에서 도착지를 하나 찍고, 순환 코스 대신 그 지점까지 가는
  point-to-point 코스를 만든다(`mode: "POINT_TO_POINT"`, `endLat`/`endLng`). "목적지 직접 지정"
  모드에서도 "꼭 지나갈 장소"를 함께 켤 수 있다 — 두 옵션이 동시에 화면에 표시되고
  함께 전송된다. 상세는 `docs/02-api-spec.md` 11장.
- 2026-09-04 확장: S-45 "꼭 지나갈 장소" 섹션에 검색창 추가 — 지도 롱프레스 대신(또는 함께)
  장소 이름으로 검색해서 추가할 수 있다. 검색 결과 목록에서 하나를 탭하면 롱프레스로 찍은 것과
  동일하게 처리된다(같은 최대 3곳 상한 공유, 마커 탭으로 개별 삭제도 그대로). point-to-point의
  목적지 검색은 이번 라운드 범위 밖. 상세는 `docs/02-api-spec.md` 12장.
- 상세 API 계약/생성 알고리즘은 `docs/02-api-spec.md` 8장, 11장, 12장 참고.

### 1.7 알림 모듈 (2026-09-03 신규 — 사용자 요청, 1단계: 인앱 알림함)

| 화면 ID | 화면명 | 기능 | 데이터 소스 |
|---|---|---|---|
| S-46 | 알림 목록 | 내 알림 목록(최신순), 읽음 처리 | `GET /notifications`, `POST /notifications/{id}/read` |

- 홈(S-10) 상단에 종 모양 아이콘 + 안 읽은 개수 배지(`GET /notifications/unread-count`) 추가, 탭하면 S-46 진입.
- 1단계는 **인앱 알림함만** — 러닝 완주 시 서버가 알림 1건을 자동 생성한다(`docs/02-api-spec.md` 9.4).
- 2단계(2026-09-04, Firebase 프로젝트 발급 완료): 알림 생성 시 등록된 기기로 FCM 푸시도 함께 발송한다. 앱은 시작 시(또는 로그인 후) FCM 토큰을 발급받아 `POST /users/me/device-tokens`로 등록한다. 상세는 `docs/02-api-spec.md` 10장.
- 상세 API 계약은 `docs/02-api-spec.md` 9장(인앱), 10장(FCM 푸시) 참고.

---

## 2. 백엔드(Ktor) 기능명세

### 2.1 모듈 구성

```
com.dallim
 ├─ plugins     (Ktor 플러그인 설정: Routing, Serialization, Auth, StatusPages, DI)
 ├─ auth        (소셜/이메일 로그인, JWT 발급/갱신, BCrypt)
 ├─ user        (프로필, 저장 코스)
 ├─ route       (SketchRoute, Route 상태 전이)
 ├─ run         (RunRecord, GpsPoint, 완주 판정)
 ├─ discovery   (AI Sketch Discovery — MVP2 예정, MVP1은 스텁)
 └─ common      (GeoJSON 변환, PostGIS 유틸, 공통 응답 래퍼)
```

### 2.1.1 Ktor 기술 선택

| 영역 | 선택 |
|---|---|
| 엔진 | Netty |
| 직렬화 | kotlinx.serialization |
| DB 접근 | **Exposed**(일반 테이블) + **raw SQL**(PostGIS 공간 쿼리 — Exposed가 PostGIS 타입을 네이티브 지원하지 않으므로 `exec`로 직접 작성) |
| 커넥션 풀 | HikariCP |
| 마이그레이션 | Flyway |
| DI | Koin |
| 인증 | ktor-server-auth-jwt |
| 비밀번호 해싱 | BCrypt |
| Redis | Lettuce |

### 2.2 기능별 비즈니스 로직 명세

#### A. 인증(auth) — 3가지 경로
- **Google**: ID Token을 Google 공개키로 서명 검증 → 내부 User 매핑
- **Kakao**: 전달받은 Access Token으로 서버가 `GET https://kapi.kakao.com/v2/user/me` 호출해 검증
- **일반(이메일)**: 회원가입 시 BCrypt 해싱 저장, 로그인 시 해시 비교
  - 비밀번호 정책: 8자 이상 + 영문/숫자 조합
  - 비밀번호는 어떤 응답·로그에도 노출하지 않는다
  - 이메일 인증 메일 발송은 MVP1 범위 밖(가입 즉시 사용 가능), MVP2에서 검토
- 계정 식별: `provider`(GOOGLE/KAKAO/EMAIL) + `providerId`(이메일 가입은 이메일) 조합을 유니크 키로
- JWT: Access Token(2시간) + Refresh Token(30일, Redis 저장해 강제 만료 가능하게)
- 재발급: `/auth/refresh`에서 Redis의 Refresh Token과 대조 후 회전(rotate)

#### B. 프로필(user)
- 닉네임 중복확인: `SELECT EXISTS` 쿼리, 유니크 인덱스로 최종 방어
- 저장 코스: User-SketchRoute 다대다 관계(SavedRoute 엔티티)

#### C. 코스(route)
- **MVP1 범위**: AI Discovery/직접그리기 제외, **운영자가 사전 등록한 큐레이션 코스만 제공**
- **코스 안전성 기준 (2026-09-03 추가, 사용자 요청)**: 좁은 골목·인도 없는 차도·시야 확보 안 되는 구간처럼
  위험하거나 러닝하기 불쾌한 구간은 코스에서 제외하고, **차로와 분리된 인도/공원/하천변 산책로 등
  안전하고 쾌적하게 달릴 수 있는 구간 위주로만 코스를 구성한다.** MVP1 큐레이션 코스(운영자 사전
  등록)는 여전히 등록 시 운영자가 육안으로 판단해 적용한다(자동화 로직 없음).
  **2026-09-04 알고리즘화 완료**: 직접 그리기(8.1)/AI 자동 생성(8.2)이 쓰는 OSRM 라우팅에
  커스텀 `foot` 프로필(`scripts/osrm-profiles/dallim-foot.lua`)을 적용해, 인도·공원·보행자 전용
  도로는 우대하고 인도 없는 대로(간선/큰길)는 기피하도록 도로 유형별 가중치를 차등화했다 — 큰길은
  다리처럼 대안이 없을 때만 사용된다. OSM 태그 기반 1단계 근사치이며(실측 도로 폭 데이터는 아직
  없음), `sidewalk`/`lit` 태그가 있으면 추가로 보정한다. 상세는 `docs/02-api-spec.md` 8.2.
  코스 상세 응답에 이런 안전 관련 메타데이터(예: 인도 여부, 조도)를 노출할지는 아직 미정 — SPEC에
  없으므로 API를 확장하지 않는다.
- Route 상태 전이 규칙(서버 배치로 매일 갱신):
  ```
  DISCOVERY → (완주 1건 이상) → VERIFIED → (완주 N건 이상, 임계치 설정값) → POPULAR
  ```
- 코스 조회 시 사용자 현재 위치 기준 반경 검색: PostGIS `ST_DWithin` 사용
- 코스 상세 응답에는 GPS LineString을 GeoJSON으로 직렬화해 반환(프론트 썸네일 렌더링용)

#### D. 러닝(run) — 가장 중요한 모듈
- **GPS 배치 업로드**: 클라이언트가 러닝 종료 후 GpsPoint 배열 전체를 1회 업로드 (실시간 스트리밍 아님 — 원문 "1초마다 서버로 보내지 않는다" 원칙)
- **완주 판정 알고리즘** (서버 최종 계산, 신뢰 소스)
  1. 이동거리 계산: 연속 GpsPoint 간 Haversine 거리 합산
  2. Route Coverage: 계획 LineString을 N개 세그먼트로 나누고, 각 세그먼트 반경 20m 이내 GpsPoint 존재 여부로 커버리지 % 산출
  3. 비정상 속도 검사: 두 포인트 간 속도 25km/h 초과 구간 비율 계산 → 임계치 초과 시 `UNDER_REVIEW`
  4. Sketch Match: 실제 궤적과 계획 경로의 Fréchet 거리 기반 유사도 점수(0~100)
  5. 최종 상태 판정:
     ```
     Coverage ≥ 90% AND 비정상속도 없음        → COMPLETED
     Coverage 50~90%                          → PARTIAL
     Coverage < 50%                            → ABORTED
     비정상속도 구간 존재                        → UNDER_REVIEW (수동/추가검토, 배지·랭킹 제외)
     ```
- 판정 결과에 따라 Route 완주자 수(`finisherCount`) 원자적 증가(Redis `INCR` 후 배치로 DB 동기화 — 동시성 대응)

#### E. 공통(common)
- GeoJSON ↔ PostGIS Geometry 변환 유틸(Jackson 커스텀 Serializer/Deserializer)
- Haversine 거리 계산, Fréchet 거리 계산은 별도 유틸 클래스로 분리(추후 discovery 모듈에서도 재사용)

### 2.3 배치/스케줄러

| 배치명 | 주기 | 역할 |
|---|---|---|
| RouteStatusUpdateJob | 매일 03:00 | 완주자 수 기준 Route 상태(DISCOVERY→VERIFIED→POPULAR) 갱신 |
| GpsRetryQueueJob | 5분 | 업로드 실패로 대기 중인 GPS 배치 재처리(있다면) |

> Ktor에는 Spring `@Scheduled` 같은 내장 스케줄러가 없다. `kotlinx.coroutines`의 주기 실행 코루틴 또는 별도 스케줄러 라이브러리를 사용한다.

### 2.4 비기능 요구사항 (백엔드)

- 위치 데이터는 PostGIS `geography` 타입 저장(구면 거리 계산 정확도). Exposed로 표현이 어려운 공간 쿼리는 raw SQL로 작성하고 해당 함수에 주석으로 쿼리 의도를 남긴다
- GPS Raw Point는 러닝 1건당 수백~수천 개 → **Run 완료 후 압축(Douglas-Peucker 알고리즘으로 포인트 수 축소)하여 저장**, 원본은 일정 기간 후 파기 정책 고려
- 완주 판정 로직은 서버가 유일한 신뢰 소스 — 클라이언트 계산값은 UX 프리뷰 용도로만 사용하고 서버 응답으로 항상 덮어씀
