package com.dallim.app.career.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.network.racerecord.RaceRecordApi
import com.dallim.network.racerecord.RaceRecordItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MedalShelfUiState {
    data object Loading : MedalShelfUiState
    data class Success(val items: List<RaceRecordItem>) : MedalShelfUiState
    data class Error(val message: String) : MedalShelfUiState
}

/**
 * S-91 완주 메달 선반 — `GET /users/me/race-records`(docs/02-api-spec.md 15.2)를 연도 내림차순
 * 그대로 받아 화면에서 연도별로 묶는다(서버가 이미 정렬해서 내려주므로 `groupBy`의 순서 보존만
 * 신뢰하면 된다 — 별도 정렬 없음).
 *
 * S-90(등록/수정/삭제)에서 돌아왔을 때의 새로고침은 `MedalShelfRoute`의 `LaunchedEffect(Unit)`이
 * 이 화면 destination이 다시 컴포지션에 들어올 때마다 [load]를 호출하는 방식으로 처리한다 —
 * SavedStateHandle 결과 플래그를 relay하는 대신, Navigation-Compose가 destination을 떠났다
 * 돌아올 때 그 destination의 content 람다를 매번 새로 실행한다는 성질을 그대로 이용한다(이
 * ViewModel 인스턴스 자체는 back stack entry에 묶여 계속 살아있어 `init`은 다시 안 불리지만,
 * `LaunchedEffect(Unit)`은 다시 불린다).
 */
@HiltViewModel
class MedalShelfViewModel @Inject constructor(
    private val raceRecordApi: RaceRecordApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MedalShelfUiState>(MedalShelfUiState.Loading)
    val uiState: StateFlow<MedalShelfUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = MedalShelfUiState.Loading
            _uiState.value = when (val result = safeApiCall { raceRecordApi.getRaceRecords() }) {
                is UiResult.Success -> MedalShelfUiState.Success(result.data.items)
                is UiResult.Error -> MedalShelfUiState.Error(result.message)
                UiResult.Loading -> MedalShelfUiState.Loading
            }
        }
    }
}
