# 달림 — Compose UI 구현 지침 (04-ui-guide)

> **이 문서는 화면을 만들기 전에 반드시 읽는다.**
> `03-design-system.md`가 "무슨 값을 쓸지"라면, 이 문서는 **"어떻게 배치할지"** 를 정한다.

---

## 0. 진단 — UI가 이상하게 나오는 7가지 원인

생성된 Compose UI가 "어딘가 이상한데 뭐가 문제인지 모르겠는" 상태일 때, 원인은 거의 항상 이 목록 안에 있다. 작업 후 자가 점검용으로도 사용한다.

| # | 증상 | 원인 | 해결 |
|---|---|---|---|
| 1 | 어디서 본 듯한 밋밋한 앱 | Material3 기본 `Button`/`Card`를 그대로 사용 | §6의 커스텀 컴포넌트만 사용 |
| 2 | 정렬이 미묘하게 안 맞음 | `13.dp`, `7.dp` 같은 임의값 사용 | §1의 8dp 스케일 값만 사용 |
| 3 | 화면이 시끄럽고 산만함 | 색을 너무 많이 씀, 그라디언트 남발 | §3의 90/5/5 규칙 |
| 4 | 답답하고 촘촘함 | 카드 안에 카드, 테두리 안에 테두리 | §4 중첩 금지 |
| 5 | 아마추어 같음 | 모든 요소를 가운데 정렬 | §5 좌측 정렬 기본 |
| 6 | 조잡함 | 이모지를 아이콘 대용으로 사용 | §7 벡터 아이콘만 |
| 7 | 뭘 봐야 할지 모르겠음 | 전부 같은 크기·굵기 (위계 없음) | §2 위계 규칙 |

---

## 1. 간격 — 8dp 스케일 (임의값 절대 금지)

```kotlin
object Spacing {
    val xs = 4.dp     // 아이콘과 라벨 사이
    val sm = 8.dp     // 요소 간 기본
    val md = 16.dp    // 카드 내부 패딩
    val lg = 24.dp    // 블록 간
    val xl = 32.dp    // 섹션 간
    val xxl = 48.dp   // 화면 상하 여백
}
```

**고정 규칙**

| 위치 | 값 |
|---|---|
| 화면 좌우 패딩 | `20.dp` (이것만 예외적 고정값) |
| 섹션과 섹션 사이 | `Spacing.xl` (32) |
| 카드 내부 패딩 | `Spacing.md` (16) |
| 리스트 아이템 간 | `12.dp` |
| 텍스트 줄 사이 | `Spacing.xs`~`sm` |

> **여백을 두려워하지 말 것.** 화면이 이상해 보일 때 열에 아홉은 요소가 부족한 게 아니라 **여백이 부족한 것**이다. 요소를 추가하기 전에 여백부터 늘려본다.

---

## 2. 위계 — 한 화면에 강조는 하나

- **Primary 버튼은 화면당 1개.** 두 개 필요하면 하나는 Secondary(아웃라인) 또는 텍스트 버튼으로.
- **텍스트 레벨은 3단계까지만**: Title / Body / Caption. 4단계 이상 쓰면 위계가 무너진다.
- **강조색(Primary)은 화면당 1~2곳에만.** 버튼과 진행률에 이미 썼으면 아이콘엔 쓰지 않는다.
- 크기로 위계를 만들되, **크기·굵기·색을 동시에 다르게 하지 않는다.** 둘 중 하나만.

```
❌ 나쁜 예: 제목 24sp Bold 코랄 / 본문 16sp Medium 보라 / 캡션 14sp Bold 회색
✅ 좋은 예: 제목 24sp Bold 검정 / 본문 16sp Regular 검정 / 캡션 13sp Regular 회색
```

---

## 3. 색 — 90 / 5 / 5 규칙

| 비율 | 용도 |
|---|---|
| **90%** | 무채색 (배경 `#FAFAF8`, 텍스트 `#1A1A1E`, 보조텍스트 `#6B6B75`, 보더 `#E5E5EA`) |
| **5%** | Primary `#4A3AFF` — 버튼, 진행률, 선택 상태, GPS 궤적 |
| **5%** | 상태색 (성공/경고/에러, Route 상태 뱃지) |

