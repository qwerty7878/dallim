# 달림(Dallim) API 명세서 — MVP1

> Base URL: `https://api.dallim.app/v1` (예시)
> 서버: **Ktor 3.x** (kotlinx.serialization 기준 JSON)
> 인증: `Authorization: Bearer {accessToken}` (JWT)
> 응답 포맷: JSON, 공통 에러 포맷은 하단 참조

---

## 0. 공통 규칙

### 공통 응답 래퍼
```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

### 공통 에러 포맷
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ROUTE_NOT_FOUND",
    "message": "코스를 찾을 수 없습니다."
  }
}
```

### 인증 방식
- Access Token: JWT, 만료 2시간
- Refresh Token: 만료 30일, Redis에 저장(회전 방식 — 재발급 시 기존 토큰 즉시 폐기)
- 인증 필요 API는 표에 🔒 표시

---

## 1. 인증(auth)

> 로그인 3종 지원: **Google, Kakao, 일반(이메일+비밀번호)**.
> 세 경로 모두 **응답 스키마를 동일하게 통일**해서 프론트가 분기 없이 하나의 로직으로 처리하도록 함.
> `provider` 값: `GOOGLE` | `KAKAO` | `EMAIL`

### `POST /auth/google`
Google ID Token으로 로그인/회원가입

**Request**
```json
{ "idToken": "eyJhbGciOi..." }
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "isNewUser": true,
    "userId": "usr_8f2a",
    "provider": "GOOGLE"
  }
}
```

**Error**
- `401 INVALID_GOOGLE_TOKEN` — 구글 토큰 검증 실패

---

### `POST /auth/kakao`
Kakao Access Token으로 로그인/회원가입

**Request**
```json
{ "kakaoAccessToken": "abcd1234..." }
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "isNewUser": false,
    "userId": "usr_8f2a",
    "provider": "KAKAO"
  }
}
```
> 서버는 전달받은 Kakao Access Token으로 카카오 사용자 정보 API(`GET https://kapi.kakao.com/v2/user/me`)를 서버 사이드에서 호출해 검증 후 내부 계정과 매핑.

**Error**
- `401 INVALID_KAKAO_TOKEN` — 카카오 토큰 검증 실패

---

### `POST /auth/signup`
일반 회원가입 (이메일 + 비밀번호)

**Request**
```json
{
  "email": "runner@example.com",
  "password": "dallim1234"
}
```

**검증 규칙**
- `email`: 이메일 형식, 최대 254자
- `password`: 8자 이상 + 영문/숫자 조합 필수
- 비밀번호는 BCrypt로 해싱해 저장하며, 어떤 응답·로그에도 평문/해시를 노출하지 않는다

**Response 201**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "isNewUser": true,
    "userId": "usr_9c3b",
    "provider": "EMAIL"
  }
}
```

**Error**
- `409 EMAIL_ALREADY_EXISTS` — 이미 가입된 이메일
- `400 INVALID_PASSWORD_FORMAT` — 비밀번호 정책 미충족
- `400 INVALID_EMAIL_FORMAT`

---

### `POST /auth/login`
일반 로그인 (이메일 + 비밀번호)

**Request**
```json
{
  "email": "runner@example.com",
  "password": "dallim1234"
}
```

**Response 200**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "isNewUser": false,
    "userId": "usr_9c3b",
    "provider": "EMAIL"
  }
}
```

**Error**
- `401 INVALID_CREDENTIALS` — 이메일 또는 비밀번호 불일치
  > 보안상 "이메일이 없음"과 "비밀번호가 틀림"을 구분해서 알려주지 않는다 (계정 존재 여부 노출 방지)

---

### 계정 통합(연동) 정책
- 계정 식별 키: `provider`(GOOGLE/KAKAO/EMAIL) + `providerId`(이메일 가입은 이메일 주소)
- 동일 이메일로 다른 provider 가입 이력이 있으면 `409 ACCOUNT_EXISTS_DIFFERENT_PROVIDER` 반환
- 카카오는 이메일 동의가 선택 항목이므로 이메일 미제공 시 통합 판단 불가 — 별도 계정으로 생성
- MVP1은 자동 계정 병합을 넣지 않는다(복잡도 대비 이득이 적음). 안내 문구로만 처리: "이미 Google로 가입된 이메일이에요"

---

### `POST /auth/refresh`
Refresh Token으로 Access Token 재발급

**Request**
```json
{ "refreshToken": "eyJ..." }
```

**Response 200**
```json
{
  "success": true,
  "data": { "accessToken": "eyJ...", "refreshToken": "eyJ..." }
}
```

**Error**
- `401 REFRESH_TOKEN_EXPIRED_OR_INVALID`

---

### `POST /auth/logout` 🔒
현재 세션의 Refresh Token 폐기(Redis에서 삭제)

**Response 200**
```json
{ "success": true, "data": null }
```

---

## 2. 사용자/프로필(user)

### `GET /users/nickname-check?value={nickname}`
닉네임 중복 확인 (비로그인도 가능)

**Response 200**
```json
{ "success": true, "data": { "available": true } }
```

---

### `POST /users/me/profile` 🔒
온보딩 프로필 최초 등록

**Request**
```json
{
  "nickname": "달리는고래",
  "avatarId": "avatar_03",
  "runningExperience": "UNDER_3_MONTHS",
  "comfortablePace": "PACE_6_7",
  "gender": "FEMALE"
}
```
> `gender`는 서버에만 저장, 어떤 응답에도 재노출하지 않음(타 유저 프로필 조회 API 포함)

**Response 201**
```json
{
  "success": true,
  "data": { "userId": "usr_8f2a", "nickname": "달리는고래" }
}
```

**Error**
- `409 NICKNAME_TAKEN`

---

### `GET /users/me` 🔒
내 프로필 조회

**Response 200**
```json
{
  "success": true,
  "data": {
    "userId": "usr_8f2a",
    "nickname": "달리는고래",
    "avatarId": "avatar_03",
    "runningExperience": "UNDER_3_MONTHS",
    "comfortablePace": "PACE_6_7",
    "totalRuns": 12,
    "totalDistanceKm": 48.2
  }
}
```
> `gender`는 응답에 포함하지 않음

---

### `GET /users/me/saved-routes` 🔒
저장한 코스 목록

**Query**: `page`, `size`

**Response 200**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "routeId": "rt_001",
        "name": "고래",
        "emoji": "🐳",
        "distanceKm": 5.1,
        "hasRun": false
      }
    ],
    "totalCount": 3
  }
}
```

---

### `POST /users/me/saved-routes/{routeId}` 🔒
코스 저장

**Response 201**
```json
{ "success": true, "data": null }
```

### `DELETE /users/me/saved-routes/{routeId}` 🔒
코스 저장 취소

**Response 200**
```json
{ "success": true, "data": null }
```

---

## 3. 홈(home)

### `GET /home` 🔒
홈 화면 통합 데이터 (Hero 카드 + 최근 달림)

**Query**: `lat`, `lng` (현재 위치, 오늘의 달림 추천용)

**Response 200**
```json
{
  "success": true,
  "data": {
    "todaySketch": {
      "routeId": "rt_001",
      "name": "고래",
      "emoji": "🐳",
      "distanceKm": 5.1,
      "estimatedMinutes": 36,
      "thumbnailGeoJson": { "type": "LineString", "coordinates": [[127.05,37.25],[127.06,37.26]] }
    },
    "continueRoutes": [
      { "routeId": "rt_002", "name": "물고기", "status": "PARTIAL", "lastCoveragePercent": 68 }
    ],
    "recentRuns": [
      { "runId": "run_101", "distanceKm": 5.18, "completedAt": "2026-08-20T07:32:00Z" }
    ]
  }
}
```

---

## 4. 코스(routes)

### `GET /routes`
코스 목록 조회 (비로그인 가능, 로그인 시 저장 여부 포함)

**Query**
| 파라미터 | 타입 | 설명 |
|---|---|---|
| `lat`, `lng` | number | 기준 좌표 |
| `radiusKm` | number | 검색 반경 (기본 5) |
| `minDistanceKm`, `maxDistanceKm` | number | 거리 필터 |
| `status` | string | `DISCOVERY`\|`VERIFIED`\|`POPULAR` |
| `sort` | string | `popular`\|`near`\|`new` |
| `page`, `size` | int | 페이징 |

**Response 200**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "routeId": "rt_001",
        "name": "고래",
        "emoji": "🐳",
        "distanceKm": 5.1,
        "estimatedMinutes": 36,
        "status": "POPULAR",
        "finisherCount": 148,
        "thumbnailGeoJson": { "type": "LineString", "coordinates": [ "..." ] }
      }
    ],
    "totalCount": 42,
    "page": 0,
    "size": 20
  }
}
```

---

### `GET /routes/{routeId}`
코스 상세 조회

**Response 200**
```json
{
  "success": true,
  "data": {
    "routeId": "rt_001",
    "name": "고래",
    "emoji": "🐳",
    "geoJson": { "type": "LineString", "coordinates": [ "..." ] },
    "distanceKm": 5.1,
    "estimatedMinutes": 36,
    "difficulty": "EASY",
    "status": "POPULAR",
    "finisherCount": 148,
    "trafficLightCount": 4,
    "elevationGainM": 32,
    "repeatSegmentPercent": 5,
    "runability": 0.87,
    "isSaved": false,
    "topFeedbackTags": ["그림이 잘 보여요", "러닝하기 편해요"]
  }
}
```

