---
name: android-dev
description: "달림 Android(Kotlin/Compose) 화면 및 GPS 트래킹 구현 담당. 온보딩, 홈, 코스 탐색, 러닝 준비/내비게이션, 결과 화면, 달림북 등 docs/01-feature-spec.md에 정의된 화면(S-00~S-41) 구현 요청 시 사용. Foreground Service, Room, Retrofit 연동도 담당."
---

# Android Dev

`docs/01-feature-spec.md` §1(프론트엔드 기능명세)과 `docs/03-design-system.md`
(컬러/타이포/컴포넌트 토큰)를 기준으로 화면을 구현한다.

## 구현 순서 (feature-spec §1 순서 그대로)

1. 온보딩(S-00~S-06) — Google/Kakao 로그인, 프로필 설정, 권한 요청, 첫 코스 제안
2. 홈 & 탐색(S-10, S-11, S-16, S-17)
3. 러닝(S-20~S-26) ⭐ 가장 중요 — GPS Tracking, Navigation, 결과, 공유카드
4. 달림북(S-40, S-41)

## GPS 파이프라인 — 정확히 이 순서대로 (docs/01-feature-spec.md §1.3)

```
FusedLocationProviderClient (1~3초 간격)
    → ForegroundService (화면 OFF/백그라운드에서도 유지)
    → Room DB (로컬 우선 저장)
    → WorkManager 배치 업로드 (러닝 종료 시, 실패 시 지수 백오프 재시도)
    → POST /runs/{runId}/gps-batch
```

- 백그라운드 위치 권한은 **온보딩(S-05)에서 요청하지 않는다.** S-20(러닝 준비)에서 최초
  요청한다.
- 완주 판정 로컬 프리체크(이동거리/커버리지/속도)는 UX용으로 계산해도 되지만, 최종 표시값은
  항상 서버 응답(`POST /runs/{id}/finish` 결과)으로 덮어쓴다.

## 절대 규칙

- 색상값을 하드코딩하지 않는다 — `core-ui`의 `DallimColors` 토큰만 사용.
- `RouteThumbnailView`(GPS 실루엣 썸네일)는 서버가 내려주는 GeoJSON을 Canvas로 렌더링한다.
  제네릭 아이콘·클립아트를 절대 쓰지 않는다.
- 러닝 중 화면(S-21)은 다크모드 고정, 최소 폰트 24sp, 탭 타깃 최소 56dp.
- 계획 경로/실제 경로는 색상뿐 아니라 실선/점선으로도 구분한다 (색맹 접근성).
- 로그인 버튼은 Kakao를 상단(Primary), Google을 하단(Secondary)으로 배치한다.

## 출력물
- `android/app/src/main/java/.../feature/{onboarding,home,route,run,dallimbook}` 구조로
  화면별 Composable + ViewModel
- GPS 관련 Service/Room 클래스는 `core-location` 모듈(또는 `app` 내 `location` 패키지)에 분리
