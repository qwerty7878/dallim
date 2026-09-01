package com.dallim.app.onboarding.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * S-04 아바타 6종. docs/02-api-spec.md 예시(`"avatarId": "avatar_03"`)에 id 포맷만 나와있고
 * 실제 일러스트 에셋은 스펙에 없다 — 실물 아바타 이미지 에셋이 준비되기 전까지, 서로 다른
 * Material 벡터 아이콘으로 6개를 구분한다(이모지/클립아트 금지 원칙, docs/04-ui-guide.md §7).
 * TODO(android-dev/design): 실제 일러스트 에셋이 나오면 이 아이콘들을 교체한다.
 */
data class AvatarOption(val id: String, val icon: ImageVector)

val avatarOptions = listOf(
    AvatarOption("avatar_01", Icons.Filled.Person),
    AvatarOption("avatar_02", Icons.Filled.Face),
    AvatarOption("avatar_03", Icons.Filled.EmojiPeople),
    AvatarOption("avatar_04", Icons.Filled.DirectionsRun),
    AvatarOption("avatar_05", Icons.Filled.SelfImprovement),
    AvatarOption("avatar_06", Icons.Filled.Accessibility),
)

/**
 * docs/02-api-spec.md 2장은 예시값(`UNDER_3_MONTHS`, `PACE_6_7`, `FEMALE`)만 보여줄 뿐 전체
 * enum 목록을 명세하지 않는다 — 아래는 그 예시에서 합리적으로 추론한 값 세트이며, 백엔드팀
 * 확정 시 조정이 필요할 수 있다.
 */
enum class RunningExperience(val apiValue: String, val label: String) {
    NONE("NONE", "이제 시작해요"),
    UNDER_3_MONTHS("UNDER_3_MONTHS", "3개월 미만"),
    MONTHS_3_TO_12("MONTHS_3_TO_12", "3개월 ~ 1년"),
    OVER_1_YEAR("OVER_1_YEAR", "1년 이상"),
}

enum class ComfortablePace(val apiValue: String, val label: String) {
    UNDER_5("PACE_UNDER_5", "5'00\" 이하"),
    PACE_5_6("PACE_5_6", "5'00\" ~ 6'00\""),
    PACE_6_7("PACE_6_7", "6'00\" ~ 7'00\""),
    PACE_7_8("PACE_7_8", "7'00\" ~ 8'00\""),
    OVER_8("PACE_OVER_8", "8'00\" 이상"),
}

/**
 * gender는 서버 저장 전용 — CLAUDE.md 규칙 2: 어떤 응답 DTO에도 노출하지 않는다. 이 화면은
 * 선택값을 [com.dallim.network.user.ProfileRequest]로 전송만 하고, 이후 어떤 화면에서도
 * 다시 표시하지 않는다.
 */
enum class Gender(val apiValue: String, val label: String) {
    FEMALE("FEMALE", "여성"),
    MALE("MALE", "남성"),
    PREFER_NOT_TO_SAY("PREFER_NOT_TO_SAY", "선택 안함"),
}