**Error**
- `404 ROUTE_NOT_FOUND`

---

### `GET /routes/{routeId}/finishers`
이 코스를 완주한 사람들의 GPS 결과 썸네일(최근 N개)

**Response 200**
```json
{
  "success": true,
  "data": {
    "items": [
      { "runId": "run_205", "userNickname": "숲속러너", "thumbnailGeoJson": { "...": "..." } }
    ]
  }
}
```

---

## 5. 러닝(runs) — 핵심 모듈

### `POST /runs` 🔒
러닝 시작 (러닝 세션 레코드 생성)

**Request**
```json
{
  "routeId": "rt_001",
  "mode": "SOLO",
  "startedAt": "2026-08-23T09:00:00Z",
  "clientDeviceInfo": { "gpsAccuracyM": 5 }
}
```

**Response 201**
```json
{
  "success": true,
  "data": { "runId": "run_301", "status": "IN_PROGRESS" }
}
```

---

### `PATCH /runs/{runId}/status` 🔒
러닝 일시정지/재개

**Request**
```json
{ "status": "PAUSED" }
```
> `status`: `PAUSED` | `RUNNING`

**Response 200**
```json
{ "success": true, "data": { "runId": "run_301", "status": "PAUSED" } }
```

---

### `POST /runs/{runId}/gps-batch` 🔒
GPS 포인트 배치 업로드 (러닝 종료 시 1회, 네트워크 실패 시 재시도)

**Request**
```json
{
  "points": [
    { "lat": 37.2506, "lng": 127.0092, "timestamp": "2026-08-23T09:00:03Z", "accuracyM": 4 },
    { "lat": 37.2508, "lng": 127.0093, "timestamp": "2026-08-23T09:00:08Z", "accuracyM": 5 }
  ]
}
```
> 클라이언트가 여러 배치로 나눠 보낼 수 있음(러닝 중 유실 방지를 위한 부분 업로드 허용). 서버는 `runId` 기준으로 누적 저장.

**Response 202** (비동기 처리, 최종 판정은 별도 API)
```json
{ "success": true, "data": { "receivedCount": 1240 } }
```

**Error**
- `409 RUN_ALREADY_FINISHED`

---

### `POST /runs/{runId}/finish` 🔒
러닝 종료 요청 → 서버가 완주 판정 수행

**Request**
```json
{
  "finishedAt": "2026-08-23T09:34:38Z",
  "clientPrecheckStatus": "COMPLETED",
  "stepCount": 6820
}
```
> `clientPrecheckStatus`는 클라이언트 로컬 판정값(참고용, 서버가 재계산해 덮어씀)
> `stepCount`(선택, 걸음 수 센서 값)는 2026-09-06 추가 —
> `docs/달림_화면별_상세기획서_v1.3.md` PART 4.1 "부정행위 방지"의 "걸음 수 대비 이동 거리
> 불일치" 향후 탐지 신호를 위해 지금부터 수집만 해둔다. **현재는 저장·조회(`GET /runs/{runId}`
> 응답)만 하고 완주 판정에는 전혀 쓰지 않는다** — 센서 값을 못 얻는 기기/세션은 생략(null) 가능.

**Response 200**
```json
{
  "success": true,
  "data": {
    "runId": "run_301",
    "status": "COMPLETED",
    "distanceKm": 5.18,
    "durationSeconds": 2078,
    "averagePaceSecPerKm": 401,
    "sketchMatchPercent": 92,
    "routeCompletionPercent": 97,
    "isFirstDiscoverer": false,
    "earnedInk": 50,
    "earnedBadges": []
  }
}
```
> `status`: `COMPLETED` | `PARTIAL` | `ABORTED` | `UNDER_REVIEW`

**Error**
- `400 GPS_DATA_INSUFFICIENT` — 업로드된 GPS 포인트가 판정에 부족
- `409 RUN_ALREADY_FINISHED`

---

### `GET /runs/{runId}` 🔒
러닝 결과 상세 조회 (결과 화면 재진입/공유용)

**Response 200**
```json
{
  "success": true,
  "data": {
    "runId": "run_301",
    "routeId": "rt_001",
    "routeName": "고래",
    "status": "COMPLETED",
    "actualGeoJson": { "type": "LineString", "coordinates": [ "..." ] },
    "plannedGeoJson": { "type": "LineString", "coordinates": [ "..." ] },
    "distanceKm": 5.18,
    "durationSeconds": 2078,
    "averagePaceSecPerKm": 401,
    "sketchMatchPercent": 92,
    "routeCompletionPercent": 97,
    "completedAt": "2026-08-23T09:34:38Z",
    "stepCount": 6820
  }
}
```
> `stepCount`: `POST /runs/{runId}/finish`에서 받은 값을 그대로 노출(2026-09-06 추가). 저장·디버깅/
> 향후 부정행위 탐지용 노출일 뿐 판정에는 쓰이지 않는다. 값이 없으면 `null`.

**Error**
- `404 RUN_NOT_FOUND`

---

## 6. 달림북(dallimbook)

### `GET /users/me/runs` 🔒
내 완주 기록 목록 (달림북 그리드용)

**Query**: `status`(선택), `page`, `size`

