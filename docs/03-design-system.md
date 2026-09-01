# 달림(Dallim) UI 디자인 시스템 — v1.0

> 대화를 통해 확정된 결정을 개발 참조용 문서로 정리한 것입니다. Compose 개발 시 이 문서의 값을 그대로 디자인 토큰 코드로 옮기면 됩니다.

---

## 1. 컬러 시스템

### 1.1 결정 배경 (왜 이 색인가)

국내 대형 앱들이 이미 점유한 색을 확인한 결과:

| 색 | 선점 브랜드 |
|---|---|
| 초록 | 네이버 |
| 노랑 | 카카오 |
| 파랑 | 토스 |
| 오렌지/코랄 | 당근·직방·**Strava(러닝앱 카테고리 자체)** |
| 민트 | 배달의민족 |
| 핑크 | 야놀자 |
| 빨강 | 여기어때 |
| 보라(플럼 계열) | 마켓컬리("컬리퍼플") |

→ 순수 단색으로는 빈 자리가 거의 없어서, **단일 컬러 대신 시그니처 그라디언트**로 방향을 잡음. 달림은 애초에 "GPS 선이 브랜드의 본체"인 서비스라 그라디언트가 오히려 정체성에 더 맞음.

### 1.2 확정 컬러

```
Primary            #4A3AFF   블루바이올렛 — 컬리퍼플(플럼/자주 계열)과 구분되도록 파랑 쪽으로 튼 톤
Primary Dark        #2E22C7   다크모드 / 러닝 중 화면(S-21)
Primary Light       #EAE8FF   배경 강조, 선택 상태

Gradient Start       #4A3AFF   (블루바이올렛 — 그라디언트 시작)
Gradient End         #FF6B4A   (코랄 — 그라디언트 끝)
```

**그라디언트 적용 범위 (중요 — 아무데나 쓰지 않음)**

| 적용 O | 적용 X — 단일 Primary만 |
|---|---|
| GPS 궤적 자체(시작=보라 → 완성=코랄로 번지는 표현) | 버튼, 텍스트 링크, 아이콘 |
| 앱 아이콘 | 폼 요소, 인풋 필드 |
| 결과 화면(S-25) GPS 그림 | 리스트 아이템 |
| 온보딩 캐러셀 슬라이드 일러스트 | 탭바 |
| 공유 카드(S-26) 배경 옵션 중 하나 | 에러/경고 상태 색 |

