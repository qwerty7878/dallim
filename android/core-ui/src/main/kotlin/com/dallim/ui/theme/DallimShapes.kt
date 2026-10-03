package com.dallim.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Component shape/sizing tokens from docs/03-design-system.md 섹션 3.
 * Do not use Material3's default shapes directly for cards/buttons — 원문 원칙:
 * "Material 3 기본 컴포넌트 그대로 쓰지 말고 커스텀" (기본 Material 느낌이 나면 브랜드감이 죽음).
 */
object DallimShapes {
    /** 카드 라운드 (섹션 3.1). */
    val CardCorner = RoundedCornerShape(16.dp)

    /** Primary 버튼 라운드 (섹션 3.4). */
    val ButtonCorner = RoundedCornerShape(14.dp)

    /** 러닝 중 화면(S-21) [일시정지] 버튼 전용 — 큰 원형, 다른 UI와 확실히 구분 (섹션 3.4). */
    val PauseButtonSize = 80.dp

    /** 탭 타깃 최소 크기 — 장갑 착용 대비, 러닝 중 오작동 방지 (섹션 3.3). */
    val MinTapTarget = 56.dp
}

val DallimMaterialShapes = Shapes(
    medium = DallimShapes.CardCorner,
    small = DallimShapes.ButtonCorner,
)