**Response 200**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "runId": "run_301",
        "routeName": "고래",
        "distanceKm": 5.18,
        "completedAt": "2026-08-23T09:34:38Z",
        "thumbnailGeoJson": { "...": "..." }
      }
    ],
    "totalCount": 12,
    "totalDistanceKm": 48.2
  }
}
```

---

## 7. 상태 코드 요약

| 코드 | 의미 |
|---|---|
| 200 | 정상 조회/처리 |
| 201 | 리소스 생성됨 |
| 202 | 비동기 접수됨(GPS 배치) |
| 400 | 요청값 오류/데이터 부족 |
| 401 | 인증 실패/토큰 만료 |
| 403 | 권한 없음(내 것이 아닌 리소스에 대한 조작 — 14장에서 처음 사용) |
| 404 | 리소스 없음 |
| 409 | 상태 충돌(중복 닉네임, 이미 종료된 러닝 등) |
| 500 | 서버 오류 |

---

## 8. 코스 생성 API (2026-09-03 — MVP 구분 없이 조기 착수)

> 사용자 요청으로 MVP2 예정이던 코스 생성(직접 그리기/AI 자동 생성)을 앞당겨 구현한다.
> 네이버 Directions API(5/15)는 자동차 전용이라 도보 라우팅에 쓸 수 없어(2026-09-03 확인),
> OSRM을 OSM 데이터 + foot 프로필로 자체 호스팅해 대체한다(`scripts/osrm-build.sh`,
> `docker-compose.yml`의 `osrm` 서비스, `localhost:5001`). 생성된 코스는 그 자체로는
> `sketch_routes`에 저장되지 않고(완주 통계가 없는 신규 코스라 DISCOVERY 상태로 등록만
> 가능 — 저장 여부는 별도 액션), 프리뷰 응답만 우선 내려준다.

### 8.1 `POST /routes/draw-convert` — 직접 그리기 → 실도로 변환

사용자가 지도 위에 손가락으로 그린 궤적(느슨한 점들)을 OSRM Map Matching으로 실제 도로에 스냅한다.

```json
// Request
{
  "drawnPath": {
    "type": "LineString",
    "coordinates": [[126.9235, 37.3905], [126.9238, 37.3907], ...]  // [lng, lat], 화면 드래그 중 일정 간격(예: 5~10m)으로 샘플링한 점들
  }
}
```
```json
// Response 200
{
  "success": true,
  "data": {
    "geoJson": { "type": "LineString", "coordinates": [[126.9235, 37.3905], ...] },
    "distanceKm": 3.42
  },
  "error": null
}
```
- 그린 점이 너무 성기거나(예: 2점뿐) 도로에서 너무 멀리 떨어져 OSRM이 매칭 실패(`code: "NoMatch"`)하면 `400 DRAW_MATCH_FAILED` — "그림이 도로와 너무 안 맞아요. 다시 그려보세요." 메시지로 응답한다.
- 최소 점 개수(예: 10개 미만)면 OSRM 호출 전에 자체적으로 400 `DRAW_TOO_SHORT`로 막는다(불필요한 OSRM 왕복 방지).

### 8.2 `POST /routes/discovery` — AI 자동 생성

시작 위치 + 목표 거리(+선택적으로 페이스)를 주면, 그 위치를 기점으로 목표 거리에 가까운 순환(loop, 출발=도착) 코스를 도로망 기반으로 생성한다.

```json
// Request
{
  "startLng": 126.9235,
  "startLat": 37.3905,
  "targetDistanceKm": 5.0,
  "pace": "PACE_6_7"   // optional, docs/01-feature-spec.md ComfortablePace apiValue 중 하나 — 현재는 로깅/향후 개인화용으로만 받고 라우팅 로직에는 아직 반영하지 않는다
}
```
```json
// Response 200
{
  "success": true,
  "data": {
    "geoJson": { "type": "LineString", "coordinates": [...] },
    "distanceKm": 4.87,
    "estimatedMinutes": 34
  },
  "error": null
}
```
- 생성 알고리즘: 목표 거리 D로부터 반지름 r = D/(2π)인 원 둘레에 K(4~6)개 후보 지점을 각도 기준으로 배치(약간의 무작위 편차 포함) → `start → wp1 → ... → wpK → start` 순서로 OSRM 다중 경유지 라우팅 호출 → 실제 반환 거리가 목표 대비 허용 오차(±15%) 밖이면 반지름을 `r *= target/actual` 비율로 조정해 재시도(최대 5회) → 그래도 안 맞으면 마지막으로 얻은 가장 근접한 결과를 그대로 반환한다(반환 응답의 `distanceKm`이 목표와 다를 수 있음을 클라이언트가 그대로 보여주면 됨 — 별도 실패 플래그 없음).
- 도로망이 희박한 위치(바다 한가운데 등)라 OSRM이 아예 경로를 못 찾으면 `422 DISCOVERY_NO_ROUTE`.
- **도로 안전성 가중치 (2026-09-04 추가)**: OSM 표준 `foot` 프로필 대신 커스텀 프로필
  (`scripts/osrm-profiles/dallim-foot.lua`, `scripts/osrm-build.sh`가 `osrm-extract`에 사용)을
  쓴다 — "좁은 골목·인도 없는 도로 회피" 안전 기준(01-feature-spec.md 2.2.C)을 도로 유형(`highway=*`)별
  가중치 차등화로 반영했다. `footway`/`pedestrian`/`path`/`living_street`/`track`(공원·하천변
  산책로 다수 포함)는 우대(기준 대비 1.2배), `primary`/`trunk`류는 강하게 기피(0.35배),
  `secondary`/`tertiary`류는 중간 기피(0.55배), 그 외(주택가 이면도로 등)는 중립. `sidewalk`
  태그가 명시돼 있으면(있음/없음) 큰길이라도 추가로 보정하고, `lit=no`(조명 없음)는 추가 페널티를
  준다. OSRM은 duration 기반 가중치라 속도를 낮게 잡을수록 그 구간이 "더 비싸져" 알고리즘이
  자연히 우회한다 — 실제 예상 소요시간(`estimatedMinutes`) 계산과는 무관(그건 거리 기준
  `ASSUMED_MINUTES_PER_KM`으로 별도 계산). **한계**: 실측 도로 폭/보차분리 GIS 데이터가 아니라
  OSM 태그 기반 1단계 근사치라, 태그가 부실한 지역은 정확도가 떨어질 수 있다.

### 8.3 이번에도 유보한 것
- `GET /races` 이하 대회 캘린더 전체
- 생성된 코스를 `sketch_routes`에 실제로 저장/공유하는 플로우(지금은 프리뷰 응답까지만)
- ~~`POST /sessions` 이하 소셜 세션 전체~~ — 2026-09-05 사용자 요청으로 일부 재개, 14장 참고
  (다만 지속적인 크루/클럽 형태는 여전히 유보, 14.5)

---

## 9. 알림 API (2026-09-03 추가 — 인앱 알림함, 1단계)

> 사용자 요청. 종 모양 아이콘 + 알림 목록의 **인앱 알림함**만 먼저 구현한다. 폰 시스템 푸시(FCM)는
> Firebase 프로젝트 키가 필요해 별도 후속 라운드로 미룬다 — 이번 라운드는 그 전 단계로, 알림을
> DB에 쌓고 앱 안에서 조회/읽음 처리만 한다. 트리거는 **러닝 완주(`RunStatus.COMPLETED`) 1건만**
> 우선 구현하고(가장 확실한 트리거), "근처에 새 코스 등록" 같은 위치 기반 트리거는 이번 범위 밖.

### 9.1 `GET /notifications` 🔒
내 알림 목록 (최신순)

**Query**: `page`(기본 0), `size`(기본 20) — `GET /routes` 페이지네이션과 동일 관례

```json
// Response 200
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "ntf_8f2a",
        "type": "RUN_COMPLETED",
        "title": "완주를 축하드려요! 🎉",
        "body": "고래 코스 5.10km를 완주했어요.",
        "relatedRunId": "run_1a2b",
        "isRead": false,
        "createdAt": "2026-09-03T05:12:00Z"
      }
    ],
    "totalCount": 1,
    "page": 0,
    "size": 20
  },
  "error": null
}
```

### 9.2 `GET /notifications/unread-count` 🔒
종 아이콘 배지용 — 목록 전체를 안 받아도 되게 별도 경량 엔드포인트로 분리.
```json
{ "success": true, "data": { "unreadCount": 3 }, "error": null }
```

### 9.3 `POST /notifications/{id}/read` 🔒
알림 하나를 읽음 처리. 이미 읽음이어도 200(멱등). 본인 알림이 아니면 404.
```json
{ "success": true, "data": null, "error": null }
```

### 9.4 서버 내부 트리거
`POST /runs/{runId}/finish`에서 판정 결과가 `COMPLETED`로 확정되는 시점(`com.dallim.run.RunService.finishRun`, `finisherCountSync.recordFinisher` 호출 바로 옆)에 알림 1건을 생성한다. `type: "RUN_COMPLETED"`, `title`/`body`는 위 예시처럼 코스 이름 + 거리를 채워 넣는다. 클라이언트가 별도로 호출하는 API는 아니다.

### 9.5 이번에도 유보한 것
- 완주 외 다른 알림 트리거(근처 신규 코스, 마케팅성 알림 등)
- 알림 설정(끄기/종류별 on-off) 화면

## 10. 폰 시스템 푸시 (2026-09-04 추가 — FCM, 알림 2단계)

> Firebase 프로젝트(`dallim-765b5`) 발급 완료로 9장에서 유보했던 FCM 푸시를 이어서 구현한다.
> 서버는 알림이 생성되는 시점(9.4의 트리거, 앞으로 추가될 다른 알림 타입도 동일)에 인앱 알림
> 저장과 함께 등록된 기기로 FCM 푸시를 보낸다. 푸시의 title/body는 인앱 알림과 동일하다.

### 10.1 `POST /users/me/device-tokens` 🔒
현재 기기의 FCM 토큰을 등록(upsert). 같은 토큰이 이미 있으면 갱신(마지막 등록 시각 업데이트)만 하고 중복 저장하지 않는다. 한 유저가 여러 기기 토큰을 가질 수 있다(다중 기기 푸시).

```json
// Request
{ "fcmToken": "dXy...", "platform": "ANDROID" }
```
```json
// Response 200
{ "success": true, "data": null, "error": null }
```

### 10.2 서버 내부 동작
- 알림 생성 시(`NotificationService`가 알림 레코드를 만드는 지점) 해당 유저의 등록된 토큰 전체로 FCM 메시지를 발송한다. 발송은 알림 생성 자체를 막지 않는다 — 실패해도 인앱 알림함 레코드는 그대로 남는다.
- FCM이 `UNREGISTERED`/`NOT_FOUND`(토큰 무효)를 반환하면 서버가 해당 토큰을 조용히 삭제한다. 클라이언트가 별도로 토큰 삭제를 호출할 API는 두지 않는다.
- 서비스 계정 키는 저장소에 커밋하지 않는다(`backend/secrets/firebase-adminsdk.json`, gitignore 처리됨). 경로는 환경변수(`FCM_CREDENTIALS_PATH`)로 주입하며 로컬 기본값은 그 경로를 가리킨다.

### 10.3 이번에도 유보한 것
- 로그아웃/토큰 폐기 시 클라이언트가 명시적으로 호출하는 삭제 API (무효 토큰은 발송 실패 시 서버가 정리)
- 알림 타입별 푸시 on/off 설정

---

## 11. 코스 생성 옵션 확장 (2026-09-04 추가 — 폐곡선 보정 + AI 필수 경유지)

> 사용자 요청, 8장의 연장선. 두 가지 별개 요구를 각각 어울리는 방식에 붙인다.
> (1) 직접 그리기(8.1)에서 출발=도착 의도로 원형에 가깝게 그려도, OSRM map-matching은 폐곡선을
> 보장하지 않는다 — 실제로 반지름 250m 원을 그려 테스트하니 매칭된 경로의 시작/끝 사이에
> 60~70m 간격이 남았다(로컬 도로망이 원형이 아니라 격자라 confidence도 0으로 나옴). 손가락으로
> 그린 그림 자체가 사용자 의도라 강제 보정은 명시적으로 요청했을 때만 한다.
> (2) AI 자동 생성(8.2)의 "이 장소는 꼭 지나가게 해줘" 요구는 직접 그리기가 아니라 AI 생성 쪽에
> 붙인다 — 이미 웨이포인트를 배치해 OSRM 다중 경유지 라우팅을 돌리는 구조라 자연스럽게 확장된다.

### 11.1 `POST /routes/draw-convert` — `closeLoop` 옵션 추가

```json
// Request (추가 필드, optional)
{
  "drawnPath": { "type": "LineString", "coordinates": [...] },
  "closeLoop": true   // 기본 false. 출발=도착 의도로 그렸을 때만 true로 보낸다.
}
```
- `closeLoop: true`이고 매칭된 경로의 첫 점과 마지막 점이 15m 넘게 떨어져 있으면, 마지막 점 →
  첫 점 구간을 OSRM `/route`로 한 번 더 연결해 폐곡선을 완성한다. 이 보정 구간 거리도
  `distanceKm`에 합산된다.
- 이미 15m 이내로 붙어 있으면 보정 구간을 추가하지 않는다(불필요한 OSRM 왕복 방지).
- 보정용 `/route` 호출까지 실패하면(도로가 아예 없는 경우 등) 에러로 막지 않고 보정 없이
  원래 매칭 결과를 그대로 반환한다 — "닫아주면 좋고 아니어도 그림 자체는 유효".
- `closeLoop`을 생략하거나 false면 8.1의 기존 동작 그대로(직선/개방 경로 포함, 폐곡선 보장 없음).

### 11.2 `POST /routes/discovery` — 필수 경유지(`requiredWaypoints`) 옵션 추가

```json
// Request (추가 필드, optional)
{
  "startLng": 126.9235,
  "startLat": 37.3905,
  "targetDistanceKm": 5.0,
  "requiredWaypoints": [
    { "lat": 37.3950, "lng": 126.9280 },
    { "lat": 37.3920, "lng": 126.9310 }
  ]   // 0~3개, 8.2의 K(4~6)개 후보 슬롯보다 항상 적어야 하므로 최대 3개로 제한
}
```
- 지정하면 8.2의 K개 후보 슬롯(각각 고유한 방위각을 가짐) 중, 각 `requiredWaypoints` 항목의
  실제 방위각(bearing, start 기준)에 가장 가까운 슬롯을 그 지점으로 고정 교체한다 — 여러 개를
  동시에 배정할 때는 (경유지, 슬롯) 쌍을 각도 차이가 작은 순서로 그리디하게 매칭해서 두 경유지가
  같은 슬롯을 다투지 않게 한다. 최종 방문 순서는 슬롯의 원래 각도 순서를 따르므로 경유지들이
  출발점을 기준으로 대략 각도 순서대로 방문된다(교차 없는 자연스러운 루프).
- 목표 거리 재시도(반지름 조정, 8.2 알고리즘)는 고정된 슬롯들에는 적용하지 않는다 — 그 지점을
  옮기면 "꼭 지나가게" 의도가 깨지므로, 나머지 자유 웨이포인트들의 반지름만 조정해 목표 거리에
  맞춘다.
- `requiredWaypoints`가 4개 이상이면(자유 웨이포인트가 하나도 안 남아 거리 조정이 불가능해짐)
  `400 VALIDATION_ERROR`.
- 경유지까지 OSRM이 경로를 못 찾으면(서비스 지역 밖, 도로망 없음 등) 기존과 동일하게
  `422 DISCOVERY_NO_ROUTE`.

### 11.3 이번에도 유보한 것
- 직접 그리기의 폐곡선 여부를 서버가 자동 판단하는 것 — `closeLoop`은 항상 클라이언트가
  명시할 때만 동작한다(그림을 보고 "닫힌 도형처럼 보인다"를 서버가 추측하지 않음)
- 경유지 방문 순서를 사용자가 직접 지정하는 것 — 항상 슬롯 각도 순서로 자동 정렬된다

### 11.4 `POST /routes/discovery` — point-to-point 모드 (`mode`, `endLat`/`endLng`) 추가

> 사용자 요청. 8.2는 항상 출발점으로 돌아오는 순환 코스만 만들었다 — 지하철역/집 등 실제
> 목적지가 있어서 그쪽으로 안 돌아와도 되는 사람을 위해, 지도에서 목적지를 직접 지정하는
> point-to-point 모드를 추가한다.

```json
// Request (추가 필드, optional)
{
  "startLng": 126.9235,
  "startLat": 37.3905,
  "targetDistanceKm": 5.0,
  "mode": "POINT_TO_POINT",   // "LOOP"(기본값) | "POINT_TO_POINT"
  "endLat": 37.4010,
  "endLng": 126.9300
}
```
- `mode: "POINT_TO_POINT"`이면 `endLat`/`endLng`가 둘 다 필수 — 없으면 `400 VALIDATION_ERROR`.
- 이번 라운드는 `requiredWaypoints`와 조합을 지원하지 않는다 — `POINT_TO_POINT`에서
  `requiredWaypoints`를 같이 보내면 `400 VALIDATION_ERROR`(11.5에 유보 명시).
- 알고리즘: 먼저 `start -> end` 직선 OSRM 경로(직선 최단 경로)를 구한다.
  - 그 거리가 이미 `targetDistanceKm`의 허용 오차(±15%) 안이거나 더 길면, 그대로 반환한다 —
    목적지가 고정된 이상 이보다 더 줄일 방법은 없다(응답 `distanceKm`이 목표와 다를 수 있음을
    클라이언트가 그대로 보여주면 된다, 8.2와 동일한 방침).
  - 더 짧으면, `start`와 `end`를 잇는 직선의 수직 방향으로 한 지점(우회 경유지)을 두고
    `start -> 우회지점 -> end` 순서로 다시 라우팅한다. 실제 반환 거리가 목표 대비 오차 밖이면
    우회지점까지의 거리를 `targetDistanceKm`에 맞춰 조정해 재시도한다(최대 5회, 8.2 알고리즘과
    동일한 재시도 원리) — 좌/우 어느 쪽으로 우회할지, 정확한 각도는 매 요청마다 무작위라 "다시
    생성"을 누르면 다른 우회 경로가 나온다.
- `start`-`end` 사이(또는 우회지점까지)에서 OSRM이 경로를 못 찾으면 기존과 동일하게
  `422 DISCOVERY_NO_ROUTE`.

### 11.5 이번에도 유보한 것 (11.4)
- 목적지 없이 "방향만 정하고 거리만큼 가다 멈추는" 모드 — 실제 목적지가 없으면 러너가 복귀
  수단을 알아서 찾아야 해서 이번 라운드는 목적지 직접 지정만 지원

### 11.6 `POST /routes/discovery` — `POINT_TO_POINT` + `requiredWaypoints` 조합 지원

> 사용자 요청, 11.5에서 유보했던 항목. 목적지를 지정한 채로도 "꼭 지나갈 장소"를 함께 쓰고
> 싶다는 요청 — 지하철역으로 가는 길에 특정 공원을 들르는 식.

```json
// Request (mode: "POINT_TO_POINT"와 requiredWaypoints를 함께 사용)
{
  "startLng": 126.9235,
  "startLat": 37.3905,
  "targetDistanceKm": 5.0,
  "mode": "POINT_TO_POINT",
  "endLat": 37.4010,
  "endLng": 126.9300,
  "requiredWaypoints": [
    { "lat": 37.3950, "lng": 126.9280 }
  ]   // 0~3개, 11.2와 동일한 상한을 그대로 재사용
}
```
- 이제 `mode: "POINT_TO_POINT"`에서 `requiredWaypoints`를 함께 보내도 `400 VALIDATION_ERROR`로
  막지 않는다. 개수 상한(0~3개)은 11.2와 동일하게 `startLat`/`startLng` 검증과 별개로 적용된다.
- 방문 순서: 11.2는 슬롯의 방위각 순서로 정렬하지만, point-to-point는 K개 후보 슬롯이 없으므로
  대신 각 경유지에 대해 `(start까지 거리) - (end까지 거리)`를 계산해 오름차순으로 정렬한다 —
  start에 가까울수록(상대적으로 end보다) 먼저 방문하고, end에 가까울수록 나중에 방문한다.
  최종 경로는 `start -> 경유지들(정렬됨) -> end` 순서로 OSRM 다중 경유지 라우팅을 한 번 호출한다.
- 그 거리가 이미 `targetDistanceKm`의 허용 오차(±15%) 안이거나 더 길면 그대로 반환한다 — 11.4와
  동일하게, 지정된 지점들 사이 경로는 이보다 더 줄일 방법이 없다.
- 더 짧으면, `start -> 경유지들 -> end` 경로에서 구간(leg) 중 직선거리가 가장 긴 구간 하나를
  골라 그 구간에만 11.4와 동일한 방식(수직 방향 우회 지점, 좌우/각도 무작위)의 우회 지점을
  하나 추가한다. 실제 반환 거리가 목표 대비 오차 밖이면 우회 지점까지의 거리를 조정해
  재시도한다(최대 5회) — 여러 구간이 동시에 짧아도 우회 지점은 항상 하나만 추가한다(11.4와
  동일한 단일 detour 정책).
- 경유지를 포함한 어떤 구간에서도 OSRM이 경로를 못 찾으면 기존과 동일하게
  `422 DISCOVERY_NO_ROUTE`.

---

## 12. 장소 검색 API (2026-09-04 추가 — AI 생성 필수 경유지를 검색으로 지정)

> 사용자 요청. 11.2의 "꼭 지나갈 장소"는 지금 지도 롱프레스로만 지정할 수 있는데, 정확한 좌표를
> 모르는 곳(예: "OO역", "OO공원")은 지도에서 직접 찾기 번거롭다 — 이름으로 검색해서 바로
> 추가할 수 있게 한다. **이번 라운드는 S-45의 필수 경유지에만 적용한다** — point-to-point의
> 목적지 검색은 유보(12.3).
>
> **검색 제공자로 카카오 로컬 API(키워드로 장소 검색)를 쓴다** — 네이버 지역 검색 API는 좌표를
> TM128(KATEC) 체계로 내려줘 WGS84 변환이 추가로 필요한 반면, 카카오는 `x`/`y`를 WGS84 경도/위도
> 문자열로 바로 내려줘 별도 좌표 변환 없이 기존 `LatLng`에 꽂을 수 있다. 로그인(1장)에서 이미
> 카카오 개발자 계정을 쓰고 있어 새 제공자 관계를 맺을 필요도 없다. REST API 키가 새로 필요하다
> (아래 12.2).

### 12.1 `GET /routes/places/search` — 장소 검색

```
GET /routes/places/search?query=안양역&lat=37.3905&lng=126.9235
```

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `query` | string | 검색어(필수, 공백만이면 400) |
| `lat` / `lng` | number | 검색 기준 좌표(optional) — 카카오 API의 위치 우선순위 파라미터(`x`/`y`)로 그대로 전달해 근처 결과를 우선 정렬. 둘 다 없으면 위치 편향 없이 검색 |

```json
// Response 200
{
  "success": true,
  "data": {
    "items": [
      { "name": "안양역", "address": "경기 안양시 만안구 안양동", "lat": 37.4004, "lng": 126.9227 }
    ]
  },
  "error": null
}
```
- 카카오 키워드 검색 결과 상위 5개만 내려준다(`size=5` 고정, 페이지네이션 없음 — 지도 롱프레스를
  대체하는 보조 입력이라 그 이상은 과함).
- `query`가 비어 있으면(공백 트림 후 빈 문자열) OSRM 호출 없는 8.1의 `DRAW_TOO_SHORT`와 같은
  성격으로 `400 VALIDATION_ERROR`.
- 업스트림(카카오 API) 호출이 실패하거나 타임아웃되면 500으로 막지 않고 `items: []`를 그대로
  반환한다 — 11.1의 `closeLoopIfNeeded`와 같은 방침: 검색은 롱프레스의 보조 수단이라 실패해도
  기능 자체가 막히면 안 된다. 클라이언트는 빈 배열을 "검색 결과 없음"과 동일하게 표시한다.
- 지역 제한(서울·경기)은 별도로 강제하지 않는다 — 카카오 검색 결과를 그대로 통과시키고, 서비스
  지역 밖 좌표를 골라도 이후 8.2/11.2의 OSRM 라우팅 단계에서 자연히 `422 DISCOVERY_NO_ROUTE`로
  걸러진다(새 검증 로직 추가 안 함).

### 12.2 서버 내부 동작
- 카카오 REST API 키(`KAKAO_LOCAL_REST_API_KEY` 환경변수, 카카오 로그인용 키와는 별도 발급)로
  `GET https://dapi.kakao.com/v2/local/search/keyword.json`을 서버 사이드에서 호출한다.
  `Authorization: KakaoAK {key}` 헤더 방식 — 클라이언트에 키를 절대 내려주지 않는다(9.2의 FCM
  서비스 계정 키와 같은 취급).
