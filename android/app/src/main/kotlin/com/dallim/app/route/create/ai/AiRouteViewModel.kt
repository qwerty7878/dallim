package com.dallim.app.route.create.ai

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.onboarding.firstroute.CurrentLocationProvider
import com.dallim.app.onboarding.profile.ComfortablePace
import com.dallim.network.route.PlaceSearchItem
import com.dallim.network.route.RouteApi
import com.dallim.network.route.RouteDiscoveryRequest
import com.dallim.network.route.RouteDiscoveryResponseBody
import com.dallim.network.route.RouteWaypoint
import com.dallim.ui.components.GeoPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** `POST /routes/discovery`가 일일 무료 횟수 소진 시 돌려주는 에러코드 (docs/02-api-spec.md 8.4). */
private const val DISCOVERY_QUOTA_EXCEEDED = "DISCOVERY_QUOTA_EXCEEDED"

data class AiRouteUiState(
    val targetDistanceKm: Float = 5f,
    val pace: ComfortablePace? = null,
    /** 처음 지도를 띄울 위치 — 현재 GPS 위치, 못 가져오면 null(네이버맵 기본 위치로 뜬다). */
    val initialCenter: GeoPoint? = null,
    /**
     * "꼭 지나갈 장소" — 지도 롱프레스로 최대 [MAX_REQUIRED_WAYPOINTS]곳 지정
     * (docs/02-api-spec.md 11.2). 이미 꽉 찼으면 새 롱프레스는 무시되고, 마커를 탭하면 그 항목만
     * 삭제된다. 비어 있으면 생성 요청에서 필드를 빈 리스트로 보낸다.
     */
    val requiredWaypoints: List<GeoPoint> = emptyList(),
    /**
     * "LOOP"(기본값, 출발점으로 돌아옴) | "POINT_TO_POINT"(지정한 목적지까지만 감,
     * docs/02-api-spec.md 11.4) | "SHAPE"(등록된 모양 윤곽을 따라 달림, docs/02-api-spec.md 13장).
     * LOOP/POINT_TO_POINT는 [requiredWaypoints]와 함께 쓸 수 있다(11.6) — 목적지를 지정한 채로
     * 꼭 지나갈 장소도 함께 켤 수 있다. SHAPE는 이번 라운드엔 [requiredWaypoints]/[destination]과
     * 조합하지 않는다(13.4) — SHAPE로 전환하면 둘 다 비우고, 반대로 LOOP/POINT_TO_POINT로
     * 전환하면 [shapeType]을 비운다. LOOP는 destination 개념이 없으므로 LOOP로 되돌리면
     * [destination]도 비워진다.
     */
    val mode: String = "LOOP",
    /** POINT_TO_POINT일 때 지도 롱프레스로 지정하는 목적지 — 한 곳만 허용. */
    val destination: GeoPoint? = null,
    /** SHAPE 모드에서 선택한 모양 — "HEART" | "CIRCLE" | "DROP", null이면 미선택 (13.3, STAR는 v1 제외). */
    val shapeType: String? = null,
    /** SHAPE 모드 크기 선택 — "S" | "M"(기본값) | "L", 모양 템플릿의 절대 스케일을 고정한다(13.1.1). */
    val shapeSize: String = "M",
    val isGenerating: Boolean = false,
    val result: RouteDiscoveryResponseBody? = null,
    val errorMessage: String? = null,
    /** "저장" 탭 시 보여줄 스낵바 메시지 — 실제 저장 API는 이번 라운드 범위 밖(docs/02-api-spec.md 8.3). */
    val snackbarMessage: String? = null,
    /**
     * "꼭 지나갈 장소" 검색 입력값 (docs/02-api-spec.md 12장). `GET /routes/places/search`는 항상
     * 200에 `items`만 내려주므로 "결과 없음"과 "업스트림 소프트 실패"는 화면에서 구분하지 않는다.
     */
    val placeSearchQuery: String = "",
    val placeSearchResults: List<PlaceSearchItem> = emptyList(),
    val isSearchingPlaces: Boolean = false,
    /**
     * 오늘 남은 무료(+보너스) 생성 횟수 (docs/02-api-spec.md 8.4). 화면 진입 시
     * `GET /routes/discovery/quota`로 채우고, 생성 성공 시 그 응답의 `remainingToday`로 갱신한다.
     * null이면 아직 못 불러온 상태 — 이때는 배너 자체를 숨긴다(실패해도 별도 에러 UI 없음).
     */
    val quotaRemainingToday: Int? = null,
    /** v1.3 S-12 "일 무료 횟수 소진" 모달 표시 여부 — 403 DISCOVERY_QUOTA_EXCEEDED 응답 시 켠다. */
    val showQuotaExceededModal: Boolean = false,
    /** 리워드 광고 로드/표시 중 — 모달의 "[광고 보고 1회 더]" 버튼 중복 탭 방지용. */
    val isWatchingAd: Boolean = false,
)

