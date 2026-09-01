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

    /**
     * 서버 응답(`distanceKm: Double`, 이미 km 단위)이나 `LocalPrecheckCalculator`처럼 km으로 계산된
     * 값을 그대로 2자리로 반올림한다. [distanceKm]과 헷갈리지 않도록 이름을 분리했다 — 저건 m 단위
     * 입력을 받는다. GPS로 계산된 실측 거리(예: `run.distanceKm`)는 소수점이 길게 나오므로 반드시
     * 이 함수를 거쳐야 한다("1.9973258027170027km"처럼 원값을 그대로 문자열 보간하지 말 것).
     */
    fun km(value: Double): String = "%.2f".format(value)

    /** `secPerKm`이 null이거나 0 이하면 "-'--" 로 표시한다(아직 페이스를 계산할 수 없을 때). */
    fun pace(secPerKm: Int?): String {
        if (secPerKm == null || secPerKm <= 0) return "-'--\""
        val m = secPerKm / 60
        val s = secPerKm % 60
        return "%d'%02d\"".format(m, s)
    }
}