- 로컬 개발 환경에서 키가 비어 있으면(설정 안 함) 카카오 호출 자체를 건너뛰고 바로 `items: []`를
  반환한다 — 안드로이드의 `NAVER_MAP_CLIENT_ID_CONFIGURED` 패턴과 동일하게, 키 없이도 나머지
  기능(롱프레스로 직접 지정)은 그대로 동작해야 한다.

### 12.3 이번에도 유보한 것
- point-to-point 모드(11.4)의 목적지(`endLat`/`endLng`)를 검색으로 지정하는 것 — UI 컴포넌트는
  같은 검색창을 재사용할 수 있을 것으로 보이나, 이번 라운드는 필수 경유지만 범위로 한다
- 검색 결과 캐싱/디바운스 서버 지원 — 클라이언트가 타이핑 중 매 호출을 그대로 서버까지 보내지
  않도록 debounce하는 건 클라이언트 책임(서버는 매 요청을 독립적으로 처리)
- 카카오가 못 찾는 장소(POI로 등록 안 된 곳)에 대한 대체 지오코딩 — 이런 경우는 계속 롱프레스로
  지정하면 된다

---

## 13. 모양 선택 AI 생성 (2026-09-04 추가, 2026-09-04 재설계 — `mode: "SHAPE"`)

