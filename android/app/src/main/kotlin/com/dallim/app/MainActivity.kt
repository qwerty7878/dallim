package com.dallim.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.rememberNavController
import com.dallim.app.navigation.DallimNavHost
import com.dallim.ui.components.LocalMapThumbnailsEnabled
import com.dallim.ui.theme.DallimTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱 전체 다크 고정(2026-10-05, docs/03-design-system.md §4)이라 상태바/네비게이션바 아이콘도
        // 항상 밝게 고정한다 — 시스템이 라이트 모드면 기본값이 "어두운 아이콘"이라 검은 지면에서 사라진다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            DallimTheme {
                // 코스 썸네일을 실제 지도 스냅샷으로 그릴지 — NCP 키가 있는 빌드에서만(MapSnapshotThumbnail 참고).
                CompositionLocalProvider(LocalMapThumbnailsEnabled provides BuildConfig.NAVER_MAP_CLIENT_ID_CONFIGURED) {
                    val navController = rememberNavController()
                    DallimNavHost(
                        navController = navController,
                        debugRoute = if (BuildConfig.DEBUG) intent.getStringExtra("debug_route") else null,
                    )
                }
            }
        }
    }
}
