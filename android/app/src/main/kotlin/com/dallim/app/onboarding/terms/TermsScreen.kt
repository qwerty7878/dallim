package com.dallim.app.onboarding.terms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.ui.components.DallimCheckboxRow
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.Spacing

/** S-03 약관 동의 — 필수 3종 + 마케팅 1종 개별 동의, 필수 항목 모두 체크해야 다음으로 진행. */
@Composable
fun TermsRoute(
    onAgreed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TermsViewModel = hiltViewModel(),
) {
    val terms by viewModel.terms.collectAsStateWithLifecycle()

    TermsScreen(
        terms = terms,
        onToggle = viewModel::onToggle,
        onToggleAll = viewModel::onToggleAll,
        onAgreed = onAgreed,
        modifier = modifier,
    )
}

@Composable
private fun TermsScreen(
    terms: List<TermItem>,
    onToggle: (String, Boolean) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onAgreed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allChecked = terms.all { it.checked }
    val requiredChecked = terms.filter { it.required }.all { it.checked }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.ScreenHorizontal),
    ) {
        Text(
            text = "약관에 동의해주세요",
            style = com.dallim.ui.theme.DallimTypography.Title1,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.xxl, bottom = Spacing.xl),
        )

        DallimCheckboxRow(
            checked = allChecked,
            onCheckedChange = onToggleAll,
            label = "전체 동의",
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DallimColors.Border)
                .padding(vertical = Spacing.sm),
        )

        Column(modifier = Modifier.weight(1f)) {
            terms.forEach { term ->
                DallimCheckboxRow(
                    checked = term.checked,
                    onCheckedChange = { checked -> onToggle(term.id, checked) },
                    label = term.label,
                    required = term.required,
                )
            }
        }

        DallimPrimaryButton(
            text = "동의하고 계속하기",
            onClick = onAgreed,
            enabled = requiredChecked,
            modifier = Modifier.padding(bottom = Spacing.xl),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TermsScreenPreview() {
    DallimTheme {
        TermsScreen(
            terms = listOf(
                TermItem("tos", "서비스 이용약관", required = true, checked = true),
                TermItem("privacy", "개인정보 처리방침", required = true, checked = true),
                TermItem("location", "위치정보 이용약관", required = true, checked = false),
                TermItem("marketing", "마케팅 정보 수신", required = false, checked = false),
            ),
            onToggle = { _, _ -> },
            onToggleAll = {},
            onAgreed = {},
        )
    }
}