> 사용자 요청. 목표 거리만으로 순환/point-to-point 코스를 만드는 8.2/11.4에 이어, 하트·별처럼
> 원하는 모양의 윤곽을 따라 달리는 코스를 만든다.

> **재설계 배경 (최초 배포 버전은 폐기)**: 최초 버전은 `targetDistanceKm`에 맞춰 스케일을
> 잡고(8.2/11.4와 같은 ±15% 재시도 방식), "거리 우선/모양 우선" `priority` 토글로 재시도
> 횟수만 조절했다. 배포 후 실사용에서 "하트/별로 안 보인다"는 피드백을 받고 로컬 백엔드로
> 그대로 재현·진단한 결과: **문제는 재시도 횟수가 아니라 스케일 자체였다.** 목표 거리(5km)에
> 맞추면 모양이 반경 700m 안에 구겨져 알아볼 수 없고, 같은 템플릿을 반경 1.5km로 키우면
> (실측 거리 17.85km, 목표의 3.6배) 뚜렷하게 보였다. `priority: "SHAPE"`(재시도 5회→2회)로도
> 결과는 여전히 700m대에 머물렀다 — 애초에 "목표 거리에 맞추는 것"과 "모양이 보이는 것"은
> 대부분의 목표 거리대(3~8km)에서 양립이 안 된다는 뜻이다. 그래서 **`targetDistanceKm`으로
> 스케일을 정하는 방식을 버리고, 모양이 검증된 절대 크기(S/M/L)로 스케일을 직접 고정**하는
> 방식으로 다시 설계했다. 거리는 그 결과로 나오는 값이지 입력이 아니다.
>
> 재검증 중 추가로 발견한 것: `STAR`는 스케일을 키워도 문제가 해결되지 않는다 — 오목한
> 꼭짓점마다 생기는 왕복 스파이크가 스케일에 비례해서 같이 커져, 반경 1.0~1.5km에서 실측
> 거리가 **45.8~60.0km**까지 치솟았고 모양도 여전히 알아볼 수 없는 뒤엉킨 선이었다(하트/원/
> 물방울은 같은 조건에서 9~18km, 뚜렷하게 인식 가능). 오목한 꼭짓점이 있는 모양은 이 알고리즘
> (다중 waypoint를 그대로 도로에 라우팅)과 근본적으로 안 맞는다는 뜻이라, `STAR`는 v1에서
> **제외**한다(13.3).

### 13.1 `POST /routes/discovery` — `mode: "SHAPE"` 추가

```json
// Request (추가 필드, mode가 SHAPE일 때만 의미 있음)
{
  "startLng": 126.9235,
  "startLat": 37.3905,
  "targetDistanceKm": 5.0,   // SHAPE 모드에서는 무시된다 — 다른 mode와 필드를 공유하기 위해 형식상 필수
  "mode": "SHAPE",
  "shapeType": "HEART",      // "HEART" | "CIRCLE" | "DROP" — 13.3 (STAR 제외)
  "size": "M"                // "S" | "M" | "L", 기본값 "M" — 13.1.1
}
```
- `mode: "SHAPE"`면 `shapeType`이 필수(없거나 미등록 값이면 `400 VALIDATION_ERROR`, `STAR`도
  미등록 취급 — 13.3).
- `targetDistanceKm`은 다른 모드와 스키마를 공유하기 위해 요청 형식상 그대로 필수 필드로
  남지만, SHAPE 모드에서는 **무시된다** — 실제 크기는 `size`가 결정한다. 안드로이드는 SHAPE
  모드에서 이 필드에 아무 유효한 양수나 채워 보내면 된다(13.5, 거리 슬라이더 자체를 숨긴다).
- 이번 라운드는 `requiredWaypoints`/`endLat`/`endLng`와 조합하지 않는다(SHAPE와 함께 오면
  `400 VALIDATION_ERROR`) — 13.4에 유보 명시.
- 응답의 `distanceKm`은 그 요청에서 실제로 라우팅된 거리 그대로다 — 목표값이 없으므로 "오차"
  개념이 없고, 클라이언트는 이 값을 있는 그대로 보여주면 된다.

#### 13.1.1 `size` — 모양별 실측 기반 캘리브레이션

`priority` 필드는 폐기한다(더 이상 "거리에 맞추려는 시도" 자체가 없으므로 의미가 없다). 대신
템플릿의 중심-최원점 반경(`templateRadiusMeters`)을 `size`별로 고정 배치한다 — 이 벨트 지역
도로 밀도 기준으로 하트·원·물방울 세 모양을 반경 1000/1500/2200m에서 직접 실측한 값:

| size | templateRadiusMeters | 실측 거리 범위(하트/원/물방울) | 비고 |
|---|---|---|---|
| S | 1000 | 약 9~10km | "짧은 코스" — 그래도 일반적인 5km 러닝보다 길다 |
| M (기본값) | 1500 | 약 14~18km | "보통 코스" |
| L | 2200 | 약 20~25km | "긴 코스" — 하프마라톤급, 상급자용으로 안내 |

