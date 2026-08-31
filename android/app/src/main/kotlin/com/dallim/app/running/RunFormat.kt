package com.dallim.app.running

/** S-21/S-25 공통 숫자 포맷 — 거리/시간/페이스 표시에 쓰는 작은 순수 함수 모음. */
object RunFormat {
    fun duration(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    fun distanceKm(meters: Double): String = "%.2f".format(meters / 1000.0)

    /** `secPerKm`이 null이거나 0 이하면 "-'--" 로 표시한다(아직 페이스를 계산할 수 없을 때). */
    fun pace(secPerKm: Int?): String {
        if (secPerKm == null || secPerKm <= 0) return "-'--\""
        val m = secPerKm / 60
        val s = secPerKm % 60
        return "%d'%02d\"".format(m, s)
    }
}
