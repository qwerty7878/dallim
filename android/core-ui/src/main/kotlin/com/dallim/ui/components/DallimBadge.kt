package com.dallim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography

/**
 * 상태 배지의 "무게". 모든 상태를 똑같이 그리지 않는 것이 이 컴포넌트의 핵심이다 — 목록을 훑을 때
 * **지금 할 수 있는 것**만 눈에 들어와야 한다.
 */
enum class BadgeTone {
    /** 지금 참여/신청할 수 있거나 사용자가 알아야 할 상태 — 상태색 틴트 면 + 같은 색 글자. */
    ACTIVE,

    /** 이미 끝나 더 할 게 없는 상태(마감/종료/취소/만료) — 면도 점도 없이 조용한 회색 글자. */
    QUIET,
}

/**
 * 상태 표시 공통 컴포넌트.
 *
 * **2026-10-05 재설계** — 직전 버전은 "6dp 색 점 + 회색 글자"였는데(2026-10-04), 관리자 대시보드에서
 * 흔한 표시라 "AI가 만든 화면" 인상을 줬다. 더 근본적인 문제는 **모든 상태가 똑같은 무게로 그려져서**
 * (점 크기도 글자색도 동일) "접수중"과 "접수 마감"이 목록에서 구분되지 않았다는 점이다.
 *
 * 이제 [tone]으로 위계를 만든다 — [BadgeTone.ACTIVE]만 틴트 면으로 띄우고 [BadgeTone.QUIET]는
 * 가라앉힌다. 이는 목록 정렬("참여 가능한 것 우선", docs/02-api-spec.md 14.2/16.2/17.2) 및 끝난
 * 항목 알파 처리와 같은 방향이다.
 *
 * 2026-10-04에 폐기한 "파스텔 배경 알약"과는 다르다 — 그건 흰 지면 위에서 색이 바래 보이던 것이고,
 * 지금은 어두운 지면 위라 같은 틴트가 발광하는 태그로 읽힌다(docs/03-design-system.md §1.5).
 *
 * [background] 인자는 호환을 위해 남겨 두었으나 쓰지 않는다 — 면 색은 [foreground]에서 파생한다.
 */
@Composable
fun DallimBadge(
    label: String,
    foreground: Color,
    @Suppress("UNUSED_PARAMETER") background: Color = Color.Transparent,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.ACTIVE,
) {
    val active = tone == BadgeTone.ACTIVE
    Text(
        text = label,
        style = DallimTypography.Label.copy(fontWeight = FontWeight.SemiBold),
        color = if (active) foreground else DallimColors.TextTertiary,
        // QUIET도 같은 가로 패딩을 줘서 목록에서 ACTIVE 배지와 글자 오른쪽 끝이 어긋나지 않게 한다.
        modifier = modifier
            .clip(BadgeShape)
            .then(if (active) Modifier.background(foreground.copy(alpha = ACTIVE_FILL_ALPHA)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private val BadgeShape = RoundedCornerShape(6.dp)
private const val ACTIVE_FILL_ALPHA = 0.16f