- 모양마다 도로망과의 궁합에 따라 같은 `size`에서도 거리가 어느 정도(실측 기준 ±25% 내외,
  드물게 그 이상) 달라진다 — 표는 범위로 표기했고, 정확한 값은 응답의 `distanceKm`으로
  확인한다.
- 요청하신 "도로보다 도보 위주의 대로변" 반영: SHAPE 모드는 8.2/11장이 쓰는 것과 **다른 전용
  OSRM 인스턴스**(13.2)로 라우팅한다 — 기존 프로필은 인도 유무와 무관하게 공원길/보행로를
  큰길보다 항상 우대해서, 그대로 쓰면 "대로변 우선"이 실현되지 않는다는 걸 확인했다(기존
  프로필의 `SIDEWALK_YES_OVERRIDE_MULTIPLIER`가 큰길+인도를 "중립"까지만 올려주고 공원길의
  우대 배율(1.2)에는 못 미침).

#### 13.1.2 알고리즘 — 후보 여러 개 생성 후 Fréchet 거리로 최적 선택 (재설계)

`size` 기반 스케일 확정 후 배포한 버전(1회 라우팅 + 회전 랜덤)은 회전각에 따라 결과 품질
편차가 컸다 — 같은 하트라도 어떤 회전에서는 위쪽 갈라짐(cleft)이 살아있어 하트로 보이고,
다른 회전에서는 도로가 그 자리에 없어서 뭉툭한 타원으로 뭉개졌다("스케치런인데 유사한
형태조차 안 나온다" 피드백, 실사용 재현으로 확인). 확률에 맡기는 대신, **여러 회전을 후보로
만들어 원본 모양과 가장 비슷한 것을 골라 반환**하도록 바꾼다.

- 각 요청마다 서로 다른 무작위 회전각으로 [`N_SHAPE_CANDIDATES`] = 6개의 회전 후보를 만들고,
  각각 13.2의 전용 인스턴스로 다중 waypoint 라우팅해 결과 경로를 얻는다(실패한 후보는 버림 —
  전부 실패하면 기존과 동일하게 `422 DISCOVERY_NO_ROUTE`).
- 각 후보에 대해, 회전된 템플릿과 실제 라우팅 결과를 각각 `GeoMath.resample`로 동일 개수(예:
  40개) 점으로 재샘플링한 뒤 **`FrechetDistance.discreteMeters`**(달림북 완주 판정의 "스케치
  일치도" 채점에 이미 쓰이는 유틸, `RunJudgementService`가 실제 GPS 궤적과 계획 경로를 비교할
  때 쓰는 것과 동일한 함수)로 "원본 모양과 실제 도로 경로가 형태적으로 얼마나 다른지"를 미터
  단위로 점수화한다.
- Fréchet 거리가 **가장 작은**(원본과 가장 비슷한) 후보를 최종 응답으로 반환한다.
- 검증: 로컬 프로토타입에서 하트 템플릿으로 회전 8개를 비교했을 때, Fréchet 거리가 가장 작은
  2개는 육안으로도 뚜렷한 하트(위쪽 갈라짐 살아있음)였고 가장 큰 2개는 뭉툭한 타원에
  가까웠다 — 이 지표가 실제 인식 가능 여부와 상관관계가 있음을 확인.
- 비용: OSRM 호출이 기존 1회에서 최대 6회로 늘어난다. 로컬 인스턴스 기준 각 호출이 100ms 내외라
  전체 지연은 수백 ms~1초 수준으로, "코스를 만들고 있어요, 몇 초 정도 걸릴 수 있어요"라는 기존
  안내 문구 범위 안에 들어온다(안드로이드 쪽 문구 변경 불필요).

#### 13.1.3 모양 템플릿 자체도 다시 점검 필요 (구현 단계 확인 사항)

재현 중 확인한 또 다른 요인: 지금 등록된 `HEART`/`DROP`의 실제 SVG 템플릿 형태가 너무
완만하면(굴곡이 약하면), 회전을 아무리 잘 골라도 도로 스냅 과정에서 그 특징(하트의 갈라짐,
물방울의 뾰족한 끝)이 옅어질 수 있다 — 13.1.2의 후보 선택은 "여러 시도 중 그나마 나은 것"을
고르는 것이지, 애초에 특징이 약한 템플릿을 강하게 만들어주진 않는다. 구현 시 각 템플릿의
굴곡(하트 상단 갈라짐 깊이, 물방울 끝 각도)을 실제 도로 스냅 결과가 여전히 알아볼 수 있을
정도로 다소 과장해서 그리는 걸 권장한다 — 로고 디자인에서 작은 크기로 축소될 걸 감안해
디테일을 과장하는 것과 같은 원리다.

### 13.2 전용 OSRM 인스턴스 — 대로변 인도 우선 프로필

새 프로필 `scripts/osrm-profiles/dallim-foot-shape.lua`(`dallim-foot.lua`를 베이스로 배율만
조정, 나머지 handler/access 설정은 그대로 재사용):

| 도로 유형 | 기존(`dallim-foot.lua`) | 신규(`dallim-foot-shape.lua`) |
|---|---|---|
| `footway`/`pedestrian`/`path`/공원·하천변 산책로 | 1.2 (최우대) | 1.0 (중립으로 낮춤) |
| `primary`/`secondary`/`tertiary` + `sidewalk=yes` | 1.0 (중립까지만) | **1.3 (신규 최우대)** |
| `residential`/`unclassified`(이면도로) | 1.0 (중립) | 0.8 (약한 기피 — "도로보다 대로변") |
| `primary`/`trunk`(인도 없음/불명) | 0.35 | 0.5 (여전히 기피하되 완화) |
| `lit=no` | 0.85 추가 페널티 | 0.85 추가 페널티(그대로) |

> 배율은 `dallim-foot.lua`가 처음 그랬듯 1단계 추정치다 — 이 문서의 프로토타입 방식대로 실제
> 벨트 지역 좌표에 돌려보고 재조정하는 걸 구현 단계 첫 작업으로 포함한다.

**데이터셋은 전국이 아니라 서비스 벨트(안양·군포·의왕·과천·성남 분당/판교)만 추출한다** —
지금 Lightsail t3.micro급(RAM 1GB) 위에 전국 데이터셋(`south-korea.osrm`, 파일 총합 ~2.8GB,
`--mmap` 미적용)을 이미 하나 돌리고 있어서, 같은 규모로 하나 더 얹으면 OOM 위험이 크다. 벨트
지역만 추출하면 용량이 몇십 MB 수준으로 훨씬 작아지고, 애초에 서비스 지역 밖에서는 코스 생성
자체가 의미 없으니 지역 한정이 자연스럽다.

- **선행 작업**: 기존 `osrm` 서비스 커맨드에도 `--mmap` 플래그를 추가해 메모리 사용을
  줄인다(새 인스턴스를 얹기 전에 먼저 적용).
- 새 스크립트 `scripts/osrm-shape-build.sh`: 이미 받아둔 `south-korea.osm.pbf`를 다시
  다운로드하지 않고 `osmium extract`(bbox: 벨트 지역)로 잘라낸 뒤, `dallim-foot-shape.lua`로
  extract/partition/customize.
- 새 `docker-compose.yml` 서비스 `osrm-shape` — 포트 `5002`,
  `command: osrm-routed --algorithm mld --mmap /data/dallim-belt.osrm`.
- 백엔드 설정에 `dallim.osrm.shapeBaseUrl`(env `OSRM_SHAPE_BASE_URL`) 추가. `DiscoveryService`는
  SHAPE 모드 요청만 이 별도 `OsrmClient` 인스턴스로 보낸다 — LOOP/POINT_TO_POINT/필수 경유지는
  기존 인스턴스 그대로.

### 13.3 모양 템플릿

- 좌표를 직접 하드코딩하지 않고 **SVG path 데이터**를 점 목록으로 변환해 등록한다 — JVM에서
  성숙한 SVG path 파서인 Apache Batik의 `org.apache.batik.parser.PathParser`
  (`org.apache.xmlgraphics:batik-parser`, Maven Central)로 표준 path 문법(M/L/C/Q/Z 등)을 그대로
  해석한다. 파싱된 폴리라인은 기존 `GeoMath.resample`로 균등 아크렝스 N개 점(예: 20개)으로
  재샘플링해 재사용한다. (이 부분은 최초 설계 그대로 — 문제는 스케일 전략이었지 템플릿 표현
  방식이 아니었다.)
- **v1 세트: `HEART`, `CIRCLE`, `DROP`(물방울)만.** 실측 결과 셋 다 반경 1000~2200m
  범위에서 뚜렷하게 인식 가능했다.
- **`STAR`는 v1에서 제외한다.** 같은 조건(반경 1000~1500m)에서 실측 거리가 45.8~60.0km로
  치솟았고, 결과 경로도 모양과 무관한 뒤엉킨 선이었다 — 오목한 꼭짓점마다 생기는 왕복
  스파이크가 스케일에 비례해서 커지기 때문에, "스케일을 키우면 해결된다"는 이번 재설계의
  핵심 전제가 오목한 모양에는 적용되지 않는다. 재도입하려면 뾰족한 꼭짓점을 둥글린 "완화된
  별" 템플릿을 만들거나, 다중 waypoint 라우팅이 아닌 다른 알고리즘(도로 그래프 위에서 직접
  모양을 따라가는 경로 탐색 등)이 필요해 보인다 — 이번 라운드 범위 밖.