**그라디언트는 아래 4곳에만.** 그 외 어디에도 쓰지 않는다.
1. 결과 화면(S-25)의 GPS 그림
2. 앱 아이콘
3. 스플래시/온보딩
4. 공유 카드 배경 옵션 중 하나

```
❌ 절대 금지: 카드마다 다른 색 / 버튼 그라디언트 / 배경 전체 그라디언트 / 텍스트 그라디언트
```

> 회색 팔레트를 제대로 쓰는 것만으로 UI의 절반은 해결된다. 색이 없어서 밋밋한 게 아니라, **회색 단계가 없어서** 밋밋한 것이다.

---

## 4. 카드와 컨테이너

- **라운드는 20dp로 통일.** 화면 안에서 16/12/8을 섞지 않는다.
- **중첩 금지**: 카드 안에 카드를 넣지 않는다. 구분이 필요하면 `Divider` 또는 여백으로.
- **테두리와 그림자를 동시에 쓰지 않는다.** 둘 중 하나.
- 그림자는 아주 약하게: `y:2 blur:12 alpha:0.06`. Compose 기본 `elevation`은 회색이 탁하게 껴서 싸구려로 보인다 — 직접 `shadow()` 지정.

```
❌  ┌─ Card ──────────────┐        ✅  ┌─ Card ──────────────┐
    │  ┌─ Card ────────┐  │            │  항목 A              │
    │  │  항목 A        │  │            │  ──────────────      │
    │  └───────────────┘  │            │  항목 B              │
    └─────────────────────┘            └─────────────────────┘
```

---

## 5. 정렬과 레이아웃

- **좌측 정렬이 기본.** 가운데 정렬은 다음 3곳에서만: 온보딩, 빈 상태(Empty), 로딩.
- 리스트 아이템은 `Row`로 [썸네일 | 텍스트블록 | 우측 액션] 3분할이 기본형.
- 썸네일은 **정사각 고정**(`aspectRatio(1f)`), 크기가 제각각이면 리스트 전체가 흔들려 보인다.
- 가로 스크롤은 좌측 패딩을 `contentPadding`으로 주고 첫 아이템이 화면 끝에 붙지 않게 한다.

### 화면 골격 (이 구조를 벗어나지 말 것)

```
[홈 S-10]                      [Route 상세 S-16]
┌──────────────────────┐       ┌──────────────────────┐
│ 날씨 한 줄            │       │                      │
│                      │       │      지도 (40%)       │
│ ┌── Hero 카드 ─────┐ │       │                      │
│ │  지도 미리보기    │ │       ├──────────────────────┤
│ │  🐳 고래 5.1km   │ │       │ 🐳 고래               │
│ │  [코스 보기]     │ │       │ VERIFIED · 12명 완주  │
│ └──────────────────┘ │       │                      │
│                      │       │ [스펙 2x4 그리드]     │
│ 최근 달림             │       │                      │
│ ▢ ▢ ▢  (가로스크롤)  │       ├──────────────────────┤
└──────────────────────┘       │ [혼자 달리기] [같이]  │ ← 하단 고정
                               └──────────────────────┘

[러닝 중 S-21 — 다크 고정]      [결과 S-25]
┌──────────────────────┐       ┌──────────────────────┐
│      지도 (40%)       │       │                      │
│   계획=점선 실제=실선   │       │    GPS 그림 (60%)     │
├──────────────────────┤       │    액자처럼 크게       │
│      ◯ 62%           │       │                      │
│   그림 완성도(거리 X)  │       ├──────────────────────┤
│   ← 120m 후 좌회전    │       │ 5.18 km   (40sp)     │
├──────────────────────┤       │ 34:38  6'41"/km      │
│ 3.2km  18:42  5'52"  │       ├──────────────────────┤
│  (28sp 이상)          │       │ Match 92% · 완주 97% │
├──────────────────────┤       ├──────────────────────┤
│        ( ⏸ )         │       │ [공유] [굿즈] [저장]  │
└──────────────────────┘       └──────────────────────┘
```