/**
 * S-45 AI 자동 생성 — 현재 위치 + 목표 거리(+선택 페이스)로 `POST /routes/discovery`를 호출해
 * 순환 코스를 만든다 (docs/02-api-spec.md 8.2). 매 호출마다 서버가 반지름에 무작위 편차를 주므로
 * "다시 생성"을 누르면 같은 입력이라도 다른 코스가 나온다 — 이게 "수정" 체감을 준다.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class AiRouteViewModel @Inject constructor(
    private val routeApi: RouteApi,
    private val locationProvider: CurrentLocationProvider,
    private val rewardedAdController: DiscoveryRewardedAdController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiRouteUiState())
    val uiState: StateFlow<AiRouteUiState> = _uiState.asStateFlow()

    /** 장소 검색어 debounce 입력 — 매 키 입력마다 API를 호출하지 않기 위한 클라이언트 책임(12.3). */
    private val placeSearchQueryInput = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val location = locationProvider.getCurrentLocation()
            if (location != null) {
                _uiState.update { it.copy(initialCenter = GeoPoint(lng = location.second, lat = location.first)) }
            }
        }
        viewModelScope.launch {
            placeSearchQueryInput
                .debounce(400)
                .distinctUntilChanged()
                .collect { query -> searchPlaces(query) }
        }
        viewModelScope.launch { loadQuota() }
    }

    /**
     * 화면 진입 시 "오늘 남은 무료 탐색 n회" 배너용 (docs/02-api-spec.md 8.4). 실패해도 배너를
     * 그냥 숨기면 되는 부가 정보라 별도 에러 UI 없이 조용히 무시한다.
     */
    private suspend fun loadQuota() {
        val result = safeApiCall { routeApi.getDiscoveryQuota() }
        if (result is UiResult.Success) {
            _uiState.update { it.copy(quotaRemainingToday = result.data.remainingToday) }
        }
    }

    fun onDistanceChange(km: Float) {
        _uiState.update { it.copy(targetDistanceKm = km) }
    }

    fun onPaceSelected(pace: ComfortablePace?) {
        _uiState.update { it.copy(pace = if (it.pace == pace) null else pace) }
    }

    /** 지도 롱프레스 — 이미 [MAX_REQUIRED_WAYPOINTS]개면 무시(docs/02-api-spec.md 11.2). */
    fun onWaypointLongPress(point: GeoPoint) {
        _uiState.update {
            if (it.requiredWaypoints.size >= MAX_REQUIRED_WAYPOINTS) it else it.copy(requiredWaypoints = it.requiredWaypoints + point)
        }
    }

    /** 마커 탭 — 그 지점 하나만 삭제한다. */
    fun onWaypointClick(point: GeoPoint) {
        _uiState.update { it.copy(requiredWaypoints = it.requiredWaypoints - point) }
    }

    /** 검색창 입력 — 실제 API 호출은 [placeSearchQueryInput] debounce 이후에만 일어난다. */
    fun onPlaceSearchQueryChange(query: String) {
        _uiState.update { it.copy(placeSearchQuery = query) }
        placeSearchQueryInput.value = query
    }

    private suspend fun searchPlaces(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(placeSearchResults = emptyList(), isSearchingPlaces = false) }
            return
        }

        _uiState.update { it.copy(isSearchingPlaces = true) }
        val center = _uiState.value.initialCenter
        val result = safeApiCall { routeApi.searchPlaces(query = query, lat = center?.lat, lng = center?.lng) }

        // Stale-response guard: 검색어가 그 사이 또 바뀌었으면 이 응답은 버린다.
        if (_uiState.value.placeSearchQuery != query) return
        _uiState.update { current ->
            when (result) {
                is UiResult.Success -> current.copy(isSearchingPlaces = false, placeSearchResults = result.data.items)
                is UiResult.Error -> current.copy(isSearchingPlaces = false, placeSearchResults = emptyList())
                UiResult.Loading -> current
            }
        }
    }

    /**
     * 검색 결과 선택 — [onWaypointLongPress]와 동일한 로직(최대 [MAX_REQUIRED_WAYPOINTS]개 상한)을
     * 그대로 재사용한다. 선택 후에는 검색창/결과 목록을 닫는다.
     */
    fun onPlaceSearchResultClick(item: PlaceSearchItem) {
        onWaypointLongPress(GeoPoint(lng = item.lng, lat = item.lat))
        placeSearchQueryInput.value = ""
        _uiState.update { it.copy(placeSearchQuery = "", placeSearchResults = emptyList(), isSearchingPlaces = false) }
    }

    /**
     * LOOP <-> POINT_TO_POINT <-> SHAPE 전환. LOOP/POINT_TO_POINT 사이에서는
     * [requiredWaypoints]가 그대로 유지된다(docs/02-api-spec.md 11.6) — LOOP로 되돌릴 때는
     * destination 개념이 없는 모드이므로 [destination]만 비운다. SHAPE는 이번 라운드엔
     * [requiredWaypoints]/[destination]과 조합하지 않으므로(13.4) SHAPE로 들어갈 때 둘 다 비우고,
     * SHAPE에서 LOOP/POINT_TO_POINT로 나갈 때는 [shapeType]을 비운다.
     */
    fun onModeSelected(mode: String) {
        _uiState.update {
            when (mode) {
                "POINT_TO_POINT" -> it.copy(mode = mode, shapeType = null)
                "SHAPE" -> it.copy(mode = mode, requiredWaypoints = emptyList(), destination = null)
                else -> it.copy(mode = "LOOP", destination = null, shapeType = null)
            }
        }
    }

    /** SHAPE 모드 모양 그리드 탭 — 다시 탭해도 선택은 유지(토글 아님, 하나는 항상 골라야 생성 가능). */
    fun onShapeTypeSelected(shapeType: String) {
        _uiState.update { it.copy(shapeType = shapeType) }
    }

    /** "짧은/보통/긴 코스" 크기 선택 (docs/02-api-spec.md 13.1.1). */
    fun onShapeSizeSelected(size: String) {
        _uiState.update { it.copy(shapeSize = size) }
    }

    /** 목적지 지정 — 다시 롱프레스하면 새 위치로 교체된다(한 곳만 허용). */
    fun onDestinationLongPress(point: GeoPoint) {
        _uiState.update { it.copy(destination = point) }
    }

    fun onDestinationClearClick() {
        _uiState.update { it.copy(destination = null) }
    }

    private companion object {
        const val MAX_REQUIRED_WAYPOINTS = 3
    }

    /** "코스 생성" / "다시 생성" 공용 — 같은 입력이라도 서버가 매번 다른 반지름 편차를 준다. */
    fun onGenerateClick() {
        if (_uiState.value.isGenerating) return

        val stateBeforeLaunch = _uiState.value
        if (stateBeforeLaunch.mode == "POINT_TO_POINT" && stateBeforeLaunch.destination == null) {
            _uiState.update { it.copy(errorMessage = "지도를 길게 눌러 목적지를 먼저 지정해주세요.") }
            return
        }
        if (stateBeforeLaunch.mode == "SHAPE" && stateBeforeLaunch.shapeType == null) {
            _uiState.update { it.copy(errorMessage = "모양을 먼저 선택해주세요.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, errorMessage = null) }

            val location = locationProvider.getCurrentLocation()
            if (location == null) {
                _uiState.update {
                    it.copy(isGenerating = false, errorMessage = "현재 위치를 확인할 수 없어요. 위치 권한을 확인해주세요.")
                }
                return@launch
            }

            val state = _uiState.value
            val request = RouteDiscoveryRequest(
                startLng = location.second,
                startLat = location.first,
                targetDistanceKm = state.targetDistanceKm.toDouble(),
                pace = state.pace?.apiValue,
                requiredWaypoints = state.requiredWaypoints.map { RouteWaypoint(lat = it.lat, lng = it.lng) },
                mode = state.mode,
                endLat = state.destination?.lat,
                endLng = state.destination?.lng,
                shapeType = if (state.mode == "SHAPE") state.shapeType else null,
                // 백엔드 DiscoveryRequest.size는 non-null(default "M")이라 null을 명시적으로 보내면
                // (Json encodeDefaults=true라 항상 필드 자체는 실림) 역직렬화가 400으로 깨진다
                // (docs/02-api-spec.md 13.1.1 — SHAPE가 아닐 때는 어차피 무시되는 필드이므로 기본값을
                // 그대로 채워 보낸다). 이번 라운드(S-56) 범위 밖의 기존 버그 수정.
                size = if (state.mode == "SHAPE") state.shapeSize else "M",
            )
            val result = safeApiCall { routeApi.discoverRoute(request) }

            _uiState.update { current ->
                when (result) {
                    is UiResult.Success -> current.copy(
                        isGenerating = false,
                        result = result.data,
                        errorMessage = null,
                        quotaRemainingToday = result.data.remainingToday,
                    )
                    is UiResult.Error -> if (result.code == DISCOVERY_QUOTA_EXCEEDED) {
                        // v1.3 S-12 "일 무료 횟수 소진" 모달로 안내 — 화면 상단 에러 텍스트는 띄우지 않는다.
                        current.copy(isGenerating = false, showQuotaExceededModal = true)
                    } else {
                        current.copy(isGenerating = false, errorMessage = result.message)
                    }
                    UiResult.Loading -> current
                }
            }
        }
    }

    fun onQuotaExceededModalDismiss() {
        _uiState.update { it.copy(showQuotaExceededModal = false) }
    }

    /**
     * 모달의 `[광고 보고 1회 더]` — 리워드 광고를 로드/표시하고, 끝까지 봐서 리워드를 받으면
     * `unlockDiscoveryReward()`로 쿼터를 +2 채운 뒤 같은 입력으로 [onGenerateClick]을 재시도한다.
     * 로드 실패/중간에 닫음/충전 API 실패는 전부 스낵바 한 줄로만 안내하고 모달은 그대로 유지한다
     * (과한 에러 UI 금지 — 사용자가 다시 탭하면 된다).
     */
    fun onWatchAdClick(activity: Activity) {
        if (_uiState.value.isWatchingAd) return
        _uiState.update { it.copy(isWatchingAd = true) }

        rewardedAdController.showAd(
            activity = activity,
            onRewardEarned = {
                viewModelScope.launch {
                    val result = safeApiCall { routeApi.unlockDiscoveryReward() }
                    when (result) {
                        is UiResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isWatchingAd = false,
                                    showQuotaExceededModal = false,
                                    quotaRemainingToday = result.data.remainingToday,
                                )
                            }
                            onGenerateClick()
                        }
                        is UiResult.Error -> _uiState.update {
                            it.copy(isWatchingAd = false, snackbarMessage = "쿼터 충전에 실패했어요. 다시 시도해주세요.")
                        }
                        UiResult.Loading -> Unit
                    }
                }
            },
            onFailedOrDismissed = {
                _uiState.update {
                    it.copy(isWatchingAd = false, snackbarMessage = "광고를 불러오지 못했어요. 잠시 후 다시 시도해주세요.")
                }
            },
        )
    }

    /** "저장" — 코스를 실제로 저장하는 API는 이번 라운드 범위 밖(docs/02-api-spec.md 8.3). */
    fun onSaveClick() {
        _uiState.update { it.copy(snackbarMessage = "저장 기능은 준비 중이에요") }
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
