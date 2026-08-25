package com.dallim.app.onboarding.permission

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.Spacing

/**
 * S-05 권한 요청 — 포그라운드 위치 권한만 요청한다. **백그라운드 위치 권한은 여기서 요청하지
 * 않는다** — docs/01-feature-spec.md 1.1: "백그라운드 위치 권한은 S-05에서 요청하지 않고,
 * S-20(러닝 준비)에서 최초 요청한다". 허용/거부 결과와 무관하게 다음 화면(S-06)으로 진행한다.
 */
@Composable
fun PermissionRoute(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // 허용/거부 결과와 무관하게 다음 화면으로 진행 (spec: "허용/거부 무관 진행 → S-06").
        onContinue()
    }

    // This screen itself doubles as the "사전 설명" (rationale) UI required before the system
    // dialog — tapping the primary button is what actually triggers the OS permission prompt.
    PermissionScreen(
        onAllowClick = {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        },
        onSkipClick = onContinue,
        modifier = modifier,
    )
}

@Composable
private fun PermissionScreen(
    onAllowClick: () -> Unit,
    onSkipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .padding(horizontal = Spacing.ScreenHorizontal),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(DallimColors.PrimaryLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = DallimColors.Primary,
                    modifier = Modifier.size(48.dp),
                )
            }

            Text(
                text = "위치 정보 접근을 허용해주세요",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = DallimColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            Text(
                text = "달린 경로를 GPS로 기록하고 그림을 완성하려면\n위치 정보 접근 권한이 필요해요.",
                fontSize = 16.sp,
                color = DallimColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        DallimPrimaryButton(text = "위치 권한 허용하기", onClick = onAllowClick)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DallimTextButton(text = "나중에 하기", onClick = onSkipClick)
        }
        Box(modifier = Modifier.padding(bottom = Spacing.xl))
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionScreenPreview() {
    DallimTheme {
        PermissionScreen(onAllowClick = {}, onSkipClick = {})
    }
}
