package com.dallim.app.onboarding.terms

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** 약관 항목 하나. [required] 3종 + 마케팅(선택) 1종 — docs/01-feature-spec.md 1.1 S-03. */
data class TermItem(
    val id: String,
    val label: String,
    val required: Boolean,
    val checked: Boolean = false,
)

/**
 * S-03 약관 동의. docs/02-api-spec.md에는 약관 동의를 저장하는 엔드포인트가 없다 — 동의 여부를
 * 서버에 별도로 기록하는 API가 스펙에 없으므로(SPEC을 임의로 확장하지 않는다), 이 화면은 로컬
 * 상태로 체크박스만 관리하고 다음 화면(S-04)으로 진행 가능 여부만 게이트한다. 백엔드에 동의
 * 이력 저장 API가 추가되면 그때 호출을 붙인다.
 */
@HiltViewModel
class TermsViewModel @Inject constructor() : ViewModel() {

    private val _terms = MutableStateFlow(
        listOf(
            TermItem(id = "tos", label = "서비스 이용약관", required = true),
            TermItem(id = "privacy", label = "개인정보 처리방침", required = true),
            TermItem(id = "location", label = "위치정보 이용약관", required = true),
            TermItem(id = "marketing", label = "마케팅 정보 수신", required = false),
        ),
    )
    val terms: StateFlow<List<TermItem>> = _terms.asStateFlow()

    fun onToggle(id: String, checked: Boolean) {
        _terms.value = _terms.value.map { if (it.id == id) it.copy(checked = checked) else it }
    }

    fun onToggleAll(checked: Boolean) {
        _terms.value = _terms.value.map { it.copy(checked = checked) }
    }
}
