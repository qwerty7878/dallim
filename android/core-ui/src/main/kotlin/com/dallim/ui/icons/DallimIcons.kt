package com.dallim.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 앱 아이콘 세트 — Lucide(ISC 라이선스, `core-ui/icons-src` 폴더의 SVG)의 SVG를 `ImageVector`로 옮긴 것이다
 * (2026-10-04). Material 기본 아이콘은 "기본값 앱" 인상이 강해 일반적으로 널리 쓰이는 라인 아이콘 세트로
 * 교체했다. 생성 스크립트 기준으로 SVG 원본과 1:1이며(24x24, stroke 2, round cap/join), `Icon(tint=...)`로
 * 색을 입힌다. *Filled 변형은 같은 경로를 채워서 그린 것으로 저장/선택 상태에 쓴다.
 *
 * 이 파일은 `icons-src` 폴더의 SVG에서 생성한 코드다 — 아이콘을 추가하려면 SVG를 넣고 다시 생성한다.
 */
@Suppress("unused")
object DallimIcons {
    private fun lucide(name: String, filled: Boolean, vararg pathData: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                pathData.forEach { d ->
                    addPath(
                        pathData = PathParser().parsePathString(d).toNodes(),
                        fill = if (filled) SolidColor(Color.Black) else null,
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()

    val Accessibility: ImageVector by lazy {
        lucide("Accessibility", false,
            "M15,4a1,1 0 1,0 2,0a1,1 0 1,0 -2,0z",
            "m18 19 1-7-6 1",
            "m5 8 3-3 5.5 3-2.36 3.5",
            "M4.24 14.5a5 5 0 0 0 6.88 6",
            "M13.76 17.5a5 5 0 0 0-6.88-6",
        )
    }
    /** 채움 버전(Accessibility) — 저장됨/선택됨 상태 표시용. */
    val AccessibilityFilled: ImageVector by lazy {
        lucide("AccessibilityFilled", true,
            "M15,4a1,1 0 1,0 2,0a1,1 0 1,0 -2,0z",
            "m18 19 1-7-6 1",
            "m5 8 3-3 5.5 3-2.36 3.5",
            "M4.24 14.5a5 5 0 0 0 6.88 6",
            "M13.76 17.5a5 5 0 0 0-6.88-6",
        )
    }

    val ArrowLeft: ImageVector by lazy {
        lucide("ArrowLeft", false,
            "m12 19-7-7 7-7",
            "M19 12H5",
        )
    }
    /** 채움 버전(ArrowLeft) — 저장됨/선택됨 상태 표시용. */
    val ArrowLeftFilled: ImageVector by lazy {
        lucide("ArrowLeftFilled", true,
            "m12 19-7-7 7-7",
            "M19 12H5",
        )
    }

    val Ban: ImageVector by lazy {
        lucide("Ban", false,
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0z",
            "M4.929 4.929 19.07 19.071",
        )
    }
    /** 채움 버전(Ban) — 저장됨/선택됨 상태 표시용. */
    val BanFilled: ImageVector by lazy {
        lucide("BanFilled", true,
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0z",
            "M4.929 4.929 19.07 19.071",
        )
    }

    val BatteryWarning: ImageVector by lazy {
        lucide("BatteryWarning", false,
            "M10 17h.01",
            "M10 7v6",
            "M14 6h2a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2",
            "M22 14v-4",
            "M6 18H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2",
        )
    }
    /** 채움 버전(BatteryWarning) — 저장됨/선택됨 상태 표시용. */
    val BatteryWarningFilled: ImageVector by lazy {
        lucide("BatteryWarningFilled", true,
            "M10 17h.01",
            "M10 7v6",
            "M14 6h2a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2",
            "M22 14v-4",
            "M6 18H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2",
        )
    }

    val Bell: ImageVector by lazy {
        lucide("Bell", false,
            "M10.268 21a2 2 0 0 0 3.464 0",
            "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
        )
    }
    /** 채움 버전(Bell) — 저장됨/선택됨 상태 표시용. */
    val BellFilled: ImageVector by lazy {
        lucide("BellFilled", true,
            "M10.268 21a2 2 0 0 0 3.464 0",
            "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
        )
    }

    val BookOpen: ImageVector by lazy {
        lucide("BookOpen", false,
            "M12 5v16",
            "M20.001 19A2 2 0 0022 17V5a2 2 0 00-1.999-2L16 3.002A5 5 0 0012 5a5 5 0 00-4-2H4a2 2 0 00-2 2v12a2 2 0 001.999 2H8a5 5 0 014 2 5 5 0 014-2z",
        )
    }
    /** 채움 버전(BookOpen) — 저장됨/선택됨 상태 표시용. */
    val BookOpenFilled: ImageVector by lazy {
        lucide("BookOpenFilled", true,
            "M12 5v16",
            "M20.001 19A2 2 0 0022 17V5a2 2 0 00-1.999-2L16 3.002A5 5 0 0012 5a5 5 0 00-4-2H4a2 2 0 00-2 2v12a2 2 0 001.999 2H8a5 5 0 014 2 5 5 0 014-2z",
        )
    }

    val BookmarkX: ImageVector by lazy {
        lucide("BookmarkX", false,
            "m14.5 7.5-5 5",
            "M17 3a2 2 0 0 1 2 2v15a1 1 0 0 1-1.496.868l-4.512-2.578a2 2 0 0 0-1.984 0l-4.512 2.578A1 1 0 0 1 5 20V5a2 2 0 0 1 2-2z",
            "m9.5 7.5 5 5",
        )
    }
    /** 채움 버전(BookmarkX) — 저장됨/선택됨 상태 표시용. */
    val BookmarkXFilled: ImageVector by lazy {
        lucide("BookmarkXFilled", true,
            "m14.5 7.5-5 5",
            "M17 3a2 2 0 0 1 2 2v15a1 1 0 0 1-1.496.868l-4.512-2.578a2 2 0 0 0-1.984 0l-4.512 2.578A1 1 0 0 1 5 20V5a2 2 0 0 1 2-2z",
            "m9.5 7.5 5 5",
        )
    }

    val Bookmark: ImageVector by lazy {
        lucide("Bookmark", false,
            "M17 3a2 2 0 0 1 2 2v15a1 1 0 0 1-1.496.868l-4.512-2.578a2 2 0 0 0-1.984 0l-4.512 2.578A1 1 0 0 1 5 20V5a2 2 0 0 1 2-2z",
        )
    }
    /** 채움 버전(Bookmark) — 저장됨/선택됨 상태 표시용. */
    val BookmarkFilled: ImageVector by lazy {
        lucide("BookmarkFilled", true,
            "M17 3a2 2 0 0 1 2 2v15a1 1 0 0 1-1.496.868l-4.512-2.578a2 2 0 0 0-1.984 0l-4.512 2.578A1 1 0 0 1 5 20V5a2 2 0 0 1 2-2z",
        )
    }

    val Calendar: ImageVector by lazy {
        lucide("Calendar", false,
            "M8 2v3",
            "M16 2v3",
            "M5,3h14a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-14a2,2 0 0 1 2,-2z",
            "M3 9h18",
        )
    }
    /** 채움 버전(Calendar) — 저장됨/선택됨 상태 표시용. */
    val CalendarFilled: ImageVector by lazy {
        lucide("CalendarFilled", true,
            "M8 2v3",
            "M16 2v3",
            "M5,3h14a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-14a2,2 0 0 1 2,-2z",
            "M3 9h18",
        )
    }

    val Check: ImageVector by lazy {
        lucide("Check", false,
            "M20 6 9 17l-5-5",
        )
    }
    /** 채움 버전(Check) — 저장됨/선택됨 상태 표시용. */
    val CheckFilled: ImageVector by lazy {
        lucide("CheckFilled", true,
            "M20 6 9 17l-5-5",
        )
    }

    val ChevronDown: ImageVector by lazy {
        lucide("ChevronDown", false,
            "m6 9 6 6 6-6",
        )
    }
    /** 채움 버전(ChevronDown) — 저장됨/선택됨 상태 표시용. */
    val ChevronDownFilled: ImageVector by lazy {
        lucide("ChevronDownFilled", true,
            "m6 9 6 6 6-6",
        )
    }

    val ChevronLeft: ImageVector by lazy {
        lucide("ChevronLeft", false,
            "m15 18-6-6 6-6",
        )
    }
    /** 채움 버전(ChevronLeft) — 저장됨/선택됨 상태 표시용. */
    val ChevronLeftFilled: ImageVector by lazy {
        lucide("ChevronLeftFilled", true,
            "m15 18-6-6 6-6",
        )
    }

    val ChevronRight: ImageVector by lazy {
        lucide("ChevronRight", false,
            "m9 18 6-6-6-6",
        )
    }
    /** 채움 버전(ChevronRight) — 저장됨/선택됨 상태 표시용. */
    val ChevronRightFilled: ImageVector by lazy {
        lucide("ChevronRightFilled", true,
            "m9 18 6-6-6-6",
        )
    }

    val CircleAlert: ImageVector by lazy {
        lucide("CircleAlert", false,
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0z",
            "M12,8L12,12",
            "M12,16L12.01,16",
        )
    }
    /** 채움 버전(CircleAlert) — 저장됨/선택됨 상태 표시용. */
    val CircleAlertFilled: ImageVector by lazy {
        lucide("CircleAlertFilled", true,
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0z",
            "M12,8L12,12",
            "M12,16L12.01,16",
        )
    }

    val CircleCheck: ImageVector by lazy {
        lucide("CircleCheck", false,
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0z",
            "m16 9-5.5 5.5L8 12",
        )
    }
    /** 채움 버전(CircleCheck) — 저장됨/선택됨 상태 표시용. */
    val CircleCheckFilled: ImageVector by lazy {
        lucide("CircleCheckFilled", true,
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0z",
            "m16 9-5.5 5.5L8 12",
        )
    }

    val Circle: ImageVector by lazy {
        lucide("Circle", false,
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0z",
        )
    }
    /** 채움 버전(Circle) — 저장됨/선택됨 상태 표시용. */
    val CircleFilled: ImageVector by lazy {
        lucide("CircleFilled", true,
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0z",
        )
    }

    val Clock: ImageVector by lazy {
        lucide("Clock", false,
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0z",
            "M12 6v6l4 2",
        )
    }
    /** 채움 버전(Clock) — 저장됨/선택됨 상태 표시용. */
    val ClockFilled: ImageVector by lazy {
        lucide("ClockFilled", true,
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0z",
            "M12 6v6l4 2",
        )
    }

    val Ellipsis: ImageVector by lazy {
        lucide("Ellipsis", false,
            "M11,12a1,1 0 1,0 2,0a1,1 0 1,0 -2,0z",
            "M18,12a1,1 0 1,0 2,0a1,1 0 1,0 -2,0z",
            "M4,12a1,1 0 1,0 2,0a1,1 0 1,0 -2,0z",
        )
    }
    /** 채움 버전(Ellipsis) — 저장됨/선택됨 상태 표시용. */
    val EllipsisFilled: ImageVector by lazy {
        lucide("EllipsisFilled", true,
            "M11,12a1,1 0 1,0 2,0a1,1 0 1,0 -2,0z",
            "M18,12a1,1 0 1,0 2,0a1,1 0 1,0 -2,0z",
            "M4,12a1,1 0 1,0 2,0a1,1 0 1,0 -2,0z",
        )
    }

    val Flag: ImageVector by lazy {
        lucide("Flag", false,
            "M4 22V4a1 1 0 0 1 .4-.8A6 6 0 0 1 8 2c3 0 5 2 7.333 2q2 0 3.067-.8A1 1 0 0 1 20 4v10a1 1 0 0 1-.4.8A6 6 0 0 1 16 16c-3 0-5-2-8-2a6 6 0 0 0-4 1.528",
        )
    }
    /** 채움 버전(Flag) — 저장됨/선택됨 상태 표시용. */
    val FlagFilled: ImageVector by lazy {
        lucide("FlagFilled", true,
            "M4 22V4a1 1 0 0 1 .4-.8A6 6 0 0 1 8 2c3 0 5 2 7.333 2q2 0 3.067-.8A1 1 0 0 1 20 4v10a1 1 0 0 1-.4.8A6 6 0 0 1 16 16c-3 0-5-2-8-2a6 6 0 0 0-4 1.528",
        )
    }

    val Footprints: ImageVector by lazy {
        lucide("Footprints", false,
            "M4 16v-2.38C4 11.5 2.97 10.5 3 8c.03-2.72 1.49-6 4.5-6C9.37 2 10 3.8 10 5.5c0 3.11-2 5.66-2 8.68V16a2 2 0 1 1-4 0Z",
            "M20 20v-2.38c0-2.12 1.03-3.12 1-5.62-.03-2.72-1.49-6-4.5-6C14.63 6 14 7.8 14 9.5c0 3.11 2 5.66 2 8.68V20a2 2 0 1 0 4 0Z",
            "M16 17h4",
            "M4 13h4",
        )
    }
    /** 채움 버전(Footprints) — 저장됨/선택됨 상태 표시용. */
    val FootprintsFilled: ImageVector by lazy {
        lucide("FootprintsFilled", true,
            "M4 16v-2.38C4 11.5 2.97 10.5 3 8c.03-2.72 1.49-6 4.5-6C9.37 2 10 3.8 10 5.5c0 3.11-2 5.66-2 8.68V16a2 2 0 1 1-4 0Z",
            "M20 20v-2.38c0-2.12 1.03-3.12 1-5.62-.03-2.72-1.49-6-4.5-6C14.63 6 14 7.8 14 9.5c0 3.11 2 5.66 2 8.68V20a2 2 0 1 0 4 0Z",
            "M16 17h4",
            "M4 13h4",
        )
    }

    val House: ImageVector by lazy {
        lucide("House", false,
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
        )
    }
    /** 채움 버전(House) — 저장됨/선택됨 상태 표시용. */
    val HouseFilled: ImageVector by lazy {
        lucide("HouseFilled", true,
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
        )
    }

    val Inbox: ImageVector by lazy {
        lucide("Inbox", false,
            "M22,12L16,12L14,15L10,15L8,12L2,12",
            "M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
        )
    }
    /** 채움 버전(Inbox) — 저장됨/선택됨 상태 표시용. */
    val InboxFilled: ImageVector by lazy {
        lucide("InboxFilled", true,
            "M22,12L16,12L14,15L10,15L8,12L2,12",
            "M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
        )
    }

    val LocateFixed: ImageVector by lazy {
        lucide("LocateFixed", false,
            "M2,12L5,12",
            "M19,12L22,12",
            "M12,2L12,5",
            "M12,19L12,22",
            "M5,12a7,7 0 1,0 14,0a7,7 0 1,0 -14,0z",
            "M9,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0z",
        )
    }
    /** 채움 버전(LocateFixed) — 저장됨/선택됨 상태 표시용. */
    val LocateFixedFilled: ImageVector by lazy {
        lucide("LocateFixedFilled", true,
            "M2,12L5,12",
            "M19,12L22,12",
            "M12,2L12,5",
            "M12,19L12,22",
            "M5,12a7,7 0 1,0 14,0a7,7 0 1,0 -14,0z",
            "M9,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0z",
        )
    }

    val Lock: ImageVector by lazy {
        lucide("Lock", false,
            "M5,11h14a2,2 0 0 1 2,2v7a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-7a2,2 0 0 1 2,-2z",
            "M7 11V7a5 5 0 0 1 10 0v4",
        )
    }
    /** 채움 버전(Lock) — 저장됨/선택됨 상태 표시용. */
    val LockFilled: ImageVector by lazy {
        lucide("LockFilled", true,
            "M5,11h14a2,2 0 0 1 2,2v7a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-7a2,2 0 0 1 2,-2z",
            "M7 11V7a5 5 0 0 1 10 0v4",
        )
    }

    val MapPin: ImageVector by lazy {
        lucide("MapPin", false,
            "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
            "M9,10a3,3 0 1,0 6,0a3,3 0 1,0 -6,0z",
        )
    }
    /** 채움 버전(MapPin) — 저장됨/선택됨 상태 표시용. */
    val MapPinFilled: ImageVector by lazy {
        lucide("MapPinFilled", true,
            "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
            "M9,10a3,3 0 1,0 6,0a3,3 0 1,0 -6,0z",
        )
    }

    val Megaphone: ImageVector by lazy {
        lucide("Megaphone", false,
            "M11 6a13 13 0 0 0 8.4-2.8A1 1 0 0 1 21 4v12a1 1 0 0 1-1.6.8A13 13 0 0 0 11 14H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
            "M6 14a12 12 0 0 0 2.4 7.2 2 2 0 0 0 3.2-2.4A8 8 0 0 1 10 14",
            "M8 6v8",
        )
    }
    /** 채움 버전(Megaphone) — 저장됨/선택됨 상태 표시용. */
    val MegaphoneFilled: ImageVector by lazy {
        lucide("MegaphoneFilled", true,
            "M11 6a13 13 0 0 0 8.4-2.8A1 1 0 0 1 21 4v12a1 1 0 0 1-1.6.8A13 13 0 0 0 11 14H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
            "M6 14a12 12 0 0 0 2.4 7.2 2 2 0 0 0 3.2-2.4A8 8 0 0 1 10 14",
            "M8 6v8",
        )
    }

    val MessageCircle: ImageVector by lazy {
        lucide("MessageCircle", false,
            "M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719",
        )
    }
    /** 채움 버전(MessageCircle) — 저장됨/선택됨 상태 표시용. */
    val MessageCircleFilled: ImageVector by lazy {
        lucide("MessageCircleFilled", true,
            "M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719",
        )
    }

    val Navigation: ImageVector by lazy {
        lucide("Navigation", false,
            "M3,11L22,2L13,21L11,13L3,11z",
        )
    }
    /** 채움 버전(Navigation) — 저장됨/선택됨 상태 표시용. */
    val NavigationFilled: ImageVector by lazy {
        lucide("NavigationFilled", true,
            "M3,11L22,2L13,21L11,13L3,11z",
        )
    }

    val Pencil: ImageVector by lazy {
        lucide("Pencil", false,
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
            "m15 5 4 4",
        )
    }
    /** 채움 버전(Pencil) — 저장됨/선택됨 상태 표시용. */
    val PencilFilled: ImageVector by lazy {
        lucide("PencilFilled", true,
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
            "m15 5 4 4",
        )
    }

    val PersonStanding: ImageVector by lazy {
        lucide("PersonStanding", false,
            "M11,5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0z",
            "m9 20 3-6 3 6",
            "m6 8 6 2 6-2",
            "M12 10v4",
        )
    }
    /** 채움 버전(PersonStanding) — 저장됨/선택됨 상태 표시용. */
    val PersonStandingFilled: ImageVector by lazy {
        lucide("PersonStandingFilled", true,
            "M11,5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0z",
            "m9 20 3-6 3 6",
            "m6 8 6 2 6-2",
            "M12 10v4",
        )
    }

    val Play: ImageVector by lazy {
        lucide("Play", false,
            "M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z",
        )
    }
    /** 채움 버전(Play) — 저장됨/선택됨 상태 표시용. */
    val PlayFilled: ImageVector by lazy {
        lucide("PlayFilled", true,
            "M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z",
        )
    }

    val Plus: ImageVector by lazy {
        lucide("Plus", false,
            "M5 12h14",
            "M12 5v14",
        )
    }
    /** 채움 버전(Plus) — 저장됨/선택됨 상태 표시용. */
    val PlusFilled: ImageVector by lazy {
        lucide("PlusFilled", true,
            "M5 12h14",
            "M12 5v14",
        )
    }

    val Search: ImageVector by lazy {
        lucide("Search", false,
            "m21 21-4.34-4.34",
            "M3,11a8,8 0 1,0 16,0a8,8 0 1,0 -16,0z",
        )
    }
    /** 채움 버전(Search) — 저장됨/선택됨 상태 표시용. */
    val SearchFilled: ImageVector by lazy {
        lucide("SearchFilled", true,
            "m21 21-4.34-4.34",
            "M3,11a8,8 0 1,0 16,0a8,8 0 1,0 -16,0z",
        )
    }

    val Send: ImageVector by lazy {
        lucide("Send", false,
            "M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z",
            "m21.854 2.147-10.94 10.939",
        )
    }
    /** 채움 버전(Send) — 저장됨/선택됨 상태 표시용. */
    val SendFilled: ImageVector by lazy {
        lucide("SendFilled", true,
            "M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z",
            "m21.854 2.147-10.94 10.939",
        )
    }

    val Share2: ImageVector by lazy {
        lucide("Share2", false,
            "M15,5a3,3 0 1,0 6,0a3,3 0 1,0 -6,0z",
            "M3,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0z",
            "M15,19a3,3 0 1,0 6,0a3,3 0 1,0 -6,0z",
            "M8.59,13.51L15.42,17.49",
            "M15.41,6.51L8.59,10.49",
        )
    }
    /** 채움 버전(Share2) — 저장됨/선택됨 상태 표시용. */
    val Share2Filled: ImageVector by lazy {
        lucide("Share2Filled", true,
            "M15,5a3,3 0 1,0 6,0a3,3 0 1,0 -6,0z",
            "M3,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0z",
            "M15,19a3,3 0 1,0 6,0a3,3 0 1,0 -6,0z",
            "M8.59,13.51L15.42,17.49",
            "M15.41,6.51L8.59,10.49",
        )
    }

    val Smile: ImageVector by lazy {
        lucide("Smile", false,
            "M15 10V9",
            "M16.472 15a6 6 0 01-8.943 0",
            "M9 10V9",
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0z",
        )
    }
    /** 채움 버전(Smile) — 저장됨/선택됨 상태 표시용. */
    val SmileFilled: ImageVector by lazy {
        lucide("SmileFilled", true,
            "M15 10V9",
            "M16.472 15a6 6 0 01-8.943 0",
            "M9 10V9",
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0z",
        )
    }

    val Sparkles: ImageVector by lazy {
        lucide("Sparkles", false,
            "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
            "M20 2v4",
            "M22 4h-4",
            "M2,20a2,2 0 1,0 4,0a2,2 0 1,0 -4,0z",
        )
    }
    /** 채움 버전(Sparkles) — 저장됨/선택됨 상태 표시용. */
    val SparklesFilled: ImageVector by lazy {
        lucide("SparklesFilled", true,
            "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
            "M20 2v4",
            "M22 4h-4",
            "M2,20a2,2 0 1,0 4,0a2,2 0 1,0 -4,0z",
        )
    }

    val Square: ImageVector by lazy {
        lucide("Square", false,
            "M5,3h14a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-14a2,2 0 0 1 2,-2z",
        )
    }
    /** 채움 버전(Square) — 저장됨/선택됨 상태 표시용. */
    val SquareFilled: ImageVector by lazy {
        lucide("SquareFilled", true,
            "M5,3h14a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-14a2,2 0 0 1 2,-2z",
        )
    }

    val Trash2: ImageVector by lazy {
        lucide("Trash2", false,
            "M10 11v6",
            "M14 11v6",
            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
            "M3 6h18",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
        )
    }
    /** 채움 버전(Trash2) — 저장됨/선택됨 상태 표시용. */
    val Trash2Filled: ImageVector by lazy {
        lucide("Trash2Filled", true,
            "M10 11v6",
            "M14 11v6",
            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
            "M3 6h18",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
        )
    }

    val Trophy: ImageVector by lazy {
        lucide("Trophy", false,
            "M10 14.66V17a1 1 0 0 1-1 1 2 2 0 0 0-2 2v2",
            "M14 14.66V17a1 1 0 0 0 1 1 2 2 0 0 1 2 2v2",
            "M17.916 10H19.5A2.5 2.5 0 0 0 22 7.5V5a1 1 0 0 0-1-1h-3",
            "M4 22h16",
            "M6 9a6 6 0 0 0 12 0V3a1 1 0 0 0-1-1H7a1 1 0 0 0-1 1z",
            "M6.084 10H4.5A2.5 2.5 0 0 1 2 7.5V5a1 1 0 0 1 1-1h3",
        )
    }
    /** 채움 버전(Trophy) — 저장됨/선택됨 상태 표시용. */
    val TrophyFilled: ImageVector by lazy {
        lucide("TrophyFilled", true,
            "M10 14.66V17a1 1 0 0 1-1 1 2 2 0 0 0-2 2v2",
            "M14 14.66V17a1 1 0 0 0 1 1 2 2 0 0 1 2 2v2",
            "M17.916 10H19.5A2.5 2.5 0 0 0 22 7.5V5a1 1 0 0 0-1-1h-3",
            "M4 22h16",
            "M6 9a6 6 0 0 0 12 0V3a1 1 0 0 0-1-1H7a1 1 0 0 0-1 1z",
            "M6.084 10H4.5A2.5 2.5 0 0 1 2 7.5V5a1 1 0 0 1 1-1h3",
        )
    }

    val UserX: ImageVector by lazy {
        lucide("UserX", false,
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            "M5,7a4,4 0 1,0 8,0a4,4 0 1,0 -8,0z",
            "M17,8L22,13",
            "M22,8L17,13",
        )
    }
    /** 채움 버전(UserX) — 저장됨/선택됨 상태 표시용. */
    val UserXFilled: ImageVector by lazy {
        lucide("UserXFilled", true,
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            "M5,7a4,4 0 1,0 8,0a4,4 0 1,0 -8,0z",
            "M17,8L22,13",
            "M22,8L17,13",
        )
    }

    val User: ImageVector by lazy {
        lucide("User", false,
            "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2",
            "M8,7a4,4 0 1,0 8,0a4,4 0 1,0 -8,0z",
        )
    }
    /** 채움 버전(User) — 저장됨/선택됨 상태 표시용. */
    val UserFilled: ImageVector by lazy {
        lucide("UserFilled", true,
            "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2",
            "M8,7a4,4 0 1,0 8,0a4,4 0 1,0 -8,0z",
        )
    }

    val Users: ImageVector by lazy {
        lucide("Users", false,
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            "M16 3.128a4 4 0 0 1 0 7.744",
            "M22 21v-2a4 4 0 0 0-3-3.87",
            "M5,7a4,4 0 1,0 8,0a4,4 0 1,0 -8,0z",
        )
    }
    /** 채움 버전(Users) — 저장됨/선택됨 상태 표시용. */
    val UsersFilled: ImageVector by lazy {
        lucide("UsersFilled", true,
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            "M16 3.128a4 4 0 0 1 0 7.744",
            "M22 21v-2a4 4 0 0 0-3-3.87",
            "M5,7a4,4 0 1,0 8,0a4,4 0 1,0 -8,0z",
        )
    }

    val X: ImageVector by lazy {
        lucide("X", false,
            "M18 6 6 18",
            "m6 6 12 12",
        )
    }
    /** 채움 버전(X) — 저장됨/선택됨 상태 표시용. */
    val XFilled: ImageVector by lazy {
        lucide("XFilled", true,
            "M18 6 6 18",
            "m6 6 12 12",
        )
    }
}
