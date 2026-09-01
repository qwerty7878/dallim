package com.dallim.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.onboarding.firstroute.CurrentLocationProvider
import com.dallim.network.home.HomeApi
import com.dallim.network.home.HomeResponseBody
import com.dallim.network.user.SavedRouteItem
import com.dallim.network.user.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Success(
        val home: HomeResponseBody,
        val savedRoutesPreview: List<SavedRouteItem>,
        /**
         * 인사말용 닉네임. `GET /users/me` 조회 실패 또는 빈 닉네임(온보딩 프로필 설정 미완료)이면
         * null — 이 경우 화면은 인사말 없이 기존 "달림" 타이틀만 보여준다.
         */
        val nickname: String? = null,
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}

/**
 * S-10 홈 — Hero 카드(오늘의 달림), 최근 달림 3개, 저장 코스 리스트 (docs/01-feature-spec.md §1.2).
 *
 * `GET /home`은 오늘의 달림 추천을 위해 현재 위치(lat/lng)를 요구하므로(docs/02-api-spec.md 3장),
 * S-06과 같은 [CurrentLocationProvider] 단발성 조회기를 재사용한다 — 러닝 중 GPS 파이프라인
 * (ForegroundService/Room/WorkManager)과는 무관한 별개의 가벼운 헬퍼라 재사용에 문제 없다.
 * 위치 권한이 없거나 조회에 실패하면 서비스 지역(서울·경기) 내 기본 좌표(서울시청)로 폴백한다.
 *
 * "저장 코스 리스트"는 `GET /home` 응답에 포함되지 않으므로(그 응답엔 todaySketch/continueRoutes/
 * recentRuns만 있음), `GET /users/me/saved-routes`를 별도로 병렬 호출해 미리보기 몇 개만 붙인다.
 * 저장 코스 조회가 실패해도 홈 화면 자체는 깨지지 않도록 빈 리스트로 흡수한다.
 *
 * 상단 인사말(닉네임)을 위해 `GET /users/me`도 같은 방식으로 세 번째 병렬 요청으로 붙인다 — 이
 * 조회가 실패하거나 닉네임이 빈 문자열이어도(온보딩 프로필 설정 미완료) 홈 화면 전체는 깨지지
 * 않고 [HomeUiState.Success.nickname]이 null로 흡수되어 인사말만 생략된다(저장 코스와 동일한
 * 원칙). `UserMeResponseBody`에는 gender가 없으므로(CLAUDE.md 규칙 2) 별도 처리는 필요 없다.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeApi: HomeApi,
    private val userApi: UserApi,
    private val locationProvider: CurrentLocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val (lat, lng) = locationProvider.getCurrentLocation() ?: DEFAULT_SEOUL_LOCATION

            val homeDeferred = async { safeApiCall { homeApi.getHome(lat = lat, lng = lng) } }
            val savedDeferred = async {
                safeApiCall { userApi.getSavedRoutes(page = 0, size = SAVED_PREVIEW_SIZE) }
            }
            val meDeferred = async { safeApiCall { userApi.getMe() } }
            val homeResult = homeDeferred.await()
            val savedResult = savedDeferred.await()
            val meResult = meDeferred.await()

            _uiState.value = when (homeResult) {
                is UiResult.Success -> HomeUiState.Success(
                    home = homeResult.data,
                    savedRoutesPreview = (savedResult as? UiResult.Success)?.data?.items ?: emptyList(),
                    nickname = (meResult as? UiResult.Success)?.data?.nickname?.takeIf { it.isNotBlank() },
                )
                is UiResult.Error -> HomeUiState.Error(homeResult.message)
                UiResult.Loading -> HomeUiState.Loading
            }
        }
    }

    private companion object {
        /** 위치 권한 거부/조회 실패 시 폴백 좌표 — 서울시청 (서비스 지역: 서울·경기). */
        val DEFAULT_SEOUL_LOCATION = 37.5665 to 126.9780
        const val SAVED_PREVIEW_SIZE = 5
    }
}