---

## 6. 커스텀 컴포넌트 (Material3 기본 대신 이것만 사용)

```kotlin
@Composable
fun DallimCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.06f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(DallimColors.Surface)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(Spacing.md),
        content = content
    )
}

@Composable
fun DallimPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)                      // 탭 타깃 최소 56dp
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) DallimColors.Primary
                else DallimColors.Primary.copy(alpha = 0.3f)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DallimSecondaryButton(/* 동일 구조, background 투명 + border 1.dp Primary */) { }

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = DallimColors.TextPrimary,
        modifier = Modifier.padding(bottom = Spacing.md)
    )
}
```

---

## 7. 아이콘

- **이모지를 아이콘으로 쓰지 않는다.** 벡터 아이콘(Material Icons 또는 커스텀 SVG)만.
- **예외**: 코스 이름의 `🐳 고래`처럼 **콘텐츠 데이터로서의 이모지는 허용**. UI 요소(버튼, 탭, 상태표시)에 쓰는 것만 금지.
- 크기 `24.dp` 통일, 스트로크 굵기 통일(Outlined 계열이면 전부 Outlined로).
- 아이콘과 라벨 간격은 `Spacing.xs`(4dp).

---

## 8. 이 앱의 시그니처 — GPS 선

달림의 UI에서 기억에 남아야 하는 단 하나는 **지도 위에 그려지는 GPS 궤적**이다.

- 코스 카드에는 **반드시** `RouteThumbnailView`(GeoJSON → Canvas 렌더)를 쓴다. 제네릭 아이콘·클립아트 절대 금지.
- 결과 화면의 GPS 그림은 **액자에 담긴 작품처럼** 크고 여백 있게. 이 화면만은 다른 곳보다 화려해도 된다.
- 나머지 화면은 조용하고 절제되게 — 시그니처가 돋보이려면 주변이 조용해야 한다.

---

## 9. 러닝 중 화면(S-21) 예외 규칙

일반 화면 규칙보다 **이쪽이 우선**한다.

- 다크 배경 고정 (시스템 설정 무관)
- 최소 폰트 **24sp**, 거리 숫자는 **28sp 이상**
- 명암비 4.5:1 이상
- 요소 수 최소화 — 지도/진행률/숫자3개/일시정지 버튼 외 아무것도 넣지 않는다
- 계획 경로와 실제 경로는 **색상뿐 아니라 실선/점선으로도** 구분 (색맹 접근성)
- 광고·프로모션·팝업 절대 금지

---

## 10. Compose 코드 규칙

- **Modifier 순서**: `size → padding → background → clip → clickable` (순서가 바뀌면 클릭 영역·배경이 어긋난다)
- **색상·크기 하드코딩 금지.** `DallimColors`, `Spacing` 토큰만 참조.
- 모든 Composable에 `modifier: Modifier = Modifier` 파라미터를 받는다.
- `@Preview`를 반드시 작성한다 — 미리보기 없이 만든 화면은 십중팔구 이상하다.
- 상태는 ViewModel에서 `StateFlow`로, Composable은 상태를 받기만 한다(무상태 우선).

---

## 11. 자가 점검 체크리스트 (화면 완성 후 필수)

- [ ] 8dp 배수가 아닌 간격값이 있는가? → 있으면 수정
- [ ] Primary 버튼이 2개 이상인가? → 하나로 줄이기
- [ ] 카드 안에 카드가 있는가? → 펴기
- [ ] 그라디언트를 허용된 4곳 외에 썼는가? → 제거
- [ ] 이모지를 UI 요소로 썼는가? → 벡터 아이콘으로 교체
- [ ] 가운데 정렬을 온보딩·빈상태·로딩 외에 썼는가? → 좌측 정렬로
- [ ] 텍스트 레벨이 4단계 이상인가? → 3단계로 줄이기
- [ ] 하드코딩된 `Color(...)` 또는 `.dp` 리터럴이 있는가? → 토큰으로 교체
- [ ] `@Preview`가 있는가?
- [ ] **여백을 한 단계 더 늘려봤는가?** (대부분 여기서 좋아진다)
