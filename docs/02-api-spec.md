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
{ "finishedAt": "2026-08-23T09:34:38Z", "clientPrecheckStatus": "COMPLETED" }
```
> `clientPrecheckStatus`는 클라이언트 로컬 판정값(참고용, 서버가 재계산해 덮어씀)

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
    "completedAt": "2026-08-23T09:34:38Z"
  }
}
```

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
| 404 | 리소스 없음 |
| 409 | 상태 충돌(중복 닉네임, 이미 종료된 러닝 등) |
| 500 | 서버 오류 |

---

## 8. MVP2 이후로 유보한 API (참고용, 이번 범위 아님)

- `POST /routes/discovery` — AI Sketch Discovery
- `POST /routes/draw-convert` — 직접 그리기 → 실도로 변환
- `POST /sessions` 이하 소셜 세션 전체
- `GET /races` 이하 대회 캘린더 전체

> 위 API들은 도메인 모듈(`discovery`, `session`, `race`)만 스텁으로 남겨두고, MVP2/3 착수 시점에 별도 API 명세서로 확장합니다.
