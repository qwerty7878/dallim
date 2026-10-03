package com.dallim.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
        setContent {
            DallimTheme {
                // 코스 썸네일을 실제 지도 스냅샷으로 그릴지 — NCP 키가 있는 빌드에서만(MapSnapshotThumbnail 참고).
                CompositionLocalProvider(LocalMapThumbnailsEnabled provides BuildConfig.NAVER_MAP_CLIENT_ID_CONFIGURED) {
                    val navController = rememberNavController()
                    DallimNavHost(navController = navController)
                }
            }
        }
    }
}
