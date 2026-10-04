package com.dallim.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/**
 * Design tokens — docs/03-design-system.md 섹션 1/5.
 * Do NOT hardcode color hex values anywhere outside this file (CLAUDE.md rule 6).
 *
 * **2026-10-05 전면 다크 전환.** 사용자 피드백("너무 AI스럽다, 실서비스처럼")으로 라이트 팔레트를
 * 폐기하고 앱 전체를 다크 고정으로 바꿨다. 직전 라운드(2026-10-04)에서 색·그림자·그라데이션을
 * 걷어낸 결과 흑백 와이어프레임처럼 보인다는 것이 문제였는데, 원인은 장식이 많아서가 아니라
 * **지면이 전부 흰색이라 이 앱의 주인공인 GPS 궤적이 묻혔던 것**이다. 어두운 지면 위에서 궤적
 * 하나만 밝게 빛나는 구조로 바꾼다(docs/03-design-system.md §1.5, §4).
 *
 * 색은 "어두운 중성 지면 + 단 하나의 밝은 보라 액센트"로 고정한다. 액센트를 쓸 곳은
 * GPS 궤적 / 선택 상태 / 진행률 / 링크뿐이고, 주 버튼은 액센트가 아니라 [ActionFill](밝은 면)이다.
 */
object DallimColors {
    // --- 지면(면) ---
    /** 앱 바탕. 순흑이 아니라 아주 약간 푸른 기가 도는 먹색 — 순흑은 OLED에서 경계가 사라져 싸구려로 보인다. */
    val Background = Color(0xFF09090D)
    /** 카드/바텀시트/상단바 면. 바탕보다 한 단계 밝다. */
    val Surface = Color(0xFF121218)
    /** 입력창/칩/보조 블록 채움 — 테두리 없이 면 밝기만으로 구분한다. */
    val SurfaceMuted = Color(0xFF1A1A22)
    /** 눌림/선택/강조된 면(한 단계 더 띄울 때). */
    val SurfaceElevated = Color(0xFF24242E)
    /** 러닝 중 화면(S-21) 바탕 — 전면 다크 전환 후 [Background]와 같다(토큰 이름은 호출부 호환용). */
    val BackgroundDark = Background

    val Border = Color(0xFF2C2C38)
    /** 리스트 구분선([Border]보다 옅음). */
    val Divider = Color(0xFF20202A)

    // --- 글자 ---
    val TextPrimary = Color(0xFFF3F3F7)
    val TextSecondary = Color(0xFF9A9AAA)
    /** 힌트/비활성 보조 텍스트. */
    val TextTertiary = Color(0xFF5F5F6D)

    // --- 액센트 (섹션 1.2) ---
    /**
     * 강조 — GPS 궤적, 선택 상태, 진행률, 링크. **버튼 배경에는 쓰지 않는다**([ActionFill] 참고).
     * 2026-10-05: 라이트용 로열 블루(#3558C8)는 어두운 지면에서 가라앉아 안 보여, 어두운 지도 위에서
     * 발광하듯 보이는 밝은 블루바이올렛으로 올렸다. 브랜드 색 계보(보라 계열)는 유지한다 —
     * 네이버 초록/카카오 노랑/토스 파랑/Strava 오렌지/컬리 플럼을 피한 1.1절 근거가 그대로 유효.
     */
    val Primary = Color(0xFF7C6BFF)
    /** 궤적 글로우/하이라이트 — [Primary]보다 밝다. */
    val PrimaryGlow = Color(0xFFA99BFF)
    /** 가라앉힌 액센트(비활성 궤적, 보조 지표). */
    val PrimaryDim = Color(0xFF4C3FA8)
    /** 액센트 틴트 면 — 선택된 칩/배지 배경처럼 "액센트가 깔린 어두운 면". */
    val PrimaryLight = Color(0xFF1C1836)
    /** 호출부 호환용(러닝 중 화면 등) — 다크 전환 후 [PrimaryDim]과 같다. */
    val PrimaryDark = PrimaryDim

    // --- 액션 버튼 ---
    /**
     * 주 버튼 배경 — 어두운 지면 위에서는 "밝은 면 + 어두운 글자"가 가장 강한 버튼이다.
     * 한 화면에 Primary 버튼 1개 규칙은 그대로(docs/04-ui-guide.md §2).
     */
    val ActionFill = Color(0xFFF3F3F7)
    /** [ActionFill] 위 글자/아이콘. */
    val OnActionFill = Color(0xFF09090D)
    /** [Primary] 위 글자/아이콘. */
    val OnPrimary = Color(0xFF09090D)

    /** 지도 오버레이 전용 순백(마커 채움/궤적 외곽선) — UI 텍스트에는 [TextPrimary]를 쓴다. */
    val White = Color(0xFFFFFFFF)

    // 그라데이션 폐지는 유지(2026-10-04). 두 토큰이 같은 색이라 기존 호출부가 자동으로 단색이 된다.
    val GradientStart = Primary
    val GradientEnd = Primary

    /** 궤적의 도착점/현재 위치 점 — 출발점(흰 점)과 구분되는 단 하나의 보조색. */
    val TrailEnd = Color(0xFFFF7A5C)

    // --- Route 상태 컬러 (섹션 1.3) — 어두운 지면 대비로 전부 한 단계 밝게 ---
    val RouteDiscovery = Color(0xFF8A8A96) // 중성 회색 — 아직 미검증
    val RouteVerified = Color(0xFF5AA9FF)
    val RoutePopular = Primary
    val RouteUnderReview = Color(0xFFFFB340)

    // --- 시맨틱 컬러 (섹션 1.4) ---
    val Success = Color(0xFF3DDC84)
    val Warning = Color(0xFFFFB340)
    val Error = Color(0xFFFF5C5C)

    /**
     * 공유 카드(S-26) 전용 팔레트. 공유 카드는 **앱 밖으로 나가는 이미지**라 앱 테마(다크 고정)와
     * 독립적인 자체 라이트/다크 선택지를 갖는다 — 사용자가 고른 배경이 그대로 결과 이미지가 돼야 한다.
     *
     * 2026-10-05: 전까지는 카드가 앱 토큰(`Background`/`TextPrimary`/`Surface`)을 그대로 썼는데,
     * 앱이 다크로 뒤집히면서 **"라이트"를 골라도 카드가 다크로 렌더되는** 버그가 됐다. 그래서 앱 토큰과
     * 분리해 여기에 고정값으로 박는다 — 앱 팔레트를 또 바꿔도 공유 카드는 영향받지 않는다.
     */
    object ShareCard {
        val LightBackground = Color(0xFFFFFFFF)
        val LightText = Color(0xFF1A1A1E)
        val LightTextSecondary = Color(0xFF6B6B75)
        val LightMapPlaceholder = Color(0xFFF1F1F4)

        val DarkBackground = Color(0xFF0F0F14)
        val DarkText = Color(0xFFF3F3F7)
        val DarkTextSecondary = Color(0xFF9A9AAA)
        val DarkMapPlaceholder = Color(0xFF24242E)
    }
}

/** 그라데이션 폐지(2026-10-04) — 이름은 호환용으로 남기고 단색 [Brush]를 돌려준다. */
val DallimGradient: Brush = SolidColor(DallimColors.Primary)
