package com.dallim.app.route.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.network.user.SavedRouteItem
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * S-17 저장한 코스 (docs/01-feature-spec.md §1.2). [SavedRouteItem]에는 GeoJSON 썸네일 필드가
 * 없으므로(docs/02-api-spec.md 2장) 이 화면의 리스트 아이템은 [RouteThumbnailView] 없이 텍스트
 * 위주로 구성한다 — 데이터가 없다고 제네릭 아이콘으로 대체하지는 않는다(docs/04-ui-guide.md §8).
 */
@Composable
fun SavedRoutesRoute(
    onBackClick: () -> Unit,
    onRouteClick: (routeId: String) -> Unit,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedRoutesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SavedRoutesScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRouteClick = onRouteClick,
        onExploreClick = onExploreClick,
        onUnsaveClick = viewModel::onUnsave,
        onRetryClick = viewModel::retry,
        onLoadNextPage = viewModel::loadNextPage,
        modifier = modifier,
    )
}

@Composable
private fun SavedRoutesScreen(
    uiState: SavedRoutesUiState,
    onBackClick: () -> Unit,
    onRouteClick: (String) -> Unit,
    onExploreClick: () -> Unit,
    onUnsaveClick: (String) -> Unit,
    onRetryClick: () -> Unit,
    onLoadNextPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadNextPage() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = DallimColors.TextPrimary,
                )
            }
            Text(text = "저장한 코스", style = DallimTypography.Title1, color = DallimColors.TextPrimary)
        }

        when {
            uiState.isLoadingInitial -> DallimLoadingState(modifier = Modifier.weight(1f))
            uiState.errorMessage != null && uiState.items.isEmpty() -> DallimErrorState(
                title = "저장한 코스를 불러오지 못했어요",
                description = uiState.errorMessage,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            uiState.items.isEmpty() -> DallimEmptyState(
                title = "아직 저장한 코스가 없어요",
                description = "마음에 드는 코스를 저장하면 여기에서 모아볼 수 있어요.",
                actionText = "코스 탐색하기",
                onActionClick = onExploreClick,
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.ScreenHorizontal,
                    end = Spacing.ScreenHorizontal,
                    top = Spacing.sm,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.ListItemGap),
            ) {
                items(uiState.items, key = { it.routeId }) { route ->
                    SavedRouteRow(
                        route = route,
                        isRemoving = route.routeId in uiState.removingRouteIds,
                        onClick = { onRouteClick(route.routeId) },
                        onUnsaveClick = { onUnsaveClick(route.routeId) },
                    )
                }
                if (uiState.isLoadingMore) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DallimColors.Primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedRouteRow(
    route: SavedRouteItem,
    isRemoving: Boolean,
    onClick: () -> Unit,
    onUnsaveClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DallimColors.Surface)
            .clickable { onClick() }
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DallimColors.PrimaryLight),
            contentAlignment = Alignment.Center,
        ) {
            // 콘텐츠 데이터로서의 이모지 (docs/04-ui-guide.md §7). GeoJSON 썸네일 데이터가 없는
            // 응답이라 RouteThumbnailView 대신 이모지 배지로 코스를 식별한다.
            Text(text = route.emoji, style = DallimTypography.Title2)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            Text(text = route.name, style = DallimTypography.Body, color = DallimColors.TextPrimary)
            Text(
                text = "${route.distanceKm}km · ${if (route.hasRun) "완주함" else "미완주"}",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        IconButton(onClick = onUnsaveClick, enabled = !isRemoving) {
            Icon(imageVector = Icons.Filled.BookmarkRemove, contentDescription = "저장 취소", tint = DallimColors.TextSecondary)
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 4

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun SavedRoutesScreenPreview() {
    DallimTheme {
        SavedRoutesScreen(
            uiState = SavedRoutesUiState(
                items = listOf(
                    SavedRouteItem(routeId = "rt_001", name = "고래", emoji = "🐳", distanceKm = 5.1, hasRun = true),
                    SavedRouteItem(routeId = "rt_002", name = "물고기", emoji = "🐟", distanceKm = 3.4, hasRun = false),
                ),
                isLoadingInitial = false,
            ),
            onBackClick = {},
            onRouteClick = {},
            onExploreClick = {},
            onUnsaveClick = {},
            onRetryClick = {},
            onLoadNextPage = {},
        )
    }
}