- **닫힌 윤곽선(한붓그리기) 전제** — 등록하는 SVG는 단일 서브패스만 허용한다. 여러 서브패스로
  이루어진 도형(태극기의 분리된 4괘 등)은 API 요청이 아니라 서버에 새 모양을 등록하는 개발
  단계에서 거부된다.

### 13.4 이번에도 유보한 것
- `requiredWaypoints`/point-to-point(11장/12장)와 SHAPE 모드의 조합
- `STAR`를 포함한 오목한 꼭짓점이 있는 모양 전반 — 13.3
- 태극기처럼 여러 개의 분리된 구성요소로 이루어진 도형 — "펜을 떼지 않는 닫힌 윤곽선" 전제를
  벗어나며, 지원하려면 구간별 이동(pen-up)을 허용하는 별도 기능이 필요하다
- 사용자가 SVG를 직접 업로드해서 커스텀 모양을 만드는 것 — v1은 서버에 미리 등록된 목록
  중에서 고르는 것만 지원

### 13.5 안드로이드 (S-45)
- "코스 방식" 칩에 "모양 선택"을 세 번째 옵션으로 추가.
- 모양 선택 시:
  - 목표 거리 슬라이더를 **숨긴다** — SHAPE 모드에서는 거리를 입력받지 않는다(13.1 참고, 서버가
    무시하는 값을 입력받게 두면 혼란만 준다).
  - 등록된 모양(하트/원/물방울) 3개를 아이콘 그리드로 노출.
  - "짧은 코스(S)/보통 코스(M)/긴 코스(L)" 크기 선택(기본값 M) — 각 옵션 옆에 대략적인 거리
    범위(예: "약 14~18km")를 13.1.1 표 기준으로 함께 보여준다. L은 "하프마라톤급, 상급자용"
    같은 보조 카피를 추가한다.
  - 결과 화면에서는 실제 `distanceKm`을 그대로 보여주고, 사용자가 미리 본 범위와 다를 수
    있다는 걸 별도로 사과하지 않는다(범위로 안내했으므로 정상 동작).
- `STAR`는 이번 라운드에 아예 노출하지 않는다(서버가 지원하지 않음, 13.3).

---

## 14. 같이 달리기 모집 (2026-09-05 추가 — 범위 공식 확장)

> 사용자 요청으로 8.3에서 "소셜 세션 전체"로 유보했던 것을 일부 재개한다. 지속적인 "크루"
> (회원/권한/랭킹이 있는 클럽)가 아니라, **기존 코스(`sketch_routes`) 하나를 지정해 특정
> 날짜·시간에 같이 뛸 사람을 모으는 일회성 게시글** 정도로 범위를 좁혔다
> (`docs/01-feature-spec.md` 1.8).

### 14.1 데이터 모델

```
run_meetups
  id               varchar(32) PK
  route_id         varchar(32) FK -> sketch_routes
  host_user_id     varchar(32) FK -> users
  scheduled_at     timestamptz      -- 미래 시각만 허용(생성 시점 기준)
  max_participants int              -- 2~20
  description      text null
  status           varchar(16)      -- OPEN | CANCELLED (CLOSED는 별도 상태 없이 "정원 참, scheduled_at 지남"을 조회 시점에 판단)
  created_at       timestamptz

run_meetup_participants
  meetup_id        varchar(32) FK -> run_meetups
  user_id          varchar(32) FK -> users
  joined_at        timestamptz
  PRIMARY KEY (meetup_id, user_id)
```
- host는 생성과 동시에 `run_meetup_participants`에 자동으로 추가된다.
- "마감"(정원 참)과 "종료"(예정 시각이 지남)는 별도 배치 작업 없이 조회 시점에
  `count(participants) >= max_participants` / `scheduled_at < now()`로 판단한다 — 상태를
  갱신하는 스케줄러를 새로 두지 않는다.

### 14.2 `GET /routes/{routeId}/meetups` — 코스의 모집 목록

```json
// Response 200
{
  "success": true,
  "data": {
    "items": [
      {
        "meetupId": "mt_001",
        "hostNickname": "달림이",
        "scheduledAt": "2026-09-13T22:00:00Z",
        "maxParticipants": 6,
        "currentParticipants": 3,
        "status": "OPEN",
        "isFull": false,
        "isPast": false
      }
    ]
  },
  "error": null
}
```
- 정렬: `scheduledAt` 오름차순(가까운 일정이 먼저). 페이지네이션 없음(코스 하나당 모집 개수가
  많지 않을 것으로 예상 — 필요해지면 다음 라운드에 추가).
- `isFull`/`isPast`는 14.1의 판단 규칙을 서버가 미리 계산해 내려준다(클라이언트가 시각 비교를
  중복 구현하지 않도록).
- 인증 불필요(다른 `/routes` 하위 조회 엔드포인트와 동일).

### 14.3 `POST /routes/{routeId}/meetups` — 모집 생성 🔒

```json
// Request
{
  "scheduledAt": "2026-09-13T22:00:00Z",
  "maxParticipants": 6,
  "description": "천천히 페이스로 완주 목표예요"
}
```
```json
// Response 201
{ "success": true, "data": { "meetupId": "mt_001" }, "error": null }
```
- `scheduledAt`이 현재 시각보다 과거면 `400 VALIDATION_ERROR`.
- `maxParticipants`가 2~20 범위 밖이면 `400 VALIDATION_ERROR`.
- `routeId`가 존재하지 않으면 `404 ROUTE_NOT_FOUND`(4장과 동일한 코드 재사용).
- 생성 즉시 host가 참가자로 등록된다(별도 `join` 호출 불필요).

### 14.4 `GET /meetups/{meetupId}` / `POST /meetups/{meetupId}/join` / `POST /meetups/{meetupId}/leave` / `DELETE /meetups/{meetupId}` 🔒

```json
// GET /meetups/{meetupId} Response 200
{
  "success": true,
  "data": {
    "meetupId": "mt_001",
    "routeId": "rt_002",
    "routeName": "물고기",
    "hostUserId": "usr_1",
    "hostNickname": "달림이",
    "scheduledAt": "2026-09-13T22:00:00Z",
    "maxParticipants": 6,
    "description": "천천히 페이스로 완주 목표예요",
    "status": "OPEN",
    "isFull": false,
    "isPast": false,
    "isHost": false,
    "isJoined": true,
    "participants": [
      { "userId": "usr_1", "nickname": "달림이", "isHost": true },
      { "userId": "usr_2", "nickname": "러너B", "isHost": false }
    ]
  },
  "error": null
}
```
- `isHost`/`isJoined`는 요청한 유저(`Authorization` 토큰) 기준 계산값.
- `join`: 이미 참가 중(host 포함)이면 `409 ALREADY_JOINED`. 정원이 찼으면 `409 MEETUP_FULL`.
  예정 시각이 지났거나 `status: CANCELLED`면 `409 MEETUP_ENDED`. 성공 시 `200`.
- `leave`: host가 호출하면 `400 VALIDATION_ERROR`("모집을 취소하려면 삭제를 사용하세요" 같은
  메시지) — host는 참가 취소가 아니라 모집 자체를 취소해야 한다. 애초에 참가 중이 아닌
  유저가 호출하면 에러 없이 그냥 `200`으로 처리한다(멱등 — 이미 안 나간 상태에서 나가기를
  눌러도 에러로 취급할 이유가 없음).
- `DELETE`: host가 아니면 `403 MEETUP_NOT_HOST`. 실제 행을 지우지 않고 `status`만
  `CANCELLED`로 바꾼다(참가자들이 갑자기 글이 사라진 걸로 오인하지 않도록, 1.8.2).
- `meetupId`가 존재하지 않으면 어떤 액션이든 `404 MEETUP_NOT_FOUND`.

### 14.5 서버 내부 동작 — 알림 연동
- 누군가 `join`하면 host에게 인앱 알림 1건을 생성한다("OO님이 [코스명] 모집에 참가했어요") —
  9장의 `NotificationService`를 그대로 재사용, 새 알림 타입 하나만 추가한다. FCM 푸시(10장)는
  알림 생성 지점에 이미 공통 연동돼 있어 추가 작업이 필요 없다.
- host 본인이나 시스템이 만든 알림이 아니므로, host가 자기 자신에게 알림이 가는 경우는 없다
  (host는 join 대상이 될 수 없음 — 이미 참가 중이라 `ALREADY_JOINED`로 막힘).

### 14.6 이번에도 유보한 것
- 지속적인 "크루"/클럽(회원 목록, 가입 승인, 랭킹) — 코스 1건짜리 일회성 모집까지만
- 모집 게시글/참가자 간 채팅
- 코스와 무관하게 모집 글을 가로질러 검색/둘러보는 통합 목록(지역/날짜 필터 등)
- 정원 초과 시 대기열(waitlist) — 지금은 꽉 차면 그냥 막는다
- 모집 취소/마감 시 참가자에게 별도 알림 — 이번 라운드는 host에게 가는 참가 알림만

---

## 15. 러닝 커리어(완주 이력) (2026-09-06 — `docs/달림_화면별_상세기획서_v1.3.md` SPEC 편입)