→ **기능적 UI는 단일 Primary(#4A3AFF)로 일관성 유지, 감정적 순간(완주·공유·앱 아이콘)에만 그라디언트.**

**스플래시(S-00) — 2026-08-25 결정 변경**: 화면 전체를 그라디언트로 채우고 "달림" 텍스트를 띄우는 방식은 폐기. 배민·토스 등 국내 앱 관례를 따라 **Background(#FAFAF8) 단색 배경 위에 앱 아이콘(그라디언트 마크)만 중앙에 짧게 노출**하는 방식으로 변경. 그라디언트는 여전히 앱 아이콘 자체에는 쓰이지만, 화면 배경 전체에는 쓰지 않는다.

### 1.3 상태 컬러 (Route 상태)

```
DISCOVERY    #9E9E9E  (중성 회색 — 아직 미검증)
VERIFIED     #4A90D9  (민트/블루 계열 — 원문 지정)
POPULAR      Primary(#4A3AFF) 배경 사용
UNDER_REVIEW #F5A623  (옐로우 경고)
```

### 1.4 시맨틱 컬러

```
Success   #2ECC71
Warning   #F5A623
Error     #E74C3C
Background        #FAFAF8  (라이트 모드 기본)
Background Dark    #0F0F14  (러닝 중 다크모드)
Surface            #FFFFFF
Text Primary       #1A1A1E
Text Secondary      #6B6B75
Border              #E5E5EA
```

---

## 2. 타이포그래피

- 산세리프 계열 (Pretendard 권장 — 한글 가독성 + 무료 라이선스)
- **숫자(거리·시간·페이스)는 크고 굵게**: 결과가 "작품처럼 보이게" 하는 게 목적. 일반 UI 텍스트와 확실히 다른 웨이트 사용

| 토큰 | 크기 | 굵기 | 용도 |
|---|---|---|---|
| Display | 40sp | Bold | 결과 화면 거리 숫자(S-25) |
| Title1 | 24sp | Bold | 화면 타이틀, Route 이름 |
| Title2 | 20sp | SemiBold | 섹션 헤더 |
| Body | 16sp | Regular | 본문 |
| Caption | 13sp | Regular | 보조 텍스트 |
| **Nav Large** | **28sp** | **Bold** | **러닝 중(S-21) 거리/시간/페이스 — 아래 3항 참조** |

### 러닝 중 화면(S-21) 전용 규칙 — 예외 적용

- 최소 폰트 **24sp 이상** (팔 흔들며 봐도 읽혀야 함)
- 명암비 **4.5:1 이상**
- 다크 배경 고정 (야간 대비, Background Dark 사용)
- 색상만으로 정보 구분 금지 — 계획경로/실제경로는 실선·점선 병행(색으로만 구분 X)

---

## 3. 컴포넌트 규칙

### 3.1 카드
- 라운드: **20px**
- 그림자: 소프트 섀도우 (Elevation 낮게, `y:2 blur:12 opacity:0.06`)
- Material 3 기본 컴포넌트 그대로 쓰지 말고 커스텀 (기본 Material 느낌이 나면 브랜드감이 죽음)

### 3.2 GPS 실루엣 썸네일 (`RouteThumbnailView`)
- **모든 코스 카드에 필수** — 제네릭 아이콘·클립아트 절대 금지 (원문 원칙, 가장 중요한 규칙)
- 서버가 내려주는 GeoJSON LineString을 Canvas로 렌더링
- 배경: Primary Light(#EAE8FF), 선: Primary(#4A3AFF) 또는 그라디언트
- 정사각형 비율 고정, 자동 bounding-box 계산 후 여백 15% 패딩

### 3.3 탭 타깃
- 최소 **56dp** (장갑 착용 대비, 러닝 중 오작동 방지)

### 3.4 버튼
- Primary 버튼: 배경 Primary(#4A3AFF), 텍스트 White, 라운드 16px
- 러닝 중 화면의 [일시정지] 버튼만 예외: 큰 원형(80dp), 다른 UI와 확실히 구분되는 형태

---

## 4. 다크모드

- 러닝 중 화면(S-21)은 **항상 다크모드 고정** (라이트/다크 시스템 설정과 무관, 야간 대비 우선)
- 그 외 화면은 시스템 설정 따라감 (라이트 기본)

---

## 5. Compose 토큰화 가이드 (개발 시 참조)

```kotlin
object DallimColors {
    val Primary = Color(0xFF4A3AFF)
    val PrimaryDark = Color(0xFF2E22C7)
    val PrimaryLight = Color(0xFFEAE8FF)
    val GradientStart = Color(0xFF4A3AFF)
    val GradientEnd = Color(0xFFFF6B4A)

    val RouteDiscovery = Color(0xFF9E9E9E)
    val RouteVerified = Color(0xFF4A90D9)
    val RouteUnderReview = Color(0xFFF5A623)

    val Background = Color(0xFFFAFAF8)
    val BackgroundDark = Color(0xFF0F0F14)
    val TextPrimary = Color(0xFF1A1A1E)
    val TextSecondary = Color(0xFF6B6B75)
}

val DallimGradient = Brush.linearGradient(
    colors = listOf(DallimColors.GradientStart, DallimColors.GradientEnd)
)
```

> LOOP 0(프로젝트 기반)에서 `core-ui` 모듈에 위 토큰을 가장 먼저 세팅하고, 이후 모든 화면은 하드코딩된 색상값 없이 이 토큰만 참조하도록 한다.