> `docs/달림_화면별_상세기획서_v1.3.md` PART 3-A "S-04b 러닝 커리어 입력", PART 3-H
> "S-82 내 대회(메달 선반)" / "S-83 완주 이력 등록/편집" 근거. 이번 라운드는 **완주 이력
> CRUD + PB 계산 + 페이스 제안**만 구현한다 — 대회 캘린더(S-80/81), 코스 미리달리기(S-85),
> 훈련플랜(S-86), 기록 인증(S-84)은 범위 밖(SPEC에 없어 보류).
>
> 자기신고 이력이므로 응답에 항상 `verified: false`가 명시된다 — 인증 승격 경로(S-84)가
> 없어 향후 배지/랭킹 등에서 신뢰 소스로 함부로 쓰이지 않도록 하기 위함.

### 15.1 데이터 모델

```
race_records
  id               varchar(32) PK
  user_id          varchar(32) FK -> users
  race_name        varchar(100)     -- 자유 입력(대회 DB 검색은 이번 범위 밖 — 직접 입력만)
  category         5K | 10K | HALF | FULL | ULTRA | TRAIL | OTHER
  distance_km      double null      -- OTHER는 필수(양수). 5K/10K/HALF/FULL은 카테고리 기준
                                     -- 고정값을 서버가 채우되 클라이언트가 보내면 그 값을 신뢰.
                                     -- ULTRA/TRAIL은 대회마다 거리가 달라 고정값이 없어 null 허용
  year             int              -- 1990 ~ 현재연도+1
  record_seconds   int null         -- hh:mm:ss를 초 단위로 저장, 선택 입력
  record_type      NET | GROSS | null
  bib_number       varchar(32) null
  memo             text null
  verified         boolean          -- 이번 라운드는 항상 false(자기신고, 인증 승격 경로 없음)
  created_at       timestamptz
```
- **PB(personal best)**: 같은 `category` 내 `recordSeconds`가 있는 이력 중 최솟값. 별도
  배치/캐시 없이 **매 조회 시 실시간 계산**한다(사용자당 이력 수가 적어 성능 문제 없음).
- 본인 이력만 조회/수정/삭제 가능 — 다른 유저의 `id`로 `PATCH`/`DELETE`를 시도하면
  `404 RACE_RECORD_NOT_FOUND`로 응답한다(5장 `RunService`의 관례와 동일하게, 남의 개인
  리소스 존재 여부를 403으로 굳이 알려주지 않는다 — 7장 상태 코드표의 403 정의와 별개로,
  "내 것이 아닌 리소스" 중에서도 조회 자체가 안 되는 개인 기록은 404를 쓴다).

### 15.2 `GET /users/me/race-records` 🔒
내 완주 이력 목록 — 연도 내림차순(동일 연도 내에서는 최신 등록순)

**Response 200**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "race_a1b2c3d4",
        "raceName": "2026 서울 하프마라톤",
        "category": "HALF",
        "distanceKm": 21.0975,
        "year": 2026,
        "recordSeconds": 6300,
        "recordType": "NET",
        "bibNumber": "A1234",
        "memo": "첫 하프",
        "verified": false,
        "isPb": true,
        "paceSuggestion": "PACE_6_7",
        "createdAt": "2026-09-06T09:00:00Z"
      }
    ]
  },
  "error": null
}
```
- `isPb`: 15.1의 규칙으로 매 요청마다 계산.
- `paceSuggestion`: `category`가 `HALF`/`FULL`이고 `recordSeconds`가 있을 때만 채워진다
  (15.5 계산 로직 재사용). 그 외에는 `null`.
- 페이지네이션 없음(코스당 모집과 마찬가지로 유저 1인당 이력 수가 많지 않을 것으로 예상 —
  필요해지면 다음 라운드에 추가).

### 15.3 `POST /users/me/race-records` 🔒
완주 이력 등록

**Request**
```json
{
  "raceName": "2026 서울 하프마라톤",
  "category": "HALF",
  "distanceKm": null,
  "year": 2026,
  "recordSeconds": 6300,
  "recordType": "NET",
  "bibNumber": "A1234",
  "memo": "첫 하프"
}
```
- `raceName`: 필수, 공백만으로는 안 됨.
- `category`: `5K` / `10K` / `HALF` / `FULL` / `ULTRA` / `TRAIL` / `OTHER` 중 하나가 아니면
  `400 VALIDATION_ERROR`.
- `distanceKm`: `category`가 `OTHER`면 필수(양수) — 없거나 0 이하면 `400 VALIDATION_ERROR`.
  그 외 카테고리는 생략 가능(생략 시 카테고리 기준 고정값을 서버가 채움: 5K=5.0,
  10K=10.0, HALF=21.0975, FULL=42.195, ULTRA/TRAIL은 null). 값을 보내면 0 이하일 때만 에러.
- `year`: `1990` ~ `현재연도+1` 범위를 벗어나면 `400 VALIDATION_ERROR`.
- `recordSeconds`: 보내는 경우 0보다 커야 함.
- `recordType`: 보내는 경우 `NET`/`GROSS`만 허용.

**Response 201** — 15.2의 아이템 하나와 동일한 형태(생성 직후 `isPb`/`paceSuggestion`까지
바로 계산해서 내려준다 — S-83 "자동 처리: 종목별 PB 자동 갱신 / 기록 → 예상 페이스 환산"
요구를 응답 한 번으로 충족).
```json
{ "success": true, "data": { "id": "race_a1b2c3d4", "...": "..." }, "error": null }
```

### 15.4 `PATCH /users/me/race-records/{id}` 🔒
완주 이력 수정 — 부분 수정. 요청에 없는 필드는 기존 값 유지(명시적으로 값을 지우는 기능은
이번 범위 밖). 검증 규칙은 15.3과 동일(수정 후 값 기준). 본인 이력이 아니거나 존재하지
않으면 `404 RACE_RECORD_NOT_FOUND`.

**Response 200** — 15.2의 아이템 하나와 동일한 형태(수정 반영된 최신 값).

### 15.5 `DELETE /users/me/race-records/{id}` 🔒
완주 이력 삭제. 본인 이력이 아니거나 존재하지 않으면 `404 RACE_RECORD_NOT_FOUND`.

**Response 200**
```json
{ "success": true, "data": null, "error": null }
```

### 15.6 `POST /users/me/race-records/pace-suggestion` 🔒
S-04b/S-83 "기록 → 예상 페이스 환산" 단독 호출용(이력을 실제로 저장하기 전에 미리보기로도
쓸 수 있도록 별도 엔드포인트로 노출 — 15.3 응답의 `paceSuggestion`과 계산 로직 공유).

**Request**
```json
{ "category": "HALF", "recordSeconds": 6300 }
```
**Response 200**
```json
{ "success": true, "data": { "suggestedPace": "PACE_6_7" }, "error": null }
```
- `category`가 `HALF`/`FULL`이 아니면 `400 VALIDATION_ERROR`("페이스 제안은 하프 또는 풀
  기록에서만 가능합니다").
- `recordSeconds`가 0 이하면 `400 VALIDATION_ERROR`.

**계산 방식** (SPEC에 정확한 공식이 없어 다음과 같이 근사 — `PaceSuggestionCalculator` 참고):
1. **Riegel 공식** `T2 = T1 × (D2/D1)^1.06`으로 입력 기록(하프 또는 풀)을 풀코스 상당
   기록으로 투영해, 하프/풀 두 입력을 하나의 기준 거리로 정규화한다.
2. 그 풀코스 상당 페이스(초/km)에 보정 배율 **1.15**를 곱해 "편안한 러닝 페이스"로 늦춘다 —
   대회 기록 자체는 전력(레이스) 페이스이고, 일상적으로 편하게 뛰는 페이스는 그보다 느리기
   때문. 1.15~1.2배 범위 중 보수적인 쪽을 채택했다(정확한 배율은 SPEC에 없는 임의 근사치).
3. 위 초/km 값을 `comfortablePace`와 동일한 5단계(`PACE_UNDER_5`/`PACE_5_6`/`PACE_6_7`/
   `PACE_7_8`/`PACE_OVER_8`, 2장 참고)로 매핑한다. 경계값은 android
   `ProfileOptions.ComfortablePace` 라벨(`5'00" 이하` 등)에서 역산한 분/km 구간이다.
4. 이 값은 온보딩(S-04b)의 페이스 제안 UX에도, 마이 탭 "완주 이력 관리"에서 이력 등록/수정
   시에도 동일하게 재사용된다 — 별도 온보딩 전용 API는 만들지 않았다.

### 15.7 이번 라운드에 만들지 않은 것
- 대회 캘린더/대회 상세/참가 예정 대회 담기(S-80/81, S-82 상단·중단 섹션)
- 코스 미리달리기(S-85), 훈련플랜(S-86)
- 기록 인증(사진/링크 제출 → 운영 검토 → `인증` 배지, S-84) — 그래서 `verified`는 항상 `false`
- 대회 DB 검색(현재는 `raceName` 자유 입력만) — S-83이 "없으면 직접 입력 허용"이라고 명시한
  경로만 우선 구현
- "완주 이력 3건 이상 시 배지" 같은 마일스톤/배지 부여 로직(S-83) — 배지 시스템 자체가 이번
  범위 밖이라 판단, 향후 배지 도메인이 생기면 이 API의 `items.length`로 계산 가능
